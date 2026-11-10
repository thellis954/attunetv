import express from 'express';
import { randomInt } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import QRCode from 'qrcode';
import { Store, token, hash } from './store.mjs';
import { providers, ProviderClient, PublicError } from './providers.mjs';

const text = (value, max) => typeof value === 'string' ? value.trim().slice(0, max) : '';
const cookieValue = req => (req.headers.cookie || '').split(';').map(v => v.trim()).find(v => v.startsWith('attune_phone='))?.slice(13);
export function createApp({ dbPath, encryptionKey, origin, provider = new ProviderClient(), allowLocal = false }) {
  const url = new URL(origin);
  if (url.origin !== origin || (url.protocol !== 'https:' && !(allowLocal && ['localhost', '127.0.0.1'].includes(url.hostname)))) throw new Error('PUBLIC_ORIGIN must be an HTTPS origin (or explicit loopback development).');
  const store = new Store(dbPath, encryptionKey), app = express(), jobs = new Map(), limits = new Map();
  app.disable('x-powered-by');
  const fail = (message, status = 400) => { throw new PublicError(message, status); };
  const cookie = (res, credential) => res.cookie('attune_phone', credential, { httpOnly: true, secure: url.protocol === 'https:', sameSite: 'strict', maxAge: 30 * 86400000, path: '/' });
  const stop = roomId => { jobs.get(roomId)?.abort(); jobs.delete(roomId); };
  const cleanup = setInterval(() => { store.cleanup(); for (const [k, v] of limits) if (v.until < Date.now()) limits.delete(k); }, 60000); cleanup.unref();
  app.use((req, res, next) => {
    res.set({ 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY', 'Permissions-Policy': 'camera=(), microphone=(), geolocation=()', 'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'" });
    if (url.protocol === 'https:') res.set('Strict-Transport-Security', 'max-age=31536000');
    // Packaged Tizen documents have an opaque origin. Only TV bearer credentials
    // work cross-origin. Phone cookies and API-key setup never do.
    if (req.headers.origin === 'null') res.set({ 'Access-Control-Allow-Origin': 'null', 'Vary': 'Origin', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': 'Authorization, Content-Type' });
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });
  app.use(express.json({ limit: '12kb', strict: true }));
  app.use('/api', (req, res, next) => {
    const id = req.socket.remoteAddress, entry = limits.get(id) || { count: 0, until: Date.now() + 60000 };
    if (entry.until < Date.now()) { entry.count = 0; entry.until = Date.now() + 60000; }
    entry.count++; limits.set(id, entry);
    if (entry.count > 240) return next(new PublicError('Too many requests. Wait a minute and try again.', 429));
    if (req.method === 'POST') {
      if (!req.is('application/json')) return next(new PublicError('JSON is required.', 415));
      const opaqueTV = req.headers.origin === 'null' && (req.path === '/tv' || req.headers.authorization?.startsWith('Bearer '));
      if (req.headers.origin !== origin && !opaqueTV) return next(new PublicError('Open AttuneTV from its original address and try again.', 403));
    }
    next();
  });
  app.get('/health', (req, res) => res.json({ ok: true, version: '0.1.0' }));
  app.get('/api/providers', (req, res) => res.json({ providers: Object.entries(providers).map(([id, p]) => ({ id, name: p.name, console: p.console, billing: p.billing, pricing: p.pricing })) }));
  app.post('/api/tv', (req, res) => {
    const n = store.db.prepare('SELECT COUNT(*) AS n FROM rooms').get().n;
    if (n >= 1000) fail('This service has reached its preview capacity.', 503);
    const { credential } = store.create(); res.status(201).json({ credential });
  });
  app.post('/api/pair/claim', (req, res) => {
    const pair = store.db.prepare('SELECT * FROM pairs WHERE id=? AND expires>?').get(hash(text(req.body.ticket, 128)), Date.now());
    if (!pair || pair.candidate) fail('This pairing code has expired or was already used. Generate a new one on the TV.', 410);
    if (req.headers.origin !== origin) fail('Pair from the phone page.', 403);
    const candidate = token(), code = String(randomInt(1000, 10000));
    store.db.prepare('UPDATE pairs SET candidate=?,code=? WHERE id=?').run(hash(candidate), code, pair.id);
    cookie(res, candidate); res.json({ code });
  });
  app.get('/api/pair/wait', (req, res) => {
    const credential = cookieValue(req), session = store.authenticate(credential);
    if (session?.role === 'phone') return res.json({ approved: true });
    const pair = store.db.prepare('SELECT code FROM pairs WHERE candidate=? AND expires>?').get(hash(credential || ''), Date.now());
    if (!pair) fail('Pairing expired. Scan a new code on the TV.', 410);
    res.json({ approved: false, code: pair.code });
  });
  app.use('/api', (req, res, next) => {
    const bearer = req.headers.authorization?.startsWith('Bearer ') ? req.headers.authorization.slice(7) : null;
    const session = store.authenticate(bearer || cookieValue(req));
    if (!session || (bearer && session.role !== 'tv') || (!bearer && session.role !== 'phone')) return next(new PublicError('Pair your phone with the TV to continue.', 401));
    if (session.role === 'phone' && req.headers.origin === 'null') return next(new PublicError('Phone setup requires the secure phone page.', 403));
    req.session = session; req.room = store.room(session.room);
    if (!req.room) return next(new PublicError('This conversation expired. Start a new one on the TV.', 401));
    next();
  });
  const requireTV = req => { if (req.session.role !== 'tv') fail('Use the TV remote for this action.', 403); };
  const requirePhone = req => { if (req.session.role !== 'phone' || req.headers.origin !== origin) fail('Manage your API key on the paired phone.', 403); };
  app.get('/api/state', (req, res) => {
    const account = store.account(req.room.id);
    const paired = store.db.prepare("SELECT COUNT(*) AS n FROM sessions WHERE room=? AND role='phone' AND expires>?").get(req.room.id, Date.now()).n > 0;
    const pair = req.session.role === 'tv' ? store.db.prepare('SELECT code,expires FROM pairs WHERE room=? AND expires>?').get(req.room.id, Date.now()) : null;
    const room = req.room;
    res.json({ context: room.context, revision: room.revision, messages: room.messages, paired, pair, role: req.session.role,
      account: account ? { provider: account.provider, model: account.model } : null,
      dailyLimit: room.dailyLimit, used: room.day === new Date().toISOString().slice(0, 10) ? room.used : 0 });
  });
  app.post('/api/pair', async (req, res) => {
    requireTV(req);
    const ticket = token(); store.db.prepare('DELETE FROM pairs WHERE room=?').run(req.room.id);
    const expires = Date.now() + 5 * 60000;
    store.db.prepare('INSERT INTO pairs(id,room,expires) VALUES(?,?,?)').run(hash(ticket), req.room.id, expires);
    // Fragment keeps the pairing secret out of HTTP access logs and referrers.
    const link = origin + '/phone#pair=' + ticket;
    res.json({ link, expires, qr: await QRCode.toDataURL(link, { width: 400, margin: 4, errorCorrectionLevel: 'M' }) });
  });
  app.post('/api/pair/approve', (req, res) => {
    requireTV(req);
    const pair = store.db.prepare('SELECT * FROM pairs WHERE room=? AND expires>?').get(req.room.id, Date.now());
    if (!pair?.candidate || req.body.code !== pair.code) fail('Compare the code on the phone and TV, then try again.', 409);
    // Exactly one phone can hold account-management access. Re-pairing also
    // removes the old key and transcript before sharing with a new phone.
    stop(req.room.id);
    store.db.prepare("DELETE FROM sessions WHERE room=? AND role='phone'").run(req.room.id);
    store.forgetAccount(req.room.id);
    req.room.messages = []; req.room.revision++; store.save(req.room);
    store.db.prepare('INSERT INTO sessions VALUES(?,?,?,?)').run(pair.candidate, pair.room, 'phone', Date.now() + 30 * 86400000);
    store.db.prepare('DELETE FROM pairs WHERE room=?').run(req.room.id);
    res.json({ ok: true });
  });
  app.post('/api/account/check', async (req, res) => {
    requirePhone(req);
    const models = await provider.models(req.body.provider, req.body.key);
    if (!models.length) fail('No text models are available for this key. Check its permissions.');
    res.json({ models });
  });
  app.post('/api/account', async (req, res) => {
    requirePhone(req);
    if (req.body.billingAccepted !== true) fail('Confirm that API usage is billed to your provider account.');
    const dailyLimit = Number(req.body.dailyLimit);
    if (!Number.isInteger(dailyLimit) || dailyLimit < 1 || dailyLimit > 100) fail('Choose a daily question limit between 1 and 100.');
    const models = await provider.models(req.body.provider, req.body.key);
    if (!models.some(m => m.id === req.body.model)) fail('Choose a model available to this API key.');
    stop(req.room.id);
    // Reload after network I/O so a concurrent context change is preserved.
    const room = store.room(req.room.id);
    if (!room || !store.authenticate(cookieValue(req))) fail('Pairing changed. Connect the phone again.', 401);
    store.setAccount(room.id, { provider: req.body.provider, key: req.body.key, model: req.body.model });
    room.dailyLimit = dailyLimit; room.messages = []; room.revision++; store.save(room);
    res.json({ ok: true });
  });
  app.post('/api/account/remove', (req, res) => { requirePhone(req); stop(req.room.id); store.forgetAccount(req.room.id); req.room.messages = []; req.room.revision++; store.save(req.room); res.json({ ok: true }); });
  app.post('/api/context', (req, res) => {
    if (req.body.revision !== req.room.revision) fail('The viewing context changed on the other screen. Reload it before saving.', 409);
    const context = { title: text(req.body.title, 240), episode: text(req.body.episode, 120), progress: text(req.body.progress, 160), service: text(req.body.service, 80) };
    if (!context.title) fail('Enter the movie or show title first.');
    stop(req.room.id); req.room.context = context; req.room.messages = []; req.room.revision++; store.save(req.room);
    res.json({ ok: true });
  });
  app.post('/api/messages', async (req, res) => {
    const room = req.room, id = text(req.body.id, 80), question = text(req.body.question, 2000);
    if (!/^[a-zA-Z0-9_-]{16,80}$/.test(id) || !question) fail('Enter a question and try again.');
    if (room.messages.some(m => m.id === id)) return res.json({ ok: true }); // Retry never incurs a second request.
    if (!room.context.title) fail('Set the movie or show title first.');
    if (req.body.revision !== room.revision) fail('The show changed. Review the viewing context and ask again.', 409);
    if (jobs.has(room.id)) fail('Wait for the current answer or stop it first.', 409);
    const account = store.account(room.id); if (!account) fail('Connect your AI provider from the paired phone first.', 409);
    const day = new Date().toISOString().slice(0, 10); if (room.day !== day) { room.day = day; room.used = 0; }
    if (room.used >= room.dailyLimit) fail('Your daily question limit is reached. It resets at midnight UTC.', 429);
    const history = room.messages.filter(m => m.status === 'complete').slice(-10).map(m => ({ role: m.role, content: m.text }));
    history.push({ role: 'user', content: question });
    room.used++; const answerId = token();
    room.messages = room.messages.slice(-38).concat([{ id, role: 'user', text: question, status: 'complete' }, { id: answerId, role: 'assistant', text: '', status: 'pending' }]);
    store.save(room); const controller = new AbortController(); jobs.set(room.id, controller);
    res.status(202).json({ ok: true });
    const timeout = setTimeout(() => controller.abort(), 90000);
    try {
      const answer = await provider.reply(account, room.context, history, controller.signal);
      if (jobs.get(room.id) !== controller) return;
      const current = store.room(room.id);
      if (jobs.get(room.id) !== controller || !current || current.revision !== room.revision) return;
      const message = current.messages.find(m => m.id === answerId);
      if (message) { message.text = answer; message.status = 'complete'; store.save(current); }
    } catch (error) {
      if (jobs.get(room.id) !== controller) return;
      const current = store.room(room.id);
      if (jobs.get(room.id) !== controller || !current || current.revision !== room.revision) return;
      const message = current.messages.find(m => m.id === answerId);
      if (message) { message.status = 'error'; message.text = error instanceof PublicError ? error.message : 'The answer could not finish. Check your connection and try again.'; store.save(current); }
    } finally { clearTimeout(timeout); if (jobs.get(room.id) === controller) jobs.delete(room.id); }
  });
  app.post('/api/stop', (req, res) => { stop(req.room.id); req.room.messages.forEach(m => { if (m.status === 'pending') { m.status = 'error'; m.text = 'Stopped. The provider may charge for work already performed.'; } }); store.save(req.room); res.json({ ok: true }); });
  app.post('/api/clear', (req, res) => { stop(req.room.id); req.room.messages = []; req.room.revision++; store.save(req.room); res.json({ ok: true }); });
  app.post('/api/disconnect', (req, res) => {
    stop(req.room.id); store.db.prepare('DELETE FROM rooms WHERE id=?').run(req.room.id);
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
  return { app, store, close() { clearInterval(cleanup); for (const c of jobs.values()) c.abort(); jobs.clear(); store.close(); } };
}
