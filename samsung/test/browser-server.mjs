// Only this test entry point supplies fixture AI. Production has no mock mode.
import { randomBytes } from 'node:crypto';
import { createApp } from '../server/app.mjs';
const service = createApp({ dbPath: ':memory:', encryptionKey: randomBytes(32), origin: 'http://127.0.0.1:8787', allowLocal: true,
  provider: { models: async () => [{ id: 'fixture-model', name: 'Test model (not live AI)' }], reply: async () => { await new Promise(r => setTimeout(r, 150)); return 'Test response: I cannot see your screen. Describe the character or moment, and I can help you discuss it without going beyond the episode you selected.'; } } });
const server = service.app.listen(8787, '127.0.0.1');
process.on('SIGTERM', () => { server.close(); service.close(); process.exit(0); });
