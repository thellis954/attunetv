import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { Pool } from 'pg';
import { Database } from '../server/database.mjs';
import { createApp } from '../server/app.mjs';

const connectionString = process.env.TEST_DATABASE_URL;
test('Postgres shares pairing, receipts, limits and cancellation across independent instances', { skip: !connectionString }, async t => {
  const encryptionKey = randomBytes(32), origin = 'http://127.0.0.1:8787';
  const dbs = [0, 1].map(() => new Database({ pool: new Pool({ connectionString, max: 3 }), encryptionKey }));
  await Promise.all(dbs.map(d => d.ready));
  let calls = 0, finish;
  const provider = { models: async () => [{ id: 'fixture-model' }], reply: () => { calls++; return new Promise(r => { finish = r; }); } };
  const services = dbs.map(database => createApp({ database, origin, allowLocal: true, provider }));
  const servers = services.map(s => s.app.listen(0, '127.0.0.1'));
  await Promise.all(servers.map(s => new Promise(r => s.once('listening', r))));
  const rooms = [];
  t.after(async () => {
    for (const id of rooms) await dbs[0].transaction(() => dbs[0].run('DELETE FROM rooms WHERE id=?', id));
    for (const s of servers) { s.closeAllConnections(); await new Promise(r => s.close(r)); }
    await Promise.all(services.map(s => s.close()));
  });
  async function req(instance, path, body, credential) {
    const headers = { Origin: origin, 'Content-Type': 'application/json' };
    if (credential) headers[credential.startsWith('cookie:') ? 'Cookie' : 'Authorization'] = credential.startsWith('cookie:') ? credential.slice(7) : 'Bearer ' + credential;
    const response = await fetch(`http://127.0.0.1:${servers[instance].address().port}/api${path}`, { method: body === undefined ? 'GET' : 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: response.status, body: await response.json(), cookie: response.headers.get('set-cookie')?.split(';')[0] };
  }
  const tv = (await req(0, '/tv', {})).body.credential;
  assert.ok(tv); const roomId = (await dbs[0].authenticate(tv)).room; rooms.push(roomId);
  const ticket = (await req(1, '/pair', {}, tv)).body.link.split('pair=')[1];
  const claims = await Promise.all([0, 1].map(i => req(i, '/pair/claim', { ticket })));
  assert.deepEqual(claims.map(c => c.status).sort(), [200, 410]);
  const claim = claims.find(c => c.status === 200), phone = 'cookie:' + claim.cookie;
  assert.equal((await req(1, '/pair/approve', { code: claim.body.code }, tv)).status, 200);
  assert.equal((await req(0, '/account', { provider: 'openai', key: 'fixture-not-a-real-api-key', model: 'fixture-model', dailyLimit: 1, billingAccepted: true }, phone)).status, 200);
  let state = (await req(1, '/state', undefined, tv)).body;
  await req(0, '/context', { revision: state.revision, title: 'Fixture film' }, tv);
  state = (await req(1, '/state', undefined, phone)).body;
  const body = { id: 'request_same_1234567890', question: 'Who is that?', revision: state.revision };
  const responses = await Promise.all([0, 1].map(i => req(i, '/messages', body, phone)));
  assert.deepEqual(responses.map(r => r.status).sort(), [200, 202]); assert.equal(calls, 1);
  assert.equal((await req(1, '/state', undefined, tv)).body.messages[1].status, 'pending');
  await req(1, '/stop', {}, tv); finish('A late answer'); await new Promise(r => setTimeout(r, 30));
  state = (await req(0, '/state', undefined, phone)).body;
  assert.equal(state.messages[1].status, 'error'); assert.ok(state.messages[1].text.startsWith('Stopped.'));
  assert.equal((await req(1, '/messages', { ...body, id: 'request_second_1234567890' }, phone)).status, 429);
  await req(0, '/clear', {}, phone);
  assert.equal((await req(1, '/messages', body, phone)).status, 200); assert.equal(calls, 1);
  assert.ok(!(await dbs[1].one('SELECT data FROM accounts WHERE room=?', roomId)).data.includes('fixture-not-a-real-api-key'));
  // Cold instances must retain an active job until its persisted deadline.
  await dbs[0].transaction(async () => { const room = await dbs[0].room(roomId); room.messages = [{ id: 'expired-job', role: 'assistant', text: '', status: 'pending', deadline: Date.now() - 1 }]; await dbs[0].save(room); });
  assert.equal((await req(1, '/state', undefined, phone)).body.messages[0].status, 'error');
  await assert.rejects(dbs[0].transaction(async () => { await dbs[0].run('DELETE FROM accounts WHERE room=?', roomId); throw new Error('Rollback fixture'); }));
  assert.ok(await dbs[1].account(roomId));
  await req(1, '/disconnect', {}, phone);
  assert.equal((await req(0, '/state', undefined, tv)).status, 401);
  assert.equal(await dbs[0].account(roomId), null);
});
