import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createApp } from '../server/app.mjs';
import { hash } from '../server/store.mjs';
const origin = 'http://127.0.0.1:8787', key = 'fixture-key-not-a-real-secret';
async function fixture(t, provider) {
  const calls = [];
  const service = createApp({ dbPath: ':memory:', encryptionKey: randomBytes(32), origin, allowLocal: true,
    provider: provider || { models: async () => [{ id: 'fixture-model', name: 'Test model' }], reply: async (a, c, m) => { calls.push({ a, c, m }); return 'Fixture answer for testing only.'; } } });
  const server = service.app.listen(0, '127.0.0.1'); await new Promise(r => server.once('listening', r));
  t.after(async () => { server.closeAllConnections(); await new Promise(r => server.close(r)); await service.close(); });
  async function req(path, body, credential, extra = {}) {
    const headers = { Origin: origin, ...extra };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (credential) { if (credential.startsWith('cookie:')) headers.Cookie = credential.slice(7); else headers.Authorization = 'Bearer ' + credential; }
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api${path}`, { method: body === undefined ? 'GET' : 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: response.status, body: await response.json(), cookie: response.headers.get('set-cookie')?.split(';')[0] };
  }
  async function paired() {
    const tv = (await req('/tv', {})).body.credential;
    const pairing = (await req('/pair', {}, tv)).body;
    const ticket = pairing.link.split('pair=')[1];
    const claim = await req('/pair/claim', { ticket }); const phone = 'cookie:' + claim.cookie;
    assert.equal((await req('/pair/approve', { code: claim.body.code }, tv)).status, 200);
    return { tv, phone, ticket };
  }
  async function connected() {
    const p = await paired();
    assert.equal((await req('/account', { provider: 'openai', key, model: 'fixture-model', billingAccepted: true, dailyLimit: 20 }, p.phone)).status, 200);
    const state = (await req('/state', undefined, p.tv)).body;
    assert.equal((await req('/context', { revision: state.revision, title: 'A show', episode: 'S1 E2', progress: '10 minutes' }, p.tv)).status, 200);
    return p;
  }
  return { service, req, paired, connected, calls };
}
test('phone needs TV confirmation, ticket is one-use, and unauthenticated history is denied', async t => {
  const { req } = await fixture(t); const tv = (await req('/tv', {})).body.credential;
  const pair = (await req('/pair', {}, tv)).body, ticket = pair.link.split('pair=')[1];
  assert.ok(pair.qr.startsWith('data:image/png;base64,'));
  const claim = await req('/pair/claim', { ticket }); const phone = 'cookie:' + claim.cookie;
  assert.equal((await req('/state', undefined, phone)).status, 401);
  assert.equal((await req('/pair/claim', { ticket })).status, 410);
  assert.equal((await req('/pair/approve', { code: '0000' }, tv)).status, 409);
  await req('/pair/approve', { code: claim.body.code }, tv);
  assert.equal((await req('/pair/wait', undefined, phone)).body.approved, true);
  assert.equal((await req('/state')).status, 401);
});
test('expired and superseded pairing links cannot claim a phone', async t => {
  const { service, req } = await fixture(t); const tv = (await req('/tv', {})).body.credential;
  const first = (await req('/pair', {}, tv)).body.link.split('pair=')[1];
  const second = (await req('/pair', {}, tv)).body.link.split('pair=')[1];
  assert.equal((await req('/pair/claim', { ticket: first })).status, 410);
  service.store.db.prepare('UPDATE pairs SET expires=0 WHERE id=?').run(hash(second));
  assert.equal((await req('/pair/claim', { ticket: second })).status, 410);
});
test('cross-site requests and TV API-key setup are rejected', async t => {
  const { req, paired } = await fixture(t); const p = await paired();
  assert.equal((await req('/account/check', { provider: 'openai', key }, p.tv)).status, 403);
  assert.equal((await req('/clear', {}, p.phone, { Origin: 'https://evil.example' })).status, 403);
  assert.equal((await req('/account/check', { provider: 'openai', key }, p.phone, { Origin: 'null' })).status, 403);
  assert.equal((await req('/state', undefined, p.tv, { Origin: 'null' })).status, 200);
});
test('consent and model selection are required; keys are encrypted and absent from state', async t => {
  const { service, req, paired } = await fixture(t); const p = await paired();
  const body = { provider: 'openai', key, model: 'fixture-model', dailyLimit: 20 };
  assert.equal((await req('/account', body, p.phone)).status, 400);
  assert.equal((await req('/account', { ...body, billingAccepted: true, model: 'not-available' }, p.phone)).status, 400);
  assert.equal((await req('/account', { ...body, billingAccepted: true }, p.phone)).status, 200);
  assert.ok(!JSON.stringify((await req('/state', undefined, p.tv)).body).includes(key));
  assert.ok(!service.store.db.prepare('SELECT data FROM accounts').get().data.includes(key));
});
test('context and conversation sync without any live TV connection; retries do not bill twice', async t => {
  const { req, connected, calls } = await fixture(t); const p = await connected();
  const revision = (await req('/state', undefined, p.phone)).body.revision;
  const body = { id: 'request_1234567890', question: 'Who is that?', revision };
  assert.equal((await req('/messages', body, p.phone)).status, 202);
  await new Promise(r => setTimeout(r, 20));
  assert.equal((await req('/messages', body, p.phone)).status, 200);
  const state = (await req('/state', undefined, p.tv)).body;
  assert.equal(calls.length, 1); assert.equal(state.messages.length, 2); assert.equal(state.used, 1);
  assert.equal(state.messages[1].text, 'Fixture answer for testing only.');
  assert.equal(calls[0].c.episode, 'S1 E2');
});
test('stale context edits are rejected and a new show clears old history', async t => {
  const { req, connected } = await fixture(t); const p = await connected(); const before = (await req('/state', undefined, p.tv)).body;
  await req('/context', { title: 'Different show', revision: before.revision }, p.phone);
  assert.equal((await req('/context', { title: 'Stale title', revision: before.revision }, p.tv)).status, 409);
  assert.equal((await req('/messages', { id: 'request_1234567890', question: 'Old show?', revision: before.revision }, p.tv)).status, 409);
  assert.equal((await req('/state', undefined, p.tv)).body.messages.length, 0);
});
test('daily limits are enforced on the server, including from TV', async t => {
  const { req, service, connected } = await fixture(t); const p = await connected();
  const room = await service.store.room((await service.store.authenticate(p.tv)).room); room.dailyLimit = 1; await service.store.save(room);
  await req('/messages', { id: 'request_1234567890', question: 'First?', revision: room.revision }, p.tv);
  await new Promise(r => setTimeout(r, 20));
  assert.equal((await req('/messages', { id: 'request_2234567890', question: 'Second?', revision: room.revision }, p.phone)).status, 429);
});
test('stopped or replaced answers cannot resurrect a cleared conversation', async t => {
  let finish;
  const { req, connected } = await fixture(t, { models: async () => [{ id: 'fixture-model' }], reply: () => new Promise(r => { finish = r; }) });
  const p = await connected(); const state = (await req('/state', undefined, p.tv)).body;
  await req('/messages', { id: 'request_1234567890', question: 'A question', revision: state.revision }, p.phone);
  await req('/clear', {}, p.tv); finish('Late answer'); await new Promise(r => setTimeout(r, 20));
  assert.deepEqual((await req('/state', undefined, p.tv)).body.messages, []);
});
test('room boundaries prevent another TV reading or managing a conversation', async t => {
  const { req, connected } = await fixture(t); const p = await connected(), other = (await req('/tv', {})).body.credential;
  const state = (await req('/state', undefined, other)).body;
  assert.equal(state.context.title, ''); assert.equal(state.account, null);
  await req('/disconnect', {}, other);
  assert.equal((await req('/state', undefined, p.tv)).status, 200);
});
test('disconnect erases the room, key and sessions; old cookies cannot regain access', async t => {
  const { req, service, connected } = await fixture(t); const p = await connected();
  await req('/disconnect', {}, p.phone);
  assert.equal((await req('/state', undefined, p.tv)).status, 401);
  assert.equal((await req('/state', undefined, p.phone)).status, 401);
  assert.equal(service.store.db.prepare('SELECT COUNT(*) AS n FROM accounts').get().n, 0);
});

test('native TV opaque-origin flow shares phone setup and never receives provider keys', async t => {
  const { req } = await fixture(t);
  const opaque = { Origin: 'null' };
  const tv = (await req('/tv', {}, null, opaque)).body.credential;
  const pair = (await req('/pair', {}, tv, opaque)).body;
  const claim = await req('/pair/claim', { ticket: pair.link.split('pair=')[1] });
  const phone = 'cookie:' + claim.cookie;
  assert.equal((await req('/pair/approve', { code: claim.body.code }, tv, opaque)).status, 200);
  assert.equal((await req('/account', { provider: 'openai', key, model: 'fixture-model', billingAccepted: true, dailyLimit: 20 }, phone)).status, 200);
  const before = (await req('/state', undefined, tv, opaque)).body;
  assert.equal((await req('/context', { revision: before.revision, title: 'A film', service: 'Prime Video' }, tv, opaque)).status, 200);
  const state = (await req('/state', undefined, tv, opaque)).body;
  assert.equal((await req('/messages', { id: 'native_request_12345', question: 'Who is the director?', revision: state.revision }, tv, opaque)).status, 202);
  await new Promise(r => setTimeout(r, 30));
  const synced = (await req('/state', undefined, phone)).body;
  assert.equal(synced.messages[1].status, 'complete');
  assert.equal(synced.context.service, 'Prime Video');
  assert.ok(!JSON.stringify(state).includes(key));
  assert.equal((await req('/account/check', { provider: 'openai', key }, tv, opaque)).status, 403);
});
