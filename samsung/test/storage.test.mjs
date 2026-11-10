import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { Store } from '../server/store.mjs';
test('restart retains sessions, encrypted account and context; interrupted answer becomes a visible error', t => {
  const dir = mkdtempSync(join(tmpdir(), 'attune-store-')); t.after(() => rmSync(dir, { recursive: true, force: true }));
  const path = join(dir, 'test.sqlite'), key = randomBytes(32); let store = new Store(path, key);
  const { room, credential } = store.create(); room.context.title = 'Example film'; room.messages = [{ id: 'pending', role: 'assistant', status: 'pending', text: '' }]; store.save(room);
  store.setAccount(room.id, { provider: 'openai', key: 'fixture-key-not-real', model: 'test-model' }); store.close();
  store = new Store(path, key); assert.equal(store.authenticate(credential).role, 'tv'); assert.equal(store.room(room.id).context.title, 'Example film');
  assert.equal(store.room(room.id).messages[0].status, 'error'); assert.equal(store.account(room.id).key, 'fixture-key-not-real'); store.close();
});
test('expired rooms and sessions are cleaned without making records immortal on restart', t => {
  const dir = mkdtempSync(join(tmpdir(), 'attune-retention-')); t.after(() => rmSync(dir, { recursive: true, force: true }));
  const path = join(dir, 'test.sqlite'), key = randomBytes(32); let store = new Store(path, key);
  const { room } = store.create(); store.db.prepare('UPDATE rooms SET touched=0').run(); store.close();
  store = new Store(path, key); store.cleanup(); assert.equal(store.room(room.id), null); store.close();
});
