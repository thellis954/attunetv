import { randomBytes, randomUUID, createHash } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, chmod, open, unlink } from 'node:fs/promises';
import { unlinkSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { createRemoteJWKSet, jwtVerify } from 'jose';

const issuer = 'https://auth.openai.com';
const resource = 'https://api.openai.com/v1';
const scopes = 'openid profile email offline_access resource.invoke chatgpt.tokens.use.direct';
const random = () => randomBytes(32).toString('base64url');
const jwks = createRemoteJWKSet(new URL(`${issuer}/.well-known/jwks.json`));
export const dataDir = process.env.ATTUNETV_DATA_DIR || join(homedir(), '.config', 'attunetv-companion');

/** A second process must not race rotating refresh tokens or overwrite account records. */
export async function lockStore() {
  await mkdir(dataDir, { recursive: true, mode: 0o700 });
  const path = join(dataDir, 'runtime.lock');
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const handle = await open(path, 'wx', 0o600);
      await handle.writeFile(String(process.pid)); await handle.close();
      process.once('exit', () => { try { unlinkSync(path); } catch {} });
      return;
    } catch (e) {
      if (e.code !== 'EEXIST') throw e;
      let running = true;
      try {
        const pid = Number(await readFile(path, 'utf8'));
        if (!Number.isInteger(pid) || pid < 1) throw new Error('Companion lock is invalid; check whether another instance is running.');
        try { process.kill(pid, 0); } catch (error) { if (error.code === 'ESRCH') running = false; else throw error; }
      } catch (error) { if (error.code === 'ENOENT') continue; throw error; }
      if (running) throw new Error('The AttuneTV companion is already running. Open http://127.0.0.1:8766.');
      await unlink(path).catch(error => { if (error.code !== 'ENOENT') throw error; });
    }
  }
  throw new Error('Could not lock companion account storage.');
}

export async function loadFile(name, fallback) {
  try { return JSON.parse(await readFile(join(dataDir, name), 'utf8')); }
  catch (e) { if (e.code === 'ENOENT') return fallback; throw new Error(`Cannot read companion ${name}.`); }
}
export async function saveFile(name, data) {
  await mkdir(dataDir, { recursive: true, mode: 0o700 });
  await chmod(dataDir, 0o700);
  const target = join(dataDir, name), temp = `${target}.${randomUUID()}.tmp`;
  await writeFile(temp, JSON.stringify(data), { mode: 0o600 });
  await rename(temp, target);
}

export class ChatGPTAuth {
  static async create() {
    const auth = new ChatGPTAuth();
    auth.store = await loadFile('accounts.json', { hostId: `urn:uuid:${randomUUID()}`, accounts: [], active: null });
    await auth.save();
    return auth;
  }
  save() { return saveFile('accounts.json', this.store); }
  get active() { return this.store.accounts.find(a => a.client_id === this.store.active); }
  publicState() {
    return { active: this.store.active, accounts: this.store.accounts.map(a => ({ id: a.client_id,
      label: `${a.email || 'ChatGPT account'} · ${a.client_id.slice(-6)}`, connected: !!a.access_token })) };
  }
  begin(redirect, accountId) {
    const account = accountId ? this.store.accounts.find(a => a.client_id === accountId) : null;
    if (accountId && !account) throw new Error('Unknown account.');
    this.pending = { state: random(), nonce: random(), verifier: random(), redirect, account, at: Date.now() };
    const p = this.pending;
    const params = new URLSearchParams({ client_id: account?.client_id || 'dynamic_agent_client',
      ext_agent_host_id: this.store.hostId, response_type: 'code', redirect_uri: redirect, scope: scopes,
      resource, state: p.state, nonce: p.nonce, code_challenge_method: 'S256',
      code_challenge: createHash('sha256').update(p.verifier).digest('base64url') });
    if (!account) params.set('agent_name_hint', 'AttuneTV');
    if (account?.id_token) params.set('id_token_hint', account.id_token);
    return `${issuer}/api/accounts/authorize?${params}`;
  }
  async tokenRequest(params) {
    const response = await fetch(`${issuer}/api/accounts/oauth/token`, { method: 'POST',
      body: new URLSearchParams(params), signal: AbortSignal.timeout(30000) });
    if (!response.ok) throw new Error('ChatGPT authorization expired or was declined. Sign in again.');
    return response.json();
  }
  async callback(params) {
    const p = this.pending;
    if (!p || params.get('state') !== p.state || Date.now() - p.at > 600000) throw new Error('Sign-in expired or state did not match.');
    this.pending = null;
    if (params.has('error')) throw new Error('ChatGPT sign-in was cancelled or declined.');
    const clientId = params.get('client_id') || p.account?.client_id;
    if (!clientId || clientId === 'dynamic_agent_client' || (p.account && clientId !== p.account.client_id)) throw new Error('ChatGPT client registration did not match.');
    const code = params.get('code');
    if (!code) throw new Error('ChatGPT did not return an authorization code.');
    const tokens = await this.tokenRequest({ grant_type: 'authorization_code', client_id: clientId,
      code, code_verifier: p.verifier, redirect_uri: p.redirect, resource });
    const { payload } = await jwtVerify(tokens.id_token, jwks, { issuer, audience: clientId, algorithms: ['RS256'], requiredClaims: ['exp', 'sub', 'nonce'] });
    if (payload.nonce !== p.nonce || (p.account && payload.sub !== p.account.subject)) throw new Error('ChatGPT account identity did not match.');
    const granted = tokens.scope?.split(' ') || [];
    if (!granted.includes('chatgpt.tokens.use.direct') || !granted.includes('resource.invoke')) throw new Error('ChatGPT plan usage was not authorized.');
    if (!tokens.access_token || !tokens.refresh_token || tokens.token_type?.toLowerCase() !== 'bearer') throw new Error('ChatGPT returned incomplete credentials.');
    const account = { ...tokens, client_id: clientId, subject: payload.sub, email: payload.email,
      expires_at: Date.now() + Number(tokens.expires_in || 3600) * 1000, scopes: granted };
    this.store.accounts = this.store.accounts.filter(a => a.client_id !== clientId).concat(account);
    this.store.active = clientId;
    await this.save();
  }
  async accessToken() {
    const account = this.active;
    if (!account?.access_token) throw new Error('Connect your ChatGPT account on the Mac first.');
    if (account.expires_at > Date.now() + 60000) return account.access_token;
    if (!this.refreshing) this.refreshing = (async () => {
      const tokens = await this.tokenRequest({ grant_type: 'refresh_token', client_id: account.client_id,
        refresh_token: account.refresh_token, resource });
      if (!tokens.access_token) throw new Error('ChatGPT could not renew this session. Sign in again.');
      if (tokens.scope && !tokens.scope.split(' ').includes('chatgpt.tokens.use.direct')) throw new Error('ChatGPT plan permission was removed.');
      Object.assign(account, tokens, { expires_at: Date.now() + Number(tokens.expires_in || 3600) * 1000 });
      await this.save();
      return account.access_token;
    })().finally(() => { this.refreshing = null; });
    return this.refreshing;
  }
  async models() {
    const response = await fetch(`${resource}/models`, { headers: { Authorization: `Bearer ${await this.accessToken()}` }, signal: AbortSignal.timeout(20000) });
    if (!response.ok) throw new Error('Cannot load models for this ChatGPT account. Check its plan access.');
    const body = await response.json();
    return (body.models || []).filter(m => m.visibility === 'list').map(m => ({ id: m.slug, name: m.display_name }));
  }
  async select(id) {
    if (!this.store.accounts.some(a => a.client_id === id)) throw new Error('Unknown account.');
    this.store.active = id; await this.save();
  }
  async signOut() {
    const account = this.active;
    if (!account) return true;
    if (this.refreshing) await this.refreshing.catch(() => {});
    let revoked = !account.refresh_token;
    for (let attempt = 0; account.refresh_token && attempt < 2; attempt++) {
      try {
        const response = await fetch(`${issuer}/api/accounts/oauth/revoke`, { method: 'POST',
          body: new URLSearchParams({ token: account.refresh_token, token_type_hint: 'refresh_token', client_id: account.client_id }), signal: AbortSignal.timeout(10000) });
        revoked = response.ok;
        if (revoked || response.status < 500) break;
      } catch { /* Clear local tokens even when revocation is unavailable. */ }
      await new Promise(r => setTimeout(r, 500));
    }
    for (const key of ['access_token', 'refresh_token', 'id_token']) delete account[key];
    await this.save();
    return revoked;
  }
}
