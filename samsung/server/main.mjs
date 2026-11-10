import { mkdirSync, readFileSync, writeFileSync, existsSync, chmodSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { resolve } from 'node:path';
import { createApp } from './app.mjs';

const folder = resolve(process.env.DATA_DIR || './data');
mkdirSync(folder, { recursive: true, mode: 0o700 }); chmodSync(folder, 0o700);
const keyFile = resolve(folder, 'encryption.key');
if (!existsSync(keyFile)) writeFileSync(keyFile, randomBytes(32), { mode: 0o600, flag: 'wx' });
const origin = process.env.PUBLIC_ORIGIN || 'http://127.0.0.1:8787';
const local = process.env.ATTUNE_LOCAL_DEV === '1';
const service = createApp({ dbPath: resolve(folder, 'attune.sqlite'), encryptionKey: readFileSync(keyFile), origin, allowLocal: local });
const server = service.app.listen(Number(process.env.PORT || 8787), local ? '127.0.0.1' : '0.0.0.0', () => console.log('AttuneTV companion listening. No provider credentials are logged.'));
server.requestTimeout = 30000;
function close() { server.close(() => { service.close(); process.exit(0); }); }
process.on('SIGTERM', close); process.on('SIGINT', close);
