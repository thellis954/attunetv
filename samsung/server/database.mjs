import { AsyncLocalStorage } from 'node:async_hooks';
import { randomBytes, createCipheriv, createDecipheriv } from 'node:crypto';
import { token, hash, Store } from './store.mjs';

const schema = `
CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, data TEXT NOT NULL, touched BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, room TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE, role TEXT NOT NULL, expires BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS pairs (id TEXT PRIMARY KEY, room TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE, expires BIGINT NOT NULL, candidate TEXT, code TEXT);
CREATE TABLE IF NOT EXISTS accounts (room TEXT PRIMARY KEY REFERENCES rooms(id) ON DELETE CASCADE, data TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS receipts (room TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE, id TEXT NOT NULL, expires BIGINT NOT NULL, PRIMARY KEY(room,id));
CREATE TABLE IF NOT EXISTS rates (id TEXT PRIMARY KEY, count INTEGER NOT NULL, expires BIGINT NOT NULL);
CREATE INDEX IF NOT EXISTS attune_session_room ON sessions(room);
CREATE INDEX IF NOT EXISTS attune_pair_room ON pairs(room);`;
function postgres(sql) {
  let index = 0;
  return sql.replace(/\?/g, () => '$' + (++index)).replace(/\b(rooms|sessions|pairs|accounts|receipts|rates)\b/g, name => 'attune_' + name);
}
export class Database {
  constructor({ dbPath, pool, encryptionKey }) {
    if (!Buffer.isBuffer(encryptionKey) || encryptionKey.length !== 32) throw new Error('A 32-byte encryption key is required.');
    this.key = encryptionKey; this.pool = pool; this.context = new AsyncLocalStorage(); this.queue = Promise.resolve();
    if (pool) this.ready = this.initialize();
    else {
      this.local = new Store(dbPath, encryptionKey); this.db = this.local.db;
      this.db.exec(schema); this.ready = Promise.resolve();
    }
  }
  async initialize() {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('SELECT pg_advisory_xact_lock(73542101)');
      await client.query(postgres(schema));
      await client.query('COMMIT');
    } catch (error) { await client.query('ROLLBACK'); throw error; }
    finally { client.release(); }
  }
  async transaction(fn) {
    await this.ready;
    if (this.context.getStore()) return fn();
    if (this.pool) {
      const client = await this.pool.connect();
      try {
        await client.query('BEGIN');
        await client.query("SET LOCAL statement_timeout = '8000ms'");
        // Short preview transactions serialize state transitions across instances.
        // Provider network requests and QR rendering always run outside this lock.
        await client.query('SELECT pg_advisory_xact_lock(73542101)');
        const result = await this.context.run(client, fn);
        await client.query('COMMIT'); return result;
      } catch (error) { await client.query('ROLLBACK'); throw error; }
      finally { client.release(); }
    }
    const previous = this.queue; let release; this.queue = new Promise(r => { release = r; });
    await previous;
    this.db.exec('BEGIN IMMEDIATE');
    try { const result = await this.context.run(this.db, fn); this.db.exec('COMMIT'); return result; }
    catch (error) { this.db.exec('ROLLBACK'); throw error; }
    finally { release(); }
  }
  async all(sql, ...params) {
    await this.ready;
    if (this.pool) return (await (this.context.getStore() || this.pool).query(postgres(sql), params)).rows;
    return this.db.prepare(sql).all(...params);
  }
  async one(sql, ...params) { return (await this.all(sql, ...params))[0]; }
  async run(sql, ...params) {
    await this.ready;
    if (this.pool) return (await (this.context.getStore() || this.pool).query(postgres(sql), params)).rowCount;
    return this.db.prepare(sql).run(...params).changes;
  }
  seal(value) {
    const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', this.key, iv);
    const bytes = Buffer.concat([cipher.update(JSON.stringify(value)), cipher.final()]);
    return Buffer.concat([iv, cipher.getAuthTag(), bytes]).toString('base64');
  }
  open(value) {
    const b = Buffer.from(value, 'base64'), decipher = createDecipheriv('aes-256-gcm', this.key, b.subarray(0, 12));
    decipher.setAuthTag(b.subarray(12, 28));
    return JSON.parse(Buffer.concat([decipher.update(b.subarray(28)), decipher.final()]).toString());
  }
  async create() {
    const room = { id: token(), context: { title: '', episode: '', progress: '', service: '' }, revision: 0, messages: [], dailyLimit: 20, day: '', used: 0 };
    await this.save(room); return { room, credential: await this.session(room.id, 'tv') };
  }
  async room(id) { const r = await this.one('SELECT data FROM rooms WHERE id=?', id); return r ? this.open(r.data) : null; }
  async save(room) { await this.run('INSERT INTO rooms VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data,touched=excluded.touched', room.id, this.seal(room), Date.now()); }
  async session(room, role) { const credential = token(); await this.run('INSERT INTO sessions VALUES(?,?,?,?)', hash(credential), room, role, Date.now() + 30 * 86400000); return credential; }
  async authenticate(credential) { return this.one('SELECT * FROM sessions WHERE id=? AND expires>?', hash(credential || ''), Date.now()); }
  async account(room) { const r = await this.one('SELECT data FROM accounts WHERE room=?', room); return r ? this.open(r.data) : null; }
  async setAccount(room, account) { await this.run('INSERT INTO accounts VALUES(?,?) ON CONFLICT(room) DO UPDATE SET data=excluded.data', room, this.seal(account)); }
  async forgetAccount(room) { await this.run('DELETE FROM accounts WHERE room=?', room); }
  async cleanup() {
    for (const table of ['pairs', 'sessions', 'receipts', 'rates']) await this.run(`DELETE FROM ${table} WHERE expires<?`, Date.now());
    await this.run('DELETE FROM rooms WHERE touched<? OR NOT EXISTS(SELECT 1 FROM sessions WHERE sessions.room=rooms.id)', Date.now() - 30 * 86400000);
  }
  async rate(id, maximum = 240) {
    const now = Date.now(), row = await this.one('SELECT * FROM rates WHERE id=?', id);
    if (!row || Number(row.expires) < now) await this.run('INSERT INTO rates VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET count=excluded.count,expires=excluded.expires', id, 1, now + 60000);
    else { if (row.count >= maximum) return false; await this.run('UPDATE rates SET count=count+1 WHERE id=?', id); }
    return true;
  }
  async close() { await this.queue; if (this.pool) await this.pool.end(); else this.local.close(); }
}
