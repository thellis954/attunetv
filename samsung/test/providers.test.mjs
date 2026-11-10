import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ProviderClient, instructions } from '../server/providers.mjs';
const json = value => new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } });
for (const provider of ['openai', 'claude', 'grok', 'kimi']) {
  test(provider + ' uses its own fixed endpoint and parses a completed text answer', async () => {
    const client = new ProviderClient(async (url, opts) => {
      const body = JSON.parse(opts.body); assert.equal(opts.redirect, 'error'); assert.equal(body.model, 'test-model');
      assert.match(url, /^https:\/\/api\.(openai\.com|anthropic\.com|x\.ai|moonshot\.ai)\/v1\//);
      if (provider === 'openai') { assert.equal(body.store, false); return json({ status: 'completed', output: [{ content: [{ type: 'output_text', text: 'Answer' }] }] }); }
      if (provider === 'claude') return json({ stop_reason: 'end_turn', content: [{ type: 'text', text: 'Answer' }] });
      return json({ choices: [{ finish_reason: 'stop', message: { content: 'Answer' } }] });
    });
    assert.equal(await client.reply({ provider, key: 'fixture-key-not-real', model: 'test-model' }, { title: 'Film' }, [{ role: 'user', content: 'Question' }], new AbortController().signal), 'Answer');
  });
}
test('provider errors cannot echo API keys or private response bodies', async () => {
  const client = new ProviderClient(async () => new Response('secret-key and private prompt', { status: 401 }));
  await assert.rejects(() => client.models('openai', 'fixture-key-not-real'), e => e.message.includes('rejected') && !e.message.includes('secret-key'));
});
test('incomplete answers are not marked successful', async () => {
  const client = new ProviderClient(async () => json({ status: 'incomplete', output: [] }));
  await assert.rejects(() => client.reply({ provider: 'openai', key: 'fixture-key-not-real', model: 'test-model' }, {}, [], new AbortController().signal), /did not finish/);
});
test('arbitrary provider URLs are rejected before sending a key', async () => {
  let called = false; const client = new ProviderClient(async () => { called = true; });
  await assert.rejects(() => client.models('https://evil.example', 'fixture-key-not-real')); assert.equal(called, false);
});
test('instructions explicitly constrain screen claims and spoilers', () => {
  const prompt = instructions({ title: 'Title', episode: 'S1 E2', progress: '10 minutes' });
  assert.match(prompt, /cannot see or hear/); assert.match(prompt, /not instructions/); assert.match(prompt, /If progress is missing/);
});
