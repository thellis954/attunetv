const $ = id => document.getElementById(id);
let state, polling = false, token = sessionStorage.getItem('attune-session') || '';
async function api(path, data) {
  const response = await fetch(path, { method: data === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Attune-Request': '1', 'X-Attune-Session': token },
    ...(data === undefined ? {} : { body: JSON.stringify(data) }) });
  const result = await response.json();
  if (!response.ok) { if (response.status === 401) { $('pairing').hidden = false; $('watching').hidden = true; } throw new Error(result.error); }
  return result;
}
function notice(e) { $('notice').textContent = e?.message || e || ''; }
async function action(path, data = {}) { try { notice(''); const result = await api(path, data); if (result.message) notice(result.message); await refresh(); return result; } catch (e) { notice(e); } }
function options(select, items, selected) {
  const stamp = JSON.stringify(items);
  if (select.dataset.items !== stamp) { select.replaceChildren(...items.map(item => new Option(item.name, item.id))); select.dataset.items = stamp; }
  if (selected) select.value = selected;
}
function time(ms) { if (ms === null) return ''; const s = Math.floor(ms/1000); return `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`; }
async function refresh() {
  if (polling) return; polling = true;
  try {
    state = await api('/state'); $('pairing').hidden = true; $('watching').hidden = false; $('account').hidden = !state.local;
    $('provider').textContent = state.context.provider || (state.context.connected ? 'Open a supported streaming app' : 'TV disconnected');
    $('context-label').textContent = state.context.title ? `${state.context.title}${state.context.episode ? ' · '+state.context.episode : ''} — supplied by ${state.context.source}` : 'The app has not shared a usable movie or show title.';
    $('playback').textContent = [state.context.state, time(state.context.positionMs)].filter(Boolean).join(' · ');
    $('availability').textContent = state.context.supported ? (state.connectedAccount ? 'Connected to ChatGPT.' : 'Connect ChatGPT on your Mac to ask questions.') : 'Chat is hidden until this app has been verified.';
    $('chat-controls').hidden = !state.context.supported;
    $('question').textContent = state.question; $('answer').textContent = state.answer || (state.busy ? 'Thinking…' : 'What would you like to know?');
    $('chat-error').textContent = state.error;
    $('ask').disabled = state.busy || !state.connectedAccount || !state.context.title;
    $('stop').disabled = !state.busy; $('hide').disabled = !state.overlay;
    options($('models'), state.models, state.selectedModel);
    if (state.local) {
      options($('accounts'), state.auth.accounts.map(a => ({id:a.id,name:a.label})), state.auth.active);
      $('accounts').hidden = !state.auth.accounts.length;
      $('reauth').hidden = !state.auth.active; $('signout').hidden = !state.connectedAccount;
      $('phone-url').replaceChildren(...state.pair.urls.map(url => { const p=document.createElement('p'), a=document.createElement('a'); a.href=url; a.textContent=url; p.append(a); return p; }));
      $('pair-code').textContent = state.pair.pin || 'Expired';
    }
  } catch (e) { notice(e); } finally { polling = false; }
}
$('pair-form').onsubmit = async e => { e.preventDefault(); try { const result = await api('/pair',{pin:$('pin').value}); token=result.token; sessionStorage.setItem('attune-session',token); notice(''); await refresh(); } catch(e){notice(e);} };
$('signin').onclick = async () => { const result=await action('/auth/start'); if(result?.url) location.assign(result.url); };
$('reauth').onclick = async () => { const result=await action('/auth/start',{accountId:$('accounts').value}); if(result?.url) location.assign(result.url); };
$('accounts').onchange = () => action('/auth/select',{id:$('accounts').value});
$('signout').onclick = () => action('/auth/signout');
$('new-pin').onclick = () => action('/pair/reset');
$('revoke-phones').onclick = () => action('/phones/revoke');
$('context-form').onsubmit = async e => {e.preventDefault();await action('/context',{title:$('title').value,episode:$('episode').value});$('context-editor').open=false;};
$('ask-form').onsubmit = async e => {e.preventDefault();const result=await action('/ask',{question:$('prompt').value});if(result)$('prompt').value='';};
$('show').onclick = () => action('/overlay',{visible:true}); $('hide').onclick = () => action('/overlay',{visible:false});
$('stop').onclick = () => action('/stop'); $('clear').onclick = () => action('/clear');
$('models').onchange = () => action('/model',{id:$('models').value}); $('refresh-models').onclick = () => action('/models');
void refresh(); setInterval(refresh,2000);
