import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { networkInterfaces } from 'node:os';
import { randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { ChatGPTAuth, lockStore } from './auth.mjs';
import { providers, foreground, playback, contextFor, promptFor, readResponseStream } from './context.mjs';

const exec = promisify(execFile);
const device = process.env.ATTUNETV_DEVICE || '192.168.86.42:5555';
if (!/^[\w.:-]+$/.test(device)) throw new Error('Invalid ADB device identifier.');
const adbPath = process.env.ADB || '/opt/homebrew/bin/adb';
const port = Number(process.env.ATTUNETV_PORT || 8765);
const authPort = Number(process.env.ATTUNETV_AUTH_PORT || 8766);
const localOrigin = `http://127.0.0.1:${authPort}`;
const addresses = Object.values(networkInterfaces()).flat().filter(n => n.family === 'IPv4' && !n.internal).map(n => n.address);
const allowedHosts = new Set([`127.0.0.1:${authPort}`, ...addresses.map(ip => `${ip}:${port}`)]);
await lockStore();
const auth = await ChatGPTAuth.create();
const capabilities = JSON.parse(await readFile(new URL('./support.json', import.meta.url), 'utf8'));
const verificationPackage = process.env.ATTUNETV_VERIFY_PACKAGE;
const enabled = [...capabilities.enabledPackages, ...(providers[verificationPackage] ? [verificationPackage] : [])];
const cookie = randomBytes(32).toString('hex');
const phoneSessions = new Set();
const pairAttempts = new Map();
let pairPin = String(randomInt(100000, 1000000)), pairExpires = Date.now() + 600000;
let snapshot = { connected: false, pkg: null, title: null, positionMs: null, state: 'unknown' };
let manual = null, history = [], answer = '', question = '', error = '', busy = false, requestController;
let overlay = false, overlayUntil = 0, generation = 0, lastPoll = 0, lastBroadcast = 0, lastSent = '';
let models = [], selectedModel = '', modelAccount = null, stopping = false;
let overlayQueue = Promise.resolve();

const isLocal = req => req.socket.localPort === authPort && ['127.0.0.1', '::ffff:127.0.0.1'].includes(req.socket.remoteAddress);
const tokenMatches = (a, b) => typeof a === 'string' && a.length === b.length && timingSafeEqual(Buffer.from(a), Buffer.from(b));
const authenticated = req => isLocal(req) ? req.headers.cookie?.split('; ').includes(`attune=${cookie}`) : phoneSessions.has(req.headers['x-attune-session']);
const context = () => contextFor({ ...snapshot, connected: snapshot.connected && Date.now() - lastPoll < 10000 }, manual, enabled);
async function adb(...args) {
  const { stdout } = await exec(adbPath, ['-s', device, ...args], { timeout: 10000, maxBuffer: 2 * 1024 * 1024 });
  return stdout;
}
function stopAnswer() { generation++; requestController?.abort(); requestController = null; busy = false; }
function resetConversation() { stopAnswer(); history = []; answer = ''; question = ''; error = ''; }

function sendOverlay(force = false) {
  const ctx = context();
  if (Date.now() >= overlayUntil || !ctx.supported) overlay = false;
  const visible = overlay;
  const payload = visible ? { visible: true, pkg: ctx.pkg, provider: ctx.provider,
    title: ctx.title || 'Add the movie or show on your phone', source: ctx.source,
    question, answer: (error || answer || 'Ask a question from your phone. Your video keeps playing.').slice(0, 5000), busy } : { visible: false };
  const serialized = JSON.stringify(payload);
  if (!force && serialized === lastSent && (!visible || Date.now() - lastBroadcast < 3500)) return overlayQueue;
  lastSent = serialized; lastBroadcast = Date.now();
  const seq = generation;
  overlayQueue = overlayQueue.catch(() => {}).then(async () => {
    // Do not resurrect a panel dismissed while a command was queued.
    if (visible && (seq !== generation || !overlay || context().pkg !== payload.pkg)) return;
    const encoded = Buffer.from(serialized).toString('base64url');
    const result = await adb('shell', 'am', 'broadcast', '-a', 'com.attunetv.launcher.COMPANION', '-n',
      'com.attunetv.launcher/.CompanionReceiver', '--es', 'payload', encoded);
    if (visible && !/result=-1\b/.test(result)) throw new Error('TV overlay is unavailable. Check that AttuneTV Home redirection is enabled.');
  }).catch(() => { if (visible) { overlay = false; error = 'Could not show the TV panel. Check the TV connection and AttuneTV service.'; } });
  return overlayQueue;
}

async function poll() {
  if (stopping) return;
  try {
    const [activities, media, uptime] = await Promise.all([
      adb('shell', 'dumpsys', 'activity', 'activities'), adb('shell', 'dumpsys', 'media_session'), adb('shell', 'cat', '/proc/uptime'),
    ]);
    const pkg = foreground(activities);
    const data = playback(media, pkg, Number(uptime.split(' ')[0]));
    if (snapshot.pkg !== pkg || (snapshot.title && data.title && snapshot.title !== data.title)) {
      overlay = false; manual = null; resetConversation();
    }
    snapshot = { connected: true, pkg, ...data }; lastPoll = Date.now();
  } catch {
    if (snapshot.connected) resetConversation();
    snapshot.connected = false; overlay = false;
  }
  await sendOverlay();
  if (!stopping) setTimeout(poll, 2000);
}

async function ensureModels() {
  if (modelAccount !== auth.store.active || !models.length) {
    models = await auth.models(); modelAccount = auth.store.active;
    selectedModel = models.some(m => m.id === selectedModel) ? selectedModel : models[0]?.id || '';
  }
  if (!selectedModel) throw new Error('No eligible ChatGPT models are available for this account.');
}
async function ask(text) {
  if (busy) throw new Error('Wait for the current answer or press Stop.');
  const ctx = context();
  if (!ctx.supported) throw new Error('Chat is hidden in this app until it has been verified.');
  if (!ctx.title) throw new Error('Enter the movie or show title first. This app is not sharing it.');
  if (!text.trim() || text.length > 2000) throw new Error('Enter a question of 1–2,000 characters.');
  busy = true; answer = ''; error = ''; question = text.trim();
  overlay = true; overlayUntil = Date.now() + 90000;
  const gen = generation, pkg = ctx.pkg;
  requestController = new AbortController();
  const signal = AbortSignal.any([requestController.signal, AbortSignal.timeout(120000)]);
  try {
    await ensureModels();
    const token = await auth.accessToken();
    if (gen !== generation) return;
    const response = await fetch('https://api.openai.com/v1/responses', { method: 'POST', signal,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: selectedModel, store: false, stream: true,
        instructions: promptFor(ctx), input: [...history.slice(-12), { role: 'user', content: question }] }) });
    if (!response.ok) throw new Error(response.status === 429 ? 'ChatGPT usage limit reached. Check ChatGPT Settings → Usage.' : 'ChatGPT could not answer. Check account access and try again.');
    const output = await readResponseStream(response.body, partial => {
      if (gen !== generation || context().pkg !== pkg) return;
      answer = partial; // Poll delivers at most one TV update every two seconds.
    });
    if (gen !== generation || context().pkg !== pkg) return;
    answer = output;
    history.push({ role: 'user', content: question }, { role: 'assistant', content: answer });
    history = history.slice(-12);
  } catch (e) {
    if (gen === generation) error = signal.aborted ? 'Answer stopped before completion.' : e.message;
  } finally {
    if (gen === generation) { busy = false; requestController = null; await sendOverlay(true); }
  }
}

function json(res, status, value) { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(value)); }
async function body(req) {
  if (!req.headers['content-type']?.startsWith('application/json')) throw new Error('Expected JSON.');
  let data = '';
  for await (const chunk of req) { data += chunk; if (Buffer.byteLength(data) > 12000) throw new Error('Request is too large.'); }
  return JSON.parse(data || '{}');
}
async function handle(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  if (!allowedHosts.has(req.headers.host)) return json(res, 403, { error: 'Unexpected host.' });
  const origin = `http://${req.headers.host}`;
  if (req.headers.origin && req.headers.origin !== origin) return json(res, 403, { error: 'Cross-site request blocked.' });
  const url = new URL(req.url, origin);
  try {
    if (isLocal(req) && url.pathname === '/auth/callback') {
      await auth.callback(url.searchParams); models = []; resetConversation();
      res.writeHead(303, { Location: '/' }); return res.end();
    }
    if (req.method === 'GET' && ['/', '/app.js', '/style.css'].includes(url.pathname)) {
      if (url.pathname === '/' && isLocal(req)) res.setHeader('Set-Cookie', `attune=${cookie}; HttpOnly; SameSite=Strict; Path=/`);
      const file = url.pathname === '/' ? 'index.html' : url.pathname.slice(1);
      res.setHeader('Content-Type', file.endsWith('js') ? 'text/javascript' : file.endsWith('css') ? 'text/css' : 'text/html');
      return res.end(await readFile(new URL(`./public/${file}`, import.meta.url)));
    }
    if (req.method === 'POST' && url.pathname === '/pair' && !isLocal(req)) {
      const ip = req.socket.remoteAddress;
      const attempts = pairAttempts.get(ip) || { count: 0, start: Date.now() };
      if (Date.now() - attempts.start > 600000) { attempts.count = 0; attempts.start = Date.now(); }
      pairAttempts.set(ip, attempts);
      if (++attempts.count > 10) return json(res, 429, { error: 'Too many pairing attempts. Wait ten minutes.' });
      const data = await body(req);
      if (Date.now() > pairExpires || !tokenMatches(data.pin, pairPin)) return json(res, 403, { error: 'Pairing code expired or incorrect. Get a new code on the Mac.' });
      const token = randomBytes(32).toString('hex'); phoneSessions.add(token);
      pairExpires = 0; // One phone per explicit pairing code. Generate a new code to add another.
      return json(res, 200, { token });
    }
    if (!authenticated(req)) return json(res, 401, { error: 'Pair this phone using the code on your Mac.', local: isLocal(req) });
    if (req.method === 'GET' && url.pathname === '/state') {
      return json(res, 200, { context: context(), question, answer, error, busy, overlay,
        connectedAccount: !!auth.active?.access_token, models, selectedModel,
        verification: !!verificationPackage, local: isLocal(req),
        ...(isLocal(req) ? { auth: auth.publicState(), pair: { pin: Date.now() < pairExpires ? pairPin : null,
          urls: addresses.map(ip => `http://${ip}:${port}`) }, support: capabilities } : {}) });
    }
    if (req.method !== 'POST') return json(res, 404, { error: 'Not found.' });
    if (req.headers['x-attune-request'] !== '1') return json(res, 403, { error: 'Missing request header.' });
    const data = await body(req);
    if (url.pathname.startsWith('/auth/') && !isLocal(req)) return json(res, 403, { error: 'Manage ChatGPT accounts on the Mac.' });
    switch (url.pathname) {
      case '/auth/start':
        return json(res, 200, { url: auth.begin(`${localOrigin}/auth/callback`, data.accountId) });
      case '/auth/select':
        stopAnswer(); await auth.select(data.id); models = []; resetConversation(); break;
      case '/auth/signout': {
        stopAnswer(); overlay = false; await sendOverlay(true);
        const revoked = await auth.signOut(); models = []; resetConversation();
        return json(res, 200, { message: revoked ? 'Signed out.' : 'Signed out locally. Remote revocation could not be confirmed; disconnect AttuneTV in ChatGPT Settings.' });
      }
      case '/pair/reset':
        if (!isLocal(req)) throw new Error('Use the Mac to pair a phone.');
        pairPin = String(randomInt(100000, 1000000)); pairExpires = Date.now() + 600000; break;
      case '/phones/revoke':
        if (!isLocal(req)) throw new Error('Use the Mac to disconnect phones.');
        phoneSessions.clear(); pairExpires = 0; overlay = false; resetConversation(); break;
      case '/models': await ensureModels(); break;
      case '/model':
        if (busy) throw new Error('Stop the current answer before changing models.');
        await ensureModels();
        if (!models.some(m => m.id === data.id)) throw new Error('Choose an available model.');
        selectedModel = data.id; resetConversation(); break;
      case '/context':
        if (!context().supported) throw new Error('This app is not supported yet.');
        resetConversation();
        manual = { pkg: snapshot.pkg, title: String(data.title || '').trim().slice(0, 240), episode: String(data.episode || '').trim().slice(0, 120) };
        break;
      case '/ask':
        // Reserve the request synchronously; errors stay visible without exposing token responses.
        void ask(String(data.question || '')).catch(e => { error = e.message; }); break;
      case '/overlay':
        if (data.visible && !context().supported) throw new Error('Chat is unavailable in this app.');
        overlay = data.visible === true; overlayUntil = overlay ? Date.now() + 90000 : 0;
        if (!overlay) { generation++; requestController?.abort(); requestController = null; busy = false; }
        await sendOverlay(true); break;
      case '/stop': stopAnswer(); error = 'Answer stopped.'; break;
      case '/clear': resetConversation(); manual = null; overlay = false; break;
      default: return json(res, 404, { error: 'Not found.' });
    }
    await sendOverlay(); return json(res, 200, { ok: true });
  } catch (e) {
    // Never return OAuth HTTP bodies, auth URLs, or token values in errors.
    if (url.pathname === '/auth/callback') {
      res.writeHead(400, { 'Content-Type': 'text/plain' });
      return res.end('ChatGPT sign-in could not be completed. Return to the companion and try again.');
    }
    return json(res, 400, { error: e.message || 'Request failed.' });
  }
}

const servers = [http.createServer(handle), http.createServer(handle)];
for (const server of servers) { server.requestTimeout = 15000; server.headersTimeout = 10000; server.maxHeadersCount = 40; }
await new Promise((resolve, reject) => servers[0].once('error', reject).listen(authPort, '127.0.0.1', resolve));
await new Promise((resolve, reject) => servers[1].once('error', reject).listen(port, '0.0.0.0', resolve));
console.log(`AttuneTV companion: ${localOrigin}`);
console.log('Open the local page to connect ChatGPT and pair a phone. No account credentials are logged.');
if (verificationPackage) console.log(`Verification mode for ${providers[verificationPackage] || 'invalid package'}; this is not a support claim.`);
void poll();
async function shutdown() {
  if (stopping) return; stopping = true; overlay = false; stopAnswer();
  await sendOverlay(true); for (const server of servers) server.close(); process.exit(0);
}
process.on('SIGINT', shutdown); process.on('SIGTERM', shutdown);
