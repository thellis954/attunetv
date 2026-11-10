import express from 'express';
import { randomInt } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import QRCode from 'qrcode';
import { token, hash } from './store.mjs';
import { Database } from './database.mjs';
import { providers, ProviderClient, PublicError } from './providers.mjs';

const text = (value, max) => typeof value === 'string' ? value.trim().slice(0, max) : '';
const cookieValue = req => (req.headers.cookie || '').split(';').map(v => v.trim()).find(v => v.startsWith('attune_phone='))?.slice(13);
export function createApp({ dbPath, encryptionKey, origin, provider = new ProviderClient(), allowLocal = false, database, background = promise => { promise.catch(() => {}); }, clientAddress = req => req.socket.remoteAddress, cronSecret }) {
  const url = new URL(origin);
  if (url.origin !== origin || (url.protocol !== 'https:' && !(allowLocal && ['localhost', '127.0.0.1'].includes(url.hostname)))) throw new Error('PUBLIC_ORIGIN must be an HTTPS origin (or explicit loopback development).');
  const store = database || new Database({ dbPath, encryptionKey }), app = express(), jobs = new Map();
  app.disable('x-powered-by');
  const fail = (message, status = 400) => { throw new PublicError(message, status); };
  const cookie = (res, credential) => res.cookie('attune_phone', credential, { httpOnly: true, secure: url.protocol === 'https:', sameSite: 'strict', maxAge: 30 * 86400000, path: '/' });
  const stop = roomId => { jobs.get(roomId)?.abort(); jobs.delete(roomId); };
  app.use((req, res, next) => {
    res.set({ 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY', 'Permissions-Policy': 'camera=(), microphone=(self), geolocation=()', 'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'" });
    if (url.protocol === 'https:') res.set('Strict-Transport-Security', 'max-age=31536000');
    // Packaged Tizen documents have an opaque origin. Only TV bearer credentials
    // work cross-origin. Phone cookies and API-key setup never do.
    if (req.headers.origin === 'null') res.set({ 'Access-Control-Allow-Origin': 'null', 'Vary': 'Origin', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': 'Authorization, Content-Type' });
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });
  app.use(express.json({ limit: '12kb', strict: true }));
  app.use('/api', (req, res, next) => {
    if (req.method === 'POST') {
      if (!req.is('application/json')) return next(new PublicError('JSON is required.', 415));
      const opaqueTV = req.headers.origin === 'null' && (req.path === '/tv' || req.headers.authorization?.startsWith('Bearer '));
      if (req.headers.origin !== origin && !opaqueTV) return next(new PublicError('Open AttuneTV from its original address and try again.', 403));
    }
    next();
  });
  const publicRoutes = new Set(['/api/tv', '/api/providers', '/api/pair/claim', '/api/pair/wait']);
  function route(method, path, handler) {
    app[method](path, async (req, res) => {
      const allowed = await store.transaction(async () => {
        if (await store.rate('maintenance', 1)) await store.cleanup();
        return store.rate(hash('ip:' + clientAddress(req)), 240);
      });
      if (!allowed) fail('Too many requests. Wait a minute and try again.', 429);
      // Verify access before contacting a provider; recheck in the commit transaction.
      if (path === '/api/account' || path === '/api/account/check') {
        await store.transaction(async () => { await authenticate(req); requirePhone(req); });
        if (path === '/api/account' && req.body.billingAccepted !== true) fail('Confirm that API usage is billed to your provider account.');
        req.models = await provider.models(req.body.provider, req.body.key);
      }
      let output, status = 200;
      const response = { status(code) { status = code; return response; }, json(value) { output = value; },
        cookie: (...args) => res.cookie(...args), clearCookie: (...args) => res.clearCookie(...args) };
      await store.transaction(async () => {
        if (!publicRoutes.has(path)) await authenticate(req);
        await handler(req, response);
      });
      if (req.qr) output.qr = await QRCode.toDataURL(req.qr, { width: 400, margin: 4, errorCorrectionLevel: 'M' });
      if (req.launch) background(req.launch());
      res.status(status).json(output);
    });
  }
  async function generate(room, answerId, account, history) {
    const controller = new AbortController(); jobs.set(room.id, controller);
    let checking = false;
    // Cancellation is persisted; any other Vercel instance can stop this request.
    const cancellation = setInterval(async () => {
      if (checking) return; checking = true;
      try {
        const current = await store.room(room.id);
        if (!current || current.revision !== room.revision || !current.messages.some(m => m.id === answerId && m.status === 'pending')) controller.abort();
      } catch { controller.abort(); } finally { checking = false; }
    }, 2000);
    const timeout = setTimeout(() => controller.abort(), 90000);
    let answer, failure;
    try { answer = await provider.reply(account, room.context, history, controller.signal); }
    catch (error) { failure = error instanceof PublicError ? error.message : 'The answer could not finish. Check your connection and try again.'; }
    finally { clearInterval(cancellation); clearTimeout(timeout); if (jobs.get(room.id) === controller) jobs.delete(room.id); }
    try {
      await store.transaction(async () => {
        const current = await store.room(room.id);
        if (!current || current.revision !== room.revision) return;
        const message = current.messages.find(m => m.id === answerId && m.status === 'pending' && m.deadline >= Date.now());
        if (message) { message.text = failure || answer; message.status = failure ? 'error' : 'complete'; await store.save(current); }
      });
    } catch { /* A failed invocation remains pending until its durable deadline, never retried automatically. */ }
  }
  app.get('/health', async (req, res) => { await store.one('SELECT 1 AS ok'); res.json({ ok: true, version: '0.1.0' }); });
  app.get('/api/maintenance', async (req, res) => {
    if (!cronSecret || hash(req.headers.authorization || '') !== hash('Bearer ' + cronSecret)) fail('Unauthorized.', 401);
    await store.transaction(() => store.cleanup()); res.json({ ok: true });
  });
  app.get('/api/providers', (req, res) => res.json({ providers: Object.entries(providers).map(([id, p]) => ({ id, name: p.name, console: p.console, billing: p.billing, pricing: p.pricing })) }));
  route('post', '/api/tv', async (req, res) => {
    const n = (await store.one('SELECT COUNT(*) AS n FROM rooms')).n;
    if (n >= 1000) fail('This service has reached its preview capacity.', 503);
    const { credential } = (await store.create()); res.status(201).json({ credential });
  });
  route('post', '/api/pair/claim', async (req, res) => {
    const pair = (await store.one('SELECT * FROM pairs WHERE id=? AND expires>?', hash(text(req.body.ticket, 128)), Date.now()));
    if (!pair || pair.candidate) fail('This pairing code has expired or was already used. Generate a new one on the TV.', 410);
    if (req.headers.origin !== origin) fail('Pair from the phone page.', 403);
    const candidate = token(), code = String(randomInt(1000, 10000));
    (await store.run('UPDATE pairs SET candidate=?,code=? WHERE id=?', hash(candidate), code, pair.id));
    cookie(res, candidate); res.json({ code });
  });
  route('get', '/api/pair/wait', async (req, res) => {
    const credential = cookieValue(req), session = (await store.authenticate(credential));
    if (session?.role === 'phone') return res.json({ approved: true });
    const pair = (await store.one('SELECT code FROM pairs WHERE candidate=? AND expires>?', hash(credential || ''), Date.now()));
    if (!pair) fail('Pairing expired. Scan a new code on the TV.', 410);
    res.json({ approved: false, code: pair.code });
  });
  async function authenticate(req) {
    const bearer = req.headers.authorization?.startsWith('Bearer ') ? req.headers.authorization.slice(7) : null;
    const session = await store.authenticate(bearer || cookieValue(req));
    if (!session || (bearer && session.role !== 'tv') || (!bearer && session.role !== 'phone')) fail('Pair your phone with the TV to continue.', 401);
    if (session.role === 'phone' && req.headers.origin === 'null') fail('Phone setup requires the secure phone page.', 403);
    req.session = session; req.room = await store.room(session.room);
    if (!req.room) fail('This conversation expired. Start a new one on the TV.', 401);
    let changed = false;
    req.room.messages.forEach(m => { if (m.status === 'pending' && (!m.deadline || m.deadline < Date.now())) { m.status = 'error'; m.text = 'The answer was interrupted. Ask again to retry.'; changed = true; } });
    if (changed) await store.save(req.room);
  }
  const requireTV = req => { if (req.session.role !== 'tv') fail('Use the TV remote for this action.', 403); };
  const requirePhone = req => { if (req.session.role !== 'phone' || req.headers.origin !== origin) fail('Manage your API key on the paired phone.', 403); };
  route('get', '/api/state', async (req, res) => {
    const account = (await store.account(req.room.id));
    const paired = (await store.one("SELECT COUNT(*) AS n FROM sessions WHERE room=? AND role='phone' AND expires>?", req.room.id, Date.now())).n > 0;
    const pair = req.session.role === 'tv' ? (await store.one('SELECT code,expires FROM pairs WHERE room=? AND expires>?', req.room.id, Date.now())) : null;
    const room = req.room;
    res.json({ context: room.context, revision: room.revision, messages: room.messages, paired, pair, role: req.session.role,
      account: account ? { provider: account.provider, model: account.model } : null,
      dailyLimit: room.dailyLimit, used: room.day === new Date().toISOString().slice(0, 10) ? room.used : 0 });
  });
  route('post', '/api/pair', async (req, res) => {
    requireTV(req);
    const ticket = token(); (await store.run('DELETE FROM pairs WHERE room=?', req.room.id));
    const expires = Date.now() + 5 * 60000;
    (await store.run('INSERT INTO pairs(id,room,expires) VALUES(?,?,?)', hash(ticket), req.room.id, expires));
    // Fragment keeps the pairing secret out of HTTP access logs and referrers.
    const link = origin + '/phone#pair=' + ticket;
    req.qr = link; res.json({ link, expires });
  });
  route('post', '/api/pair/approve', async (req, res) => {
    requireTV(req);
    const pair = (await store.one('SELECT * FROM pairs WHERE room=? AND expires>?', req.room.id, Date.now()));
    if (!pair?.candidate || req.body.code !== pair.code) fail('Compare the code on the phone and TV, then try again.', 409);
    // Exactly one phone can hold account-management access. Re-pairing also
    // removes the old key and transcript before sharing with a new phone.
    stop(req.room.id);
    (await store.run("DELETE FROM sessions WHERE room=? AND role='phone'", req.room.id));
    (await store.forgetAccount(req.room.id));
    req.room.messages = []; req.room.revision++; (await store.save(req.room));
    (await store.run('INSERT INTO sessions VALUES(?,?,?,?)', pair.candidate, pair.room, 'phone', Date.now() + 30 * 86400000));
    (await store.run('DELETE FROM pairs WHERE room=?', req.room.id));
    res.json({ ok: true });
  });
  route('post', '/api/account/check', async (req, res) => {
    requirePhone(req);
    const models = req.models;
    if (!models.length) fail('No text models are available for this key. Check its permissions.');
    res.json({ models });
  });
  route('post', '/api/account', async (req, res) => {
    requirePhone(req);
    if (req.body.billingAccepted !== true) fail('Confirm that API usage is billed to your provider account.');
    const dailyLimit = Number(req.body.dailyLimit);
    if (!Number.isInteger(dailyLimit) || dailyLimit < 1 || dailyLimit > 100) fail('Choose a daily question limit between 1 and 100.');
    const models = req.models;
    if (!models.some(m => m.id === req.body.model)) fail('Choose a model available to this API key.');
    stop(req.room.id);
    // Reload after network I/O so a concurrent context change is preserved.
    const room = (await store.room(req.room.id));
    if (!room || !(await store.authenticate(cookieValue(req)))) fail('Pairing changed. Connect the phone again.', 401);
    (await store.setAccount(room.id, { provider: req.body.provider, key: req.body.key, model: req.body.model }));
    room.dailyLimit = dailyLimit; room.messages = []; room.revision++; (await store.save(room));
    res.json({ ok: true });
  });
  route('post', '/api/account/remove', async (req, res) => { requirePhone(req); stop(req.room.id); (await store.forgetAccount(req.room.id)); req.room.messages = []; req.room.revision++; (await store.save(req.room)); res.json({ ok: true }); });
  route('post', '/api/context', async (req, res) => {
    if (req.body.revision !== req.room.revision) fail('The viewing context changed on the other screen. Reload it before saving.', 409);
    const context = { title: text(req.body.title, 240), episode: text(req.body.episode, 120), progress: text(req.body.progress, 160), service: text(req.body.service, 80) };
    if (!context.title) fail('Enter the movie or show title first.');
    stop(req.room.id); req.room.context = context; req.room.messages = []; req.room.revision++; (await store.save(req.room));
    res.json({ ok: true });
  });
  route('post', '/api/messages', async (req, res) => {
    const room = req.room, id = text(req.body.id, 80), question = text(req.body.question, 2000);
    if (!/^[a-zA-Z0-9_-]{16,80}$/.test(id) || !question) fail('Enter a question and try again.');
    if (await store.one('SELECT id FROM receipts WHERE room=? AND id=?', room.id, id)) return res.json({ ok: true }); // Retry never incurs a second request.
    if (!room.context.title) fail('Set the movie or show title first.');
    if (req.body.revision !== room.revision) fail('The show changed. Review the viewing context and ask again.', 409);
    if (room.messages.some(m => m.status === 'pending')) fail('Wait for the current answer or stop it first.', 409);
    const account = (await store.account(room.id)); if (!account) fail('Connect your AI provider from the paired phone first.', 409);
    const day = new Date().toISOString().slice(0, 10); if (room.day !== day) { room.day = day; room.used = 0; }
    if (room.used >= room.dailyLimit) fail('Your daily question limit is reached. It resets at midnight UTC.', 429);
    const history = room.messages.filter(m => m.status === 'complete').slice(-10).map(m => ({ role: m.role, content: m.text }));
    history.push({ role: 'user', content: question });
    room.used++; const answerId = token();
    room.messages = room.messages.slice(-38).concat([{ id, role: 'user', text: question, status: 'complete' }, { id: answerId, role: 'assistant', text: '', status: 'pending', deadline: Date.now() + 100000 }]);
    await store.save(room);
    await store.run('INSERT INTO receipts VALUES(?,?,?)', room.id, id, Date.now() + 30 * 86400000);
    req.launch = () => generate(room, answerId, account, history);
    res.status(202).json({ ok: true });
  });
  route('post', '/api/stop', async (req, res) => { stop(req.room.id); req.room.messages.forEach(m => { if (m.status === 'pending') { m.status = 'error'; m.text = 'Stopped. The provider may charge for work already performed.'; } }); (await store.save(req.room)); res.json({ ok: true }); });
  route('post', '/api/clear', async (req, res) => { stop(req.room.id); req.room.messages = []; req.room.revision++; (await store.save(req.room)); res.json({ ok: true }); });
  route('post', '/api/disconnect', async (req, res) => {
    stop(req.room.id); (await store.run('DELETE FROM rooms WHERE id=?', req.room.id));
    res.clearCookie('attune_phone', { path: '/' }); res.json({ ok: true });
  });
  const publicDir = fileURLToPath(new URL('../web/', import.meta.url));
  app.get(['/tv', '/phone', '/'], (req, res) => res.sendFile(publicDir + 'index.html'));
  app.use(express.static(publicDir, { etag: false, index: false }));
  app.use((error, req, res, next) => {
    if (res.headersSent) return next(error);
    const status = error instanceof PublicError ? error.status : error.type === 'entity.too.large' ? 413 : error instanceof SyntaxError ? 400 : 500;
    res.status(status).json({ error: error instanceof PublicError ? error.message : status === 413 ? 'The request is too large.' : 'This request could not be completed. Try again.' });
  });
  return { app, store, async close() { for (const c of jobs.values()) c.abort(); jobs.clear(); await store.close(); } };
}
