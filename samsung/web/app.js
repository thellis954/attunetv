/* ES5 syntax deliberately retained for 2018 Samsung TVs (Chromium 56). */
(function () {
  'use strict';
  var config = window.ATTUNE_CONFIG || {}, api = config.api || '', phone = location.pathname.indexOf('/phone') === 0;
  var state = null, credential = '', ticket = '', timer, polling = false, panel = '', previousFocus, providers = [], renderKey = '', modelKey = '', editRevision = 0, pairExpiry = 0, waiting = false, mutation = false, composeId = '', editing = false;
  function el(id) { return document.getElementById(id); }
  function show(id, visible) { el(id).hidden = !visible; }
  function setText(id, value) { el(id).textContent = value; }
  function message(error) {
    var id = panel ? 'dialog-error' : 'notice'; setText(id, error.message || String(error)); show(id, true);
  }
  function request(path, data, method) {
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest(); xhr.open(method || (data ? 'POST' : 'GET'), api + '/api' + path, true); xhr.timeout = 30000;
      if (credential && !phone) xhr.setRequestHeader('Authorization', 'Bearer ' + credential);
      if (data) xhr.setRequestHeader('Content-Type', 'application/json');
      xhr.onload = function () {
        var result; try { result = JSON.parse(xhr.responseText); } catch (e) { reject(new Error('The service sent an unreadable response.')); return; }
        if (xhr.status >= 200 && xhr.status < 300) resolve(result);
        else { var error = new Error(result.error || 'The request failed. Try again.'); error.status = xhr.status; reject(error); }
      };
      xhr.onerror = xhr.ontimeout = function () { reject(new Error('Cannot reach AttuneTV. Check your connection; your question has not been cleared.')); };
      xhr.send(data ? JSON.stringify(data) : null);
    });
  }
  function focusables(root) { return Array.prototype.filter.call(root.querySelectorAll('button,input,textarea,select,a[href],[tabindex="0"]'), function (node) { return !node.disabled && node.offsetWidth && node.offsetHeight; }); }
  function openPanel(id, title) {
    previousFocus = document.activeElement; panel = id; editing = false;
    ['pair-panel', 'context-form', 'phone-pair-panel', 'account-form', 'confirm-panel'].forEach(function (name) { show(name, name === id); });
    setText('modal-title', title); show('dialog-error', false); show('modal', true);
    el('main').setAttribute('aria-hidden', 'true');
    var items = focusables(el(id)); (items[0] || el('close-modal')).focus();
  }
  function closePanel() {
    if (panel === 'account-form') { el('api-key').value = ''; modelKey = ''; show('model-step', false); }
    panel = ''; editing = false; show('modal', false); el('main').removeAttribute('aria-hidden');
    if (previousFocus && previousFocus.offsetWidth) previousFocus.focus();
  }
  function confirmAction(title, copy, action) {
    openPanel('confirm-panel', title); setText('confirm-copy', copy);
    el('confirm-action').onclick = function () { action().then(function () { closePanel(); refresh(); }).catch(message); };
  }
  function refresh() {
    if (polling || document.hidden || waiting) return Promise.resolve();
    polling = true;
    return request('/state').then(function (value) { state = value; render(); setText('connection', phone ? 'Connected to your TV' : value.paired ? 'Phone connected' : 'Ready to pair'); })
      .catch(function (error) { setText('connection', 'Disconnected'); if (error.status === 401) { state = null; show('room-actions', false); show('restart', !phone); el('ask').disabled = true; el('question').disabled = true; message(error); } else message(error); })
      .then(function () { polling = false; });
  }
  function render() {
    show('restart', false); show('room-actions', true); show('pair-phone', !phone); show('setup-ai', phone); show('clear', true); show('disconnect', true); show('leave-tv', !phone && !!window.tizen);
    var context = state.context, hasContext = !!context.title;
    show('context-summary', hasContext); show('viewing-heading', !hasContext); show('intro-copy', !hasContext);
    setText('show-title', context.title); setText('show-detail', [context.service, context.episode, context.progress].filter(Boolean).join(' · '));
    setText('edit-context', hasContext ? 'Change what’s playing' : 'Set what’s playing');
    setText('provider-label', state.account ? state.account.provider + ' / ' + state.account.model : 'Connect on your phone');
    var pending = state.messages.some(function (m) { return m.status === 'pending'; });
    el('question').disabled = !state.account || !hasContext || mutation; el('ask').disabled = !state.account || !hasContext || pending || mutation;
    show('stop', pending); show('ask', !pending);
    setText('usage', state.account ? state.used + ' of ' + state.dailyLimit + ' daily questions used' : phone ? 'Tap AI connection to get started.' : 'Pair a phone to connect your AI provider.');
    var key = JSON.stringify(state.messages);
    if (key !== renderKey) {
      renderKey = key; var transcript = el('transcript'), nearBottom = transcript.scrollHeight - transcript.scrollTop - transcript.clientHeight < 100;
      while (transcript.firstChild) transcript.removeChild(transcript.firstChild);
      if (!state.messages.length) {
        var empty = document.createElement('div'); empty.className = 'empty';
        var title = document.createElement('h3'); title.textContent = 'What caught your attention?'; empty.appendChild(title);
        var copy = document.createElement('p'); copy.textContent = state.account ? 'Ask about a character, a confusing moment, or the story behind the story.' : 'Connect an AI provider on your paired phone, then set what you’re watching.'; empty.appendChild(copy); transcript.appendChild(empty);
      }
      state.messages.forEach(function (m) {
        var item = document.createElement('div'); item.className = 'message ' + m.role + (m.status === 'error' ? ' error' : '');
        var speaker = document.createElement('span'); speaker.className = 'speaker'; speaker.textContent = m.role === 'user' ? 'You' : m.status === 'error' ? 'Could not finish' : 'AttuneTV';
        item.appendChild(speaker); item.appendChild(document.createTextNode(m.status === 'pending' ? 'Thinking about your question…' : m.text)); transcript.appendChild(item);
      });
      if (nearBottom || pending) transcript.scrollTop = transcript.scrollHeight;
    }
    if (panel === 'pair-panel') {
      var code = state.pair && state.pair.code;
      show('approve-pair', !!code); setText('approve-pair', code ? 'Approve phone ' + code : 'Approve phone');
      setText('pair-status', code ? 'Check that your phone shows ' + code + '.' : Date.now() > pairExpiry ? 'Code expired. Get a new code.' : 'Waiting for your phone…');
    }
  }
  function pair() {
    return request('/pair', {}).then(function (data) {
      openPanel('pair-panel', 'Bring your phone into the conversation');
      el('qr').src = data.qr; pairExpiry = data.expires; show('approve-pair', false); setText('pair-status', 'Waiting for your phone…'); refresh();
    }).catch(message);
  }
  function editContext() {
    if (!state) return;
    editRevision = state.revision;
    ['title', 'episode', 'progress', 'service'].forEach(function (id) { el(id).value = state.context[id] || ''; });
    openPanel('context-form', 'What are you watching?');
  }
  function providerLinks() {
    var provider = providers.filter(function (p) { return p.id === el('provider').value; })[0]; if (!provider) return;
    el('key-link').href = provider.console; el('billing-link').href = provider.billing; el('pricing-link').href = provider.pricing;
    setText('key-link', 'Open ' + provider.name + ' API keys');
    el('api-key').value = ''; modelKey = ''; show('model-step', false); setText('key-status', ''); el('billing-consent').checked = false;
  }
  function setup() {
    request('/providers').then(function (data) {
      providers = data.providers; openPanel('account-form', 'Connect your AI provider');
      if (state.account) el('provider').value = state.account.provider;
      el('daily-limit').value = state.dailyLimit; providerLinks(); show('remove-account', !!state.account);
    }).catch(message);
  }
  function waitForApproval() {
    request('/pair/wait').then(function (data) {
      if (data.approved) { waiting = false; closePanel(); refresh().then(function () { setup(); }); }
      else { setText('phone-code', data.code); setText('phone-pair-copy', 'Approve this number on your TV. That gives this phone access to its conversation and AI connection.'); }
    }).catch(function (error) { waiting = false; message(error); });
  }
  function init() {
    document.body.className = phone ? 'phone' : 'tv';
    try { credential = !phone ? localStorage.getItem('attuneTV') || '' : ''; } catch (e) { message(new Error('Storage is unavailable. Pairing will last only until this page closes.')); }
    if (phone) {
      var match = /(?:^#|&)pair=([a-zA-Z0-9_-]{43})/.exec(location.hash);
      if (match) { ticket = match[1]; history.replaceState(null, '', location.pathname); openPanel('phone-pair-panel', 'Pair with your TV'); }
      else refresh();
    } else {
      if (config.packaged && !api) { message(new Error('This preview has no service address. Ask the installer to configure the companion service.')); setText('connection', 'Service setup needed'); return; }
      if (credential) refresh().then(function () { if (state) el('edit-context').focus(); });
      else request('/tv', {}).then(function (data) { credential = data.credential; try { localStorage.setItem('attuneTV', credential); } catch (e) { /* session only */ } return refresh(); }).then(function () { el('pair-phone').focus(); }).catch(message);
    }
    timer = setInterval(function () { if (!document.hidden) { if (waiting) waitForApproval(); else if (!(ticket && !state)) refresh(); } }, 1800);
  }
  el('close-modal').onclick = closePanel;
  el('brand').onclick = function (event) { event.preventDefault(); };
  el('restart').onclick = function () { try { localStorage.removeItem('attuneTV'); } catch (e) {} location.reload(); };
  el('pair-phone').onclick = pair; el('refresh-pair').onclick = pair;
  el('approve-pair').onclick = function () { if (!state || !state.pair) return; request('/pair/approve', { code: state.pair.code }).then(function () { closePanel(); return refresh(); }).catch(message); };
  el('claim-phone').onclick = function () {
    el('claim-phone').disabled = true;
    request('/pair/claim', { ticket: ticket }).then(function (data) { waiting = true; setText('phone-code', data.code); show('claim-phone', false); waitForApproval(); }).catch(function (error) { el('claim-phone').disabled = false; message(error); });
  };
  el('edit-context').onclick = editContext;
  el('context-form').onsubmit = function (event) { event.preventDefault(); var body = { revision: editRevision }; ['title', 'episode', 'progress', 'service'].forEach(function (id) { body[id] = el(id).value; }); request('/context', body).then(function () { closePanel(); return refresh(); }).catch(function (error) { message(error); if (error.status === 409) refresh(); }); };
  el('setup-ai').onclick = setup;
  el('provider').onchange = providerLinks;
  el('api-key').oninput = function () { modelKey = ''; show('model-step', false); };
  el('check-key').onclick = function () {
    var key = el('api-key').value.trim(), provider = el('provider').value; el('check-key').disabled = true;
    setText('key-status', 'Checking access and available text models…'); show('dialog-error', false);
    request('/account/check', { provider: provider, key: key }).then(function (data) {
      if (el('api-key').value.trim() !== key || el('provider').value !== provider) return;
      var model = el('model'); while (model.options.length > 1) model.remove(1);
      data.models.forEach(function (m) { var option = document.createElement('option'); option.value = m.id; option.textContent = m.name; model.appendChild(option); });
      model.value = ''; modelKey = key; show('model-step', true); setText('key-status', 'Key accepted. Choose a model below. No answer has been generated.'); model.focus();
    }).catch(function (error) { setText('key-status', 'Connection check failed.'); message(error); }).then(function () { el('check-key').disabled = false; });
  };
  el('account-form').onsubmit = function (event) {
    event.preventDefault(); if (!modelKey || modelKey !== el('api-key').value.trim()) return message(new Error('Check your key before connecting.'));
    el('save-account').disabled = true;
    request('/account', { provider: el('provider').value, key: modelKey, model: el('model').value, dailyLimit: Number(el('daily-limit').value), billingAccepted: el('billing-consent').checked })
      .then(function () { closePanel(); return refresh(); }).then(function () { if (state && !state.context.title) editContext(); }).catch(message).then(function () { el('save-account').disabled = false; });
  };
  el('remove-account').onclick = function () { confirmAction('Remove this API key?', 'This clears the saved key and conversation on both screens. It does not revoke the key at your provider.', function () { return request('/account/remove', {}); }); };
  el('question').oninput = function () { composeId = ''; };
  el('compose').onsubmit = function (event) {
    event.preventDefault(); if (!state || mutation) return;
    if (!composeId) { var bytes = new Uint8Array(16); window.crypto.getRandomValues(bytes); composeId = Array.prototype.map.call(bytes, function (n) { return ('0' + n.toString(16)).slice(-2); }).join(''); }
    mutation = true; el('ask').disabled = true; show('notice', false);
    request('/messages', { id: composeId, question: el('question').value, revision: state.revision }).then(function () { el('question').value = ''; composeId = ''; return refresh(); })
      .catch(message).then(function () { mutation = false; if (state) render(); });
  };
  el('stop').onclick = function () { request('/stop', {}).then(refresh).catch(message); };
  el('clear').onclick = function () { confirmAction('Start a fresh conversation?', 'This erases the conversation on your phone and TV. Your viewing context and AI connection stay in place.', function () { return request('/clear', {}); }); };
  el('disconnect').onclick = function () {
    confirmAction('Disconnect and erase?', 'This removes the phone pairing, API key and conversation from this service. Revoke your key at the provider if you want to disable it everywhere.', function () { return request('/disconnect', {}).then(function () { try { localStorage.removeItem('attuneTV'); } catch (e) {} location.reload(); }); });
  };
  el('leave-tv').onclick = function () { try { window.tizen.application.getCurrentApplication().hide(); } catch (e) { message(new Error('Press Home on your remote and open your streaming app.')); } };
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) { if (waiting) waitForApproval(); else refresh(); }
  });
  document.addEventListener('keydown', function (event) {
    var key = event.keyCode, active = document.activeElement;
    if (key === 27 || key === 10009) {
      event.preventDefault();
      if (editing) { editing = false; active.blur(); active.focus(); return; }
      if (panel) closePanel(); else if (window.tizen) { try { window.tizen.application.getCurrentApplication().hide(); } catch (e) {} }
      return;
    }
    var items = focusables(panel ? el('modal') : document.body);
    if (key === 9 && panel) {
      var i = items.indexOf(active), next = event.shiftKey ? (i <= 0 ? items.length - 1 : i - 1) : (i + 1) % items.length;
      event.preventDefault(); items[next].focus(); return;
    }
    if (!phone && key === 13 && /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName)) { editing = true; return; }
    if (phone || key < 37 || key > 40 || editing) return;
    if (active === el('transcript') && (key === 38 || key === 40)) {
      var transcript = el('transcript'), delta = key === 40 ? 160 : -160;
      if ((delta > 0 && transcript.scrollTop + transcript.clientHeight < transcript.scrollHeight - 5) || (delta < 0 && transcript.scrollTop > 0)) { event.preventDefault(); transcript.scrollTop += delta; return; }
    }
    var rect = active.getBoundingClientRect(), best = null, score = Infinity, cx = rect.left + rect.width / 2, cy = rect.top + rect.height / 2;
    items.forEach(function (node) {
      if (node === active) return; var r = node.getBoundingClientRect(), dx = r.left + r.width / 2 - cx, dy = r.top + r.height / 2 - cy;
      var forward = key === 37 ? -dx : key === 39 ? dx : key === 38 ? -dy : dy;
      if (forward <= 3) return; var cross = key === 37 || key === 39 ? Math.abs(dy) : Math.abs(dx), s = forward + cross * 3;
      if (s < score) { score = s; best = node; }
    });
    if (best) { event.preventDefault(); best.focus(); }
  });
  window.addEventListener('pagehide', function () { el('api-key').value = ''; modelKey = ''; });
  init();
}());
