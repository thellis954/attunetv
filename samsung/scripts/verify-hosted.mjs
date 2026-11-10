// Smoke-test the operator's hosted pilot without entering keys or invoking AI.
import { chromium, expect } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
const base = process.env.ATTUNE_VERIFY_ORIGIN;
if (!base || new URL(base).protocol !== 'https:') throw new Error('Set ATTUNE_VERIFY_ORIGIN to the owned HTTPS deployment.');
const output = process.env.ATTUNE_VERIFY_OUTPUT || 'test-results/hosted';
await mkdir(output, { recursive: true });
const browser = await chromium.launch();
const tvContext = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
const phoneContext = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
let tv = await tvContext.newPage(); const phone = await phoneContext.newPage(); const errors = [];
tv.on('pageerror', e => errors.push(e.message)); phone.on('pageerror', e => errors.push(e.message));
let credential;
try {
  await tv.goto(base + '/tv');
  await expect(tv.getByRole('button', { name: 'Pair phone', exact: true })).toBeVisible();
  credential = await tv.evaluate(() => localStorage.getItem('attuneTV'));
  await tv.screenshot({ path: output + '/hosted-tv-home.png' });
  const pending = tv.waitForResponse(r => r.url().endsWith('/api/pair') && r.request().method() === 'POST');
  await tv.getByRole('button', { name: 'Pair phone', exact: true }).click();
  const pair = await (await pending).json();
  await tv.screenshot({ path: output + '/hosted-tv-pair.png' });
  await phone.goto(pair.link);
  await phone.getByRole('button', { name: 'Pair this phone', exact: true }).click();
  await expect(tv.getByRole('button', { name: /Approve phone \d+/ })).toBeVisible({ timeout: 15000 });
  await tv.getByRole('button', { name: /Approve phone \d+/ }).click();
  await expect(phone.getByRole('heading', { name: 'Connect your AI provider' })).toBeVisible({ timeout: 15000 });
  await phone.screenshot({ path: output + '/hosted-phone-api-setup.png' });
  for (const id of ['openai', 'claude', 'grok', 'kimi']) {
    await phone.getByLabel('Choose your provider').selectOption(id);
    const link = await phone.locator('#key-link').getAttribute('href');
    if (!link?.startsWith('https://')) throw new Error('Provider setup link missing: ' + id);
  }
  await phone.getByRole('button', { name: 'Close dialog', exact: true }).click();
  await phone.getByRole('button', { name: /^(Set|Change) what’s playing$/ }).click();
  await phone.getByLabel('Movie or show').fill('Hosted connection check');
  await phone.getByLabel(/Season and episode/).fill('No API key connected');
  await phone.getByRole('button', { name: 'Save viewing context' }).click();
  await expect(tv.locator('#show-title')).toHaveText('Hosted connection check', { timeout: 15000 });
  await tv.close();
  await phone.getByRole('button', { name: /^(Set|Change) what’s playing$/ }).click();
  await phone.getByLabel('Movie or show').fill('Phone sync verified');
  await phone.getByRole('button', { name: 'Save viewing context' }).click();
  tv = await tvContext.newPage(); await tv.goto(base + '/tv');
  await expect(tv.locator('#show-title')).toHaveText('Phone sync verified', { timeout: 15000 });
  await tv.screenshot({ path: output + '/hosted-tv-sync.png' });
  await phone.screenshot({ path: output + '/hosted-phone-sync.png', fullPage: true });
  expect(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(errors).toEqual([]);
  await writeFile(output + '/hosted-verification.txt', 'PASS: deployed HTTPS UI/assets, TV-confirmed QR pairing, four provider setup links, shared manual context, phone works after TV page closes, TV restores shared context, no client exceptions or phone overflow. No real API key or inference used. Screenshots are hosted browser views, not physical Samsung screenshots. Test pairing erased afterward.\n');
  console.log('Hosted pairing, guided setup and cross-screen persistence passed. No paid AI request made.');
} finally {
  // The phone context owns the test room; delete it without logging credentials.
  let erased = await phoneContext.request.post(base + '/api/disconnect', { headers: { Origin: base }, data: {} });
  if (erased.status() === 401 && credential) erased = await tvContext.request.post(base + '/api/disconnect', { headers: { Origin: base, Authorization: 'Bearer ' + credential }, data: {} });
  console.log('Test pairing cleanup:', erased.status());
  expect(erased.status()).toBe(200);
  await browser.close();
}
