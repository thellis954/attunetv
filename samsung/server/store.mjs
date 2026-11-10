import { DatabaseSync } from 'node:sqlite';
import { randomBytes, createHash, createCipheriv, createDecipheriv } from 'node:crypto';

export const token = () => randomBytes(32).toString('base64url');
export const hash = value => createHash('sha256').update(value).digest('hex');

export class Store {
  constructor(path, key) {
    if (!Buffer.isBuffer(key) || key.length !== 32) throw new Error('A 32-byte encryption key is required.');
    this.key = key;
    this.db = new DatabaseSync(path);
    this.db.exec(`PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA secure_delete=ON;
      CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, data TEXT NOT NULL, touched INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, room TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE, role TEXT NOT NULL, expires INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS pairs (id TEXT PRIMARY KEY, room TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE, expires INTEGER NOT NULL, candidate TEXT, code TEXT);
      CREATE TABLE IF NOT EXISTS accounts (room TEXT PRIMARY KEY REFERENCES rooms(id) ON DELETE CASCADE, data TEXT NOT NULL);`);
    for (const row of this.db.prepare('SELECT id FROM rooms').all()) {
      const room = this.room(row.id);
      room.messages.forEach(m => { if (m.status === 'pending') { m.status = 'error'; m.text = 'The service restarted. Ask again to retry.'; } });
      this.db.prepare('UPDATE rooms SET data=? WHERE id=?').run(this.seal(room), room.id);
    }
  }
  seal(value) {
    const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', this.key, iv);
    const encrypted = Buffer.concat([cipher.update(JSON.stringify(value)), cipher.final()]);
    return Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString('base64');
  }
  open(value) {
    const b = Buffer.from(value, 'base64'), decipher = createDecipheriv('aes-256-gcm', this.key, b.subarray(0, 12));
    decipher.setAuthTag(b.subarray(12, 28));
    return JSON.parse(Buffer.concat([decipher.update(b.subarray(28)), decipher.final()]).toString());
  }
  create() {
    const room = { id: token(), context: { title: '', episode: '', progress: '', service: '' }, revision: 0, messages: [], dailyLimit: 20, day: '', used: 0 };
    this.save(room);
    return { room, credential: this.session(room.id, 'tv') };
  }
  room(id) { const r = this.db.prepare('SELECT data FROM rooms WHERE id=?').get(id); return r ? this.open(r.data) : null; }
  save(room) { this.db.prepare('INSERT INTO rooms VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data,touched=excluded.touched').run(room.id, this.seal(room), Date.now()); }
  session(room, role) { const credential = token(); this.db.prepare('INSERT INTO sessions VALUES(?,?,?,?)').run(hash(credential), room, role, Date.now() + 30 * 86400000); return credential; }
  authenticate(credential) { return this.db.prepare('SELECT * FROM sessions WHERE id=? AND expires>?').get(hash(credential || ''), Date.now()); }
  account(room) { const r = this.db.prepare('SELECT data FROM accounts WHERE room=?').get(room); return r ? this.open(r.data) : null; }
  setAccount(room, account) { this.db.prepare('INSERT INTO accounts VALUES(?,?) ON CONFLICT(room) DO UPDATE SET data=excluded.data').run(room, this.seal(account)); }
  forgetAccount(room) { this.db.prepare('DELETE FROM accounts WHERE room=?').run(room); }
  cleanup() {
    this.db.prepare('DELETE FROM pairs WHERE expires<?').run(Date.now());
    this.db.prepare('DELETE FROM sessions WHERE expires<?').run(Date.now());
    this.db.prepare('DELETE FROM rooms WHERE touched<? OR NOT EXISTS(SELECT 1 FROM sessions WHERE sessions.room=rooms.id)').run(Date.now() - 30 * 86400000);
  }
  close() { this.db.close(); }
}
