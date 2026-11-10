import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
test('TV and phone pair, guide API setup, share context and sync after TV closes', async ({ browser }) => {
  const tvContext = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const phoneContext = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  await phoneContext.addInitScript(() => {
    window.SpeechRecognition = class {
      start() { setTimeout(() => { this.onresult({ results: [[{ transcript: 'Explain the opening scene' }]] }); this.onend(); }, 20); }
      stop() { this.onend(); }
      abort() { this.onend(); }
    };
  });
  let tv = await tvContext.newPage(); const phone = await phoneContext.newPage();
  const errors = []; tv.on('pageerror', e => errors.push(e.message)); phone.on('pageerror', e => errors.push(e.message));
  await mkdir('test-results/screens', { recursive: true });
  await tv.goto('/tv'); await expect(tv.getByRole('button', { name: 'Pair phone', exact: true })).toBeVisible();
  await tv.screenshot({ path: 'test-results/screens/tv-home.png' });
  const pairResponse = tv.waitForResponse(r => r.url().endsWith('/api/pair') && r.request().method() === 'POST');
  await tv.getByRole('button', { name: 'Pair phone', exact: true }).click();
  const pairing = await (await pairResponse).json();
  await tv.screenshot({ path: 'test-results/screens/tv-pair.png' });
  await phone.goto(pairing.link); await phone.getByRole('button', { name: 'Pair this phone', exact: true }).click();
  await expect(tv.getByRole('button', { name: /Approve phone \d+/ })).toBeVisible();
  await tv.getByRole('button', { name: /Approve phone \d+/ }).click();
  await expect(phone.getByRole('heading', { name: 'Connect your AI provider' })).toBeVisible();
  await phone.screenshot({ path: 'test-results/screens/phone-api-setup.png' });
  await phone.getByLabel('API key', { exact: true }).fill('fixture-key-not-a-real-secret');
  await phone.getByRole('button', { name: 'Check key & load models' }).click();
  await phone.getByLabel('Choose a text model').selectOption('fixture-model');
  await phone.getByLabel(/I understand API usage/).check();
  await phone.screenshot({ path: 'test-results/screens/phone-api-model.png' });
  await phone.getByRole('button', { name: 'Connect provider', exact: true }).click();
  await expect(phone.getByRole('heading', { name: 'What are you watching?' })).toBeVisible();
  await phone.getByLabel('Movie or show').fill('Severance');
  await phone.getByLabel(/Season and episode/).fill('Season 1, episode 2');
  await phone.getByLabel(/Where are you/).fill('20 minutes in. No later spoilers.');
  await phone.getByLabel(/Watching on/).selectOption('Apple TV');
  await phone.getByRole('button', { name: 'Save viewing context' }).click();
  await expect(tv.locator('#show-title')).toHaveText('Severance');
  await phone.getByRole('button', { name: 'Dictate', exact: true }).click();
  await expect(phone.getByLabel('Your question')).toHaveValue('Explain the opening scene');
  await expect(phone.locator('#transcript')).not.toContainText('Test response:');
  // Closing the TV page is stronger than merely hiding it: the phone must not
  // depend on a running TV process for inference or shared persistence.
  await tv.close();
  await phone.getByLabel('Your question').fill('Can you explain this character’s motivation?');
  await phone.getByRole('button', { name: 'Ask', exact: true }).click();
  await expect(phone.locator('#transcript')).toContainText('Test response:', { timeout: 10000 });
  await phone.screenshot({ path: 'test-results/screens/phone-chat.png', fullPage: true });
  tv = await tvContext.newPage(); await tv.goto('/tv');
  await expect(tv.locator('#transcript')).toContainText('Test response:');
  await tv.screenshot({ path: 'test-results/screens/tv-chat.png' });
  expect(await phone.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(await tv.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await tv.getByLabel('Your question').fill('Who is this character?');
  await phone.getByRole('button', { name: 'Change what’s playing' }).click();
  await phone.getByLabel(/Season and episode/).fill('Season 1, episode 3');
  await phone.getByRole('button', { name: 'Save viewing context' }).click();
  await expect(tv.getByRole('button', { name: 'Use updated context' })).toBeVisible();
  await expect(tv.getByRole('button', { name: 'Ask', exact: true })).toBeDisabled();
  await expect(tv.getByLabel('Your question')).toHaveValue('Who is this character?');
  await tv.getByRole('button', { name: 'Use updated context' }).click();
  await expect(tv.getByRole('button', { name: 'Ask', exact: true })).toBeEnabled();
  expect(errors).toEqual([]);
  await tvContext.close(); await phoneContext.close();
});

test('remote focus stays in dialogs and Back returns to the invoking control', async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 1080 }); await page.goto('/tv');
  await page.getByRole('button', { name: 'Set what’s playing' }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByLabel('Movie or show')).toBeFocused();
  await page.keyboard.press('ArrowDown'); await expect(page.getByLabel(/Season and episode/)).toBeFocused();
  for (let i = 0; i < 10; i++) await page.keyboard.press('Tab');
  expect(await page.evaluate(() => !!document.activeElement.closest('#modal'))).toBe(true);
  await page.keyboard.press('Escape'); await expect(page.locator('#modal')).toBeHidden();
  await expect(page.getByRole('button', { name: 'Set what’s playing' })).toBeFocused();
  await page.getByRole('button', { name: 'Set what’s playing' }).press('Enter');
  await page.getByLabel('Movie or show').press('Enter');
  await page.keyboard.press('Escape');
  await expect(page.locator('#modal')).toBeVisible();
  await page.keyboard.press('ArrowDown');
  await expect(page.getByLabel(/Season and episode/)).toBeFocused();
});
