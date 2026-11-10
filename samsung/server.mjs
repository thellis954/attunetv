import express from 'express';
import { Pool } from 'pg';
import { attachDatabasePool, waitUntil } from '@vercel/functions';
import { Database } from './server/database.mjs';
import { createApp } from './server/app.mjs';

// Lazy initialization keeps builds safe before the Marketplace database is connected.
let service;
const app = express();
app.use(async (req, res, next) => {
  try {
    if (!service) {
      const { DATABASE_URL, ATTUNE_ENCRYPTION_KEY, PUBLIC_ORIGIN } = process.env;
      if (!DATABASE_URL || !/^[a-f0-9]{64}$/i.test(ATTUNE_ENCRYPTION_KEY || '') || !PUBLIC_ORIGIN) {
        return res.status(503).json({ error: 'AttuneTV hosting setup is incomplete. Configure the database, encryption key and HTTPS origin.' });
      }
      const pool = new Pool({ connectionString: DATABASE_URL, max: 3, idleTimeoutMillis: 5000, connectionTimeoutMillis: 5000 });
      pool.on('error', () => {}); // Never log connection strings or database error payloads.
      attachDatabasePool(pool);
      const database = new Database({ pool, encryptionKey: Buffer.from(ATTUNE_ENCRYPTION_KEY, 'hex') });
      service = createApp({ database, origin: PUBLIC_ORIGIN, background: waitUntil, cronSecret: process.env.CRON_SECRET,
        clientAddress: req => req.headers['x-vercel-forwarded-for'] || req.socket.remoteAddress });
    }
    try { await service.store.ready; } catch { const failed = service; service = undefined; await failed.close().catch(() => {}); throw new Error('Database setup unavailable'); }
    service.app(req, res, next);
  } catch { res.status(503).json({ error: 'The conversation service is temporarily unavailable. Please try again.' }); }
});
export default app;
