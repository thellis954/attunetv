import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import http from 'node:http';

test('HTTP pairing, origin protection, account isolation and phone revocation', { timeout: 20000 }, async () => {
  const dir = await mkdtemp(join(tmpdir(),'attunetv-test-'));
  const fake = join(dir,'adb');
  await writeFile(fake, `#!/usr/bin/env node
const args=process.argv.join(' ');
if(args.includes('activity activities')) console.log('mResumedActivity: ActivityRecord{abc u0 com.amazon.firebat/.Player t1}');
else if(args.includes('media_session')) console.log('ownerPid=1\\n package=com.amazon.firebat\\n active=true\\n state=PlaybackState {state=3, position=1000, buffered position=0, speed=1.0, updated=1000}\\n metadata: description=PrimeVideo, null, null');
else if(args.includes('/proc/uptime')) console.log('20.00 10.00');
else console.log('Broadcast completed: result=-1');
`, {mode:0o700});
  const child = spawn(process.execPath,['server.mjs'], { cwd: new URL('.',import.meta.url),
    env:{...process.env,ADB:fake,ATTUNETV_PORT:'18765',ATTUNETV_AUTH_PORT:'18766',ATTUNETV_DATA_DIR:dir,ATTUNETV_VERIFY_PACKAGE:'com.amazon.firebat'},stdio:['ignore','pipe','pipe'] });
  try {
    await new Promise((resolve,reject)=>{child.stdout.once('data',resolve);child.once('error',reject);child.once('exit',()=>reject(Error('Server exited early')));});
    const local='http://127.0.0.1:18766';
    const page=await fetch(local); const cookie=page.headers.get('set-cookie').split(';')[0];
    const state=await (await fetch(local+'/state',{headers:{Cookie:cookie}})).json();
    const lan=state.pair.urls[0]; assert.ok(lan,'LAN interface needed for pairing test');
    const post=(base,path,data,headers={})=>fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json','X-Attune-Request':'1',...headers},body:JSON.stringify(data)});
    assert.equal((await fetch(lan+'/state')).status,401);
    assert.equal((await post(local,'/auth/start',{}, {Cookie:cookie,Origin:'https://untrusted.example'})).status,403);
    const badHostStatus = await new Promise((resolve,reject) => {
      http.get(local+'/state',{headers:{Cookie:cookie,Host:'untrusted.example'}},res=>{res.resume();resolve(res.statusCode);}).on('error',reject);
    });
    assert.equal(badHostStatus,403);
    const paired=await (await post(lan,'/pair',{pin:state.pair.pin})).json(); assert.ok(paired.token);
    const phone={'X-Attune-Session':paired.token};
    const phoneState=await (await fetch(lan+'/state',{headers:phone})).json();
    assert.equal(phoneState.local,false); assert.equal(phoneState.auth,undefined); assert.equal(phoneState.pair,undefined);
    assert.equal((await post(lan,'/auth/start',{},phone)).status,403);
    assert.equal((await post(lan,'/pair',{pin:state.pair.pin})).status,403,'pairing code is single use');
    assert.equal((await post(local,'/phones/revoke',{}, {Cookie:cookie})).status,200);
    assert.equal((await fetch(lan+'/state',{headers:phone})).status,401);
  } finally {
    child.kill('SIGTERM'); await once(child,'exit'); await rm(dir,{recursive:true,force:true});
  }
});
