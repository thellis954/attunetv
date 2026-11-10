import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './test/browser', workers: 1, timeout: 45000,
  use: { baseURL: 'http://127.0.0.1:8787', screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  webServer: { command: 'node test/browser-server.mjs', url: 'http://127.0.0.1:8787/health', reuseExistingServer: false }
});
