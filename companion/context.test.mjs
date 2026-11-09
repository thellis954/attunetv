import test from 'node:test';
import assert from 'node:assert/strict';
import { contextFor, foreground, playback, readResponseStream, promptFor } from './context.mjs';
import { ChatGPTAuth } from './auth.mjs';

const session = (pkg, title, state = 3, active = true) => `
    Session ${pkg}
      ownerPid=123, ownerUid=10106, userId=0
      package=${pkg}
      active=${active}
      state=PlaybackState {state=${state}, position=15000, buffered position=0, speed=1.0, updated=100000, actions=1}
      metadata: size=7, description=${title}, null, null
`;
test('foreground matches resumed activity, not stale entries elsewhere in the dump', () => {
  assert.equal(foreground('Other com.netflix.ninja/.Main\n mResumedActivity: ActivityRecord{abc u0 com.amazon.firebat/.Player t87}'), 'com.amazon.firebat');
  assert.equal(foreground('no resumed app'), null);
});
test('generic PrimeVideo title stays unknown; playing position extrapolates', () => {
  assert.deepEqual(playback(session('com.amazon.firebat', 'PrimeVideo'), 'com.amazon.firebat', 105),
    {title:null,description:null,positionMs:20000,state:'playing'});
});
test('metadata belongs to foreground package and active session only', () => {
  const dump = session('com.netflix.ninja','Wrong show') + session('com.amazon.firebat','Correct show',2);
  const found = playback(dump,'com.amazon.firebat',999);
  assert.equal(found.title,'Correct show'); assert.equal(found.positionMs,15000); assert.equal(found.state,'paused');
  assert.equal(playback(session('com.amazon.firebat','Stale',3,false),'com.amazon.firebat',105).title,null);
});
test('unsupported, disconnected and unverified apps are gated', () => {
  const base = {pkg:'com.amazon.firebat',connected:true,title:null};
  assert.equal(contextFor(base,null,[]).supported,false);
  assert.equal(contextFor({...base,connected:false},null,[base.pkg]).supported,false);
  assert.equal(contextFor({...base,pkg:'com.evil.firebat'},null,['com.evil.firebat']).supported,false);
  assert.equal(contextFor(base,null,[base.pkg]).supported,true);
});
test('manual title never follows the viewer into another provider', () => {
  const manual={pkg:'com.amazon.firebat',title:'My movie',episode:'Episode 2'};
  assert.equal(contextFor({pkg:'com.netflix.ninja',connected:true},manual,[]).title,null);
  const ctx=contextFor({pkg:manual.pkg,connected:true,title:'Generic'},manual,[manual.pkg]);
  assert.equal(ctx.source,'you'); assert.equal(ctx.title,'My movie');
  assert.match(promptFor(ctx),/cannot see or hear/);
});
const stream = (...events) => new ReadableStream({ start(controller) {
  const bytes=new TextEncoder().encode(events.map(event => `data: ${JSON.stringify(event)}\r\n\r\n`).join(''));
  for(let i=0;i<bytes.length;i+=7) controller.enqueue(bytes.slice(i,i+7)); controller.close();
}});
test('SSE handles chunk boundaries and requires response.completed',async () => {
  let last;
  assert.equal(await readResponseStream(stream({type:'response.output_text.delta',delta:'Hello ✓'},{type:'response.completed'}),s=>last=s),'Hello ✓');
  assert.equal(last,'Hello ✓');
  await assert.rejects(readResponseStream(stream({type:'response.output_text.delta',delta:'Partial'}),()=>{}),/before ChatGPT completed/);
});
test('stream failure and incomplete responses are not accepted as success',async () => {
  for(const type of ['response.failed','response.incomplete','error'])
    await assert.rejects(readResponseStream(stream({type}),()=>{}),/did not finish/);
});
test('OAuth state, expiry and returning client mismatch fail before token exchange',async () => {
  const auth = new ChatGPTAuth(); auth.store={hostId:'urn:uuid:test',accounts:[],active:null};
  let calls=0; auth.tokenRequest=async()=>{calls++;throw Error('Should not exchange');};
  auth.begin('http://127.0.0.1:8766/auth/callback');
  await assert.rejects(auth.callback(new URLSearchParams({state:'bad',code:'x'})),/state did not match/);
  const original=auth.pending; original.at=0;
  await assert.rejects(auth.callback(new URLSearchParams({state:original.state,code:'x'})),/expired/);
  auth.store.accounts=[{client_id:'issued-one',subject:'one'}];
  auth.begin('http://127.0.0.1:8766/auth/callback','issued-one');
  await assert.rejects(auth.callback(new URLSearchParams({state:auth.pending.state,client_id:'issued-two',code:'x'})),/registration did not match/);
  assert.equal(calls,0);
});
