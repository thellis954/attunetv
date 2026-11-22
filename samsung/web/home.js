/* Samsung Home: ES5 / Chromium 56. App inventory and favorites never leave the TV. */
(function () {
  'use strict';
  var apps = [], favorites = [], selected = null, previousFocus = null, editing = false, loading = false, requestId = 0, noticeTimer, launchTimer, launching = false;
  var api = window.tizen && window.tizen.application, ownId = 'AttuneTV01.Companion', stored = null;
  function el(id) { return document.getElementById(id); }
  function text(id, value) { el(id).textContent = value; }
  function visible(id, yes) { el(id).hidden = !yes; }
  function notify(value) { text('notice', value); visible('notice', true); clearTimeout(noticeTimer); noticeTimer = setTimeout(function () { visible('notice', false); }, 6500); }
  function save() { try { localStorage.setItem('attuneHomeFavorites', JSON.stringify(favorites)); } catch (e) { notify('Favorites cannot be saved on this TV right now.'); } }
  try { stored = JSON.parse(localStorage.getItem('attuneHomeFavorites')); if (!Array.isArray(stored)) stored = null; } catch (e) { stored = null; }
  try { if (api) ownId = api.getCurrentApplication().appInfo.id; } catch (e) { /* known package fallback */ }
  function clock() {
    var now = new Date(), hour = now.getHours();
    text('clock', (hour % 12 || 12) + ':' + ('0' + now.getMinutes()).slice(-2));
    text('greeting', hour < 12 ? 'Good morning.' : hour < 18 ? 'Good afternoon.' : 'Good evening.');
    text('date', now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }));
  }
  function choose(app) { selected = app; text('selected-name', app.name); text('selected-kind', favorites.indexOf(app.id) >= 0 ? 'Ready when you are' : 'On your TV'); el('options-button').disabled = false; el('grid-options').disabled = false; }
  // Exact aliases only: artwork never changes the OS app ID used to launch.
  var brands = {
    'netflix': ['netflix', '#101010'],
    'prime video': ['prime-video', '#f4f7f9'],
    'amazon prime video': ['prime-video', '#f4f7f9'],
    'apple tv': ['apple-tv', '#101010'],
    'disney+': ['disney-plus', '#f4f7f9'],
    'hulu': ['hulu', '#071d17'],
    'youtube': ['youtube', '#101010']
  };
  var artworkColors = {};
  function tint(art, icon, path) {
    // Match a solid icon's edge instead of putting every square on a blue mat.
    // Some Tizen firmware taints local canvases; neutral charcoal is the fallback.
    try {
      var color = artworkColors[path];
      if (!color) {
        var canvas = document.createElement('canvas'); canvas.width = canvas.height = 8;
        var ctx = canvas.getContext('2d'); ctx.drawImage(icon, 0, 0, 8, 8);
        var data = ctx.getImageData(0, 0, 8, 8).data, colors = [];
        [0, 7, 56, 63].forEach(function (n) { if (data[n * 4 + 3] > 240) colors.push([data[n * 4], data[n * 4 + 1], data[n * 4 + 2]]); });
        if (colors.length === 4 && colors.every(function (c) { return Math.abs(c[0] - colors[0][0]) + Math.abs(c[1] - colors[0][1]) + Math.abs(c[2] - colors[0][2]) < 70; })) color = 'rgb(' + colors[0].join(',') + ')';
        else color = '#202124';
        artworkColors[path] = color;
      }
      art.style.backgroundColor = color;
    } catch (e) { /* OS icon remains visible even without color sampling. */ }
  }
  function tile(app, inGrid) {
    var button = document.createElement('button'); button.className = 'tile'; button.setAttribute('data-app-id', app.id); button.setAttribute('aria-label', app.name);
    var art = document.createElement('span'); art.className = 'tile-art'; button.appendChild(art);
    var label = document.createElement('span'); label.className = 'fallback'; label.textContent = app.name; art.appendChild(label);
    var caption = document.createElement('span'); caption.className = 'tile-caption'; caption.textContent = app.name; caption.setAttribute('aria-hidden', 'true'); button.appendChild(caption);
    var brand = brands[app.name.toLocaleLowerCase().trim()];
    var nativePath = app.iconPath && /^(file:\/\/|\/|wgt-private\/)/.test(app.iconPath) && app.iconPath.indexOf('//') !== 0 ? app.iconPath : '';
    if (brand || nativePath) {
      var icon = document.createElement('img'); icon.alt = '';
      icon.onload = function () {
        label.hidden = true;
        if (!brand) {
          art.className = 'tile-art ' + (icon.naturalWidth / icon.naturalHeight > 1.45 ? 'native-banner' : 'native-icon');
          tint(art, icon, nativePath);
        }
      };
      icon.onerror = function () { icon.hidden = true; label.hidden = false; };
      if (brand) { art.className = 'tile-art brand-art'; art.style.backgroundColor = brand[1]; icon.src = 'assets/logos/' + brand[0] + '.svg'; }
      else icon.src = nativePath;
      art.insertBefore(icon, label);
    }
    if (inGrid && favorites.indexOf(app.id) >= 0) { var pin = document.createElement('span'); pin.className = 'pin'; pin.textContent = '★'; pin.setAttribute('aria-hidden', 'true'); art.appendChild(pin); }
    button.onfocus = function () { choose(app); if (inGrid) button.scrollIntoView({ block: 'nearest', inline: 'nearest' }); else { var dock = el('dock'); if (button.offsetLeft < dock.scrollLeft) dock.scrollLeft = button.offsetLeft; if (button.offsetLeft + button.offsetWidth > dock.scrollLeft + dock.clientWidth) dock.scrollLeft = button.offsetLeft + button.offsetWidth - dock.clientWidth + 18; } };
    button.onclick = function () { choose(app); launch(app); }; button.oncontextmenu = function (event) { event.preventDefault(); choose(app); menu(); }; return button;
  }
  function find(id) { return apps.filter(function (a) { return a.id === id; })[0]; }
  function renderDock() {
    el('dock').textContent = ''; favorites.forEach(function (id) { var app = find(id); if (app) el('dock').appendChild(tile(app, false)); });
    visible('empty', favorites.length === 0);
    if (!favorites.length) { text('empty-copy', apps.length ? 'Make this space yours. Open All apps, choose an app, then Menu → Pin to Home.' : 'No apps are available yet.'); visible('retry', apps.length === 0); }
  }
  function renderGrid() {
    var query = el('app-search').value.toLocaleLowerCase().trim(), filtered = apps.filter(function (a) { return a.name.toLocaleLowerCase().indexOf(query) >= 0; });
    el('grid').textContent = ''; filtered.forEach(function (a) { el('grid').appendChild(tile(a, true)); });
    text('apps-status', filtered.length ? filtered.length + ' installed apps' : query ? 'No apps match that search.' : 'No apps available. Return to Home to retry.');
  }
  function focusApp(id) {
    var root = el('apps-panel').hidden ? el('dock') : el('grid');
    var tiles = root.querySelectorAll('.tile');
    for (var i = 0; i < tiles.length; i++) if (tiles[i].getAttribute('data-app-id') === id) { tiles[i].focus(); return; }
    (tiles[0] || (el('apps-panel').hidden ? el('apps-button') : el('app-search'))).focus();
  }
  function loadApps() {
    if (loading) return; loading = true; var attempt = ++requestId;
    visible('empty', true); text('empty-copy', 'Finding your apps…'); visible('retry', false);
    function failed() { if (attempt !== requestId) return; loading = false; requestId++; text('empty-copy', api ? 'Samsung could not share the app list. Try again, or open an app from Samsung Home.' : 'Open the installed Samsung version to see and launch your TV apps. Ask is available in this browser.'); visible('empty', true); visible('retry', !!api); el('ask-button').focus(); }
    if (!api || typeof api.getAppsInfo !== 'function') { failed(); return; }
    var timeout = setTimeout(failed, 8000);
    try { api.getAppsInfo(function (items) {
      if (attempt !== requestId) return; clearTimeout(timeout); loading = false;
      var seen = {};
      // Some firmware marks background workers as visible. Keep user packages and
      // known built-in launchable apps, excluding services and internal tools.
      var builtins = ['org.tizen.netflix-app', 'com.samsung.tv.aria-video', 'org.tizen.apple.apple-music', 'org.tizen.browser', 'com.samsung.tv.gallery'];
      items = items.filter(function (a) { return a && (builtins.indexOf(a.id) >= 0 || (/^[A-Za-z0-9]{10}\./.test(a.id) && !/service|worker|preview|extension/i.test(a.id + ' ' + a.name))); });
      items.sort(function (a, b) { return (new Date(b.installDate).getTime() || 0) - (new Date(a.installDate).getTime() || 0); });
      apps = items.filter(function (a) { if (!a || typeof a.id !== 'string' || !a.id || a.id === ownId || a.show === false || typeof a.name !== 'string' || !a.name.trim() || seen['id:' + a.id] || seen['name:' + a.name.toLocaleLowerCase()]) return false; seen['id:' + a.id] = true; seen['name:' + a.name.toLocaleLowerCase()] = true; return true; });
      apps.sort(function (a, b) { return a.name.localeCompare(b.name); });
      if (stored === null) {
        var preferred = [];
        [/^netflix$/i, /^prime video$/i, /^apple tv$/i, /^disney\+$/i, /^hulu$/i, /^youtube$/i].forEach(function (pattern) { var app = apps.filter(function (a) { return pattern.test(a.name); })[0]; if (app) preferred.push(app); });
        favorites = preferred.concat(apps.filter(function (a) { return preferred.indexOf(a) < 0; })).slice(0, 6).map(function (a) { return a.id; }); stored = favorites.slice(); save();
      } else { favorites = stored.filter(function (id, index) { return typeof id === 'string' && !!find(id) && stored.indexOf(id) === index; }); }
      renderDock(); renderGrid();
      var last; try { last = sessionStorage.getItem('attuneHomeFocus'); } catch (e) {} focusApp(last || favorites[0]);
    }, function () { clearTimeout(timeout); failed(); }); } catch (e) { clearTimeout(timeout); failed(); }
  }
  function launch(app) {
    if (launching) return;
    if (!api || typeof api.launch !== 'function') { notify('This TV cannot open apps from AttuneTV. Use Samsung Home to open ' + app.name + '.'); return; }
    launching = true; try { sessionStorage.setItem('attuneHomeFocus', app.id); } catch (e) {}
    function failure() { clearTimeout(launchTimer); launching = false; notify('Could not open ' + app.name + '. Try it from Samsung Home, then return here.'); focusApp(app.id); }
    launchTimer = setTimeout(failure, 10000);
    try { api.launch(app.id, function () { clearTimeout(launchTimer); launching = false; }, failure); } catch (e) { failure(); }
  }
  function allApps(search) { visible('apps-panel', true); el('app-search').value = ''; renderGrid(); editing = false; if (search) el('app-search').focus(); else focusApp(selected && selected.id); }
  function closeApps() { visible('apps-panel', false); editing = false; focusApp(selected && selected.id); }
  function menu() {
    if (!selected) return; previousFocus = document.activeElement; visible('menu', true); text('menu-title', selected.name);
    var index = favorites.indexOf(selected.id); text('menu-pin', index < 0 ? 'Pin to Home' : 'Unpin from Home');
    el('menu-left').disabled = index <= 0; el('menu-right').disabled = index < 0 || index === favorites.length - 1; el('menu-open').focus();
  }
  function closeMenu() { visible('menu', false); if (previousFocus && document.body.contains(previousFocus)) previousFocus.focus(); else focusApp(selected && selected.id); }
  function updateFavorites() { stored = favorites.slice(); save(); renderDock(); renderGrid(); closeMenu(); }
  function move(delta) { var index = favorites.indexOf(selected.id), next = index + delta; if (index < 0 || next < 0 || next >= favorites.length) return; var swap = favorites[next]; favorites[next] = selected.id; favorites[index] = swap; updateFavorites(); }
  el('search-button').onclick = function () { allApps(true); }; el('apps-button').onclick = function () { allApps(false); }; el('close-apps').onclick = closeApps;
  el('app-search').oninput = renderGrid; el('options-button').onclick = menu; el('grid-options').onclick = menu; el('menu-close').onclick = closeMenu;
  el('menu-open').onclick = function () { closeMenu(); launch(selected); };
  el('menu-pin').onclick = function () { var index = favorites.indexOf(selected.id); if (index < 0) favorites.push(selected.id); else favorites.splice(index, 1); updateFavorites(); };
  el('menu-left').onclick = function () { move(-1); }; el('menu-right').onclick = function () { move(1); }; el('retry').onclick = loadApps;
  el('stay').onclick = function () { visible('exit', false); focusApp(selected && selected.id); };
  el('exit-confirm').onclick = function () { try { api.getCurrentApplication().exit(); } catch (e) { visible('exit', false); notify('Use your remote’s Home button to leave AttuneTV.'); } };
  if (!(window.ATTUNE_CONFIG || {}).packaged) el('ask-button').href = '/tv';
  document.addEventListener('keydown', function (event) {
    var key = event.keyCode, active = document.activeElement;
    if (key === 27 || key === 10009) {
      event.preventDefault();
      if (editing) { editing = false; active.blur(); active.focus(); }
      else if (!el('menu').hidden) closeMenu();
      else if (!el('exit').hidden) el('stay').click();
      else if (!el('apps-panel').hidden) closeApps();
      else { visible('exit', true); el('stay').focus(); } return;
    }
    if ((key === 10133 || key === 93) && el('menu').hidden && el('exit').hidden && active.className === 'tile') { event.preventDefault(); menu(); return; }
    if (active === el('app-search') && key === 13) { editing = true; return; }
    if (key === 40 && el('apps-panel').hidden && el('menu').hidden && el('exit').hidden && active.className === 'tile') { event.preventDefault(); allApps(false); return; }
    if (editing) return;
    var root = !el('menu').hidden ? el('menu') : !el('exit').hidden ? el('exit') : !el('apps-panel').hidden ? el('apps-panel') : el('home');
    var items = Array.prototype.filter.call(root.querySelectorAll('button,a[href],input'), function (n) { return !n.disabled && n.offsetWidth && n.offsetHeight; });
    if (key === 9 && root !== el('home')) { event.preventDefault(); var index = items.indexOf(active), next = (index + (event.shiftKey ? -1 : 1) + items.length) % items.length; items[next].focus(); return; }
    if (key < 37 || key > 40) return;
    var r = active.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2, best, score = Infinity;
    items.forEach(function (node) { if (node === active) return; var b = node.getBoundingClientRect(), dx = b.left + b.width / 2 - cx, dy = b.top + b.height / 2 - cy;
      var forward = key === 37 ? -dx : key === 39 ? dx : key === 38 ? -dy : dy, cross = key === 37 || key === 39 ? Math.abs(dy) : Math.abs(dx);
      if (forward > 3 && forward + cross * 4 < score) { best = node; score = forward + cross * 4; }
    });
    event.preventDefault(); if (best) best.focus();
  });
  try { if (window.tizen && window.tizen.tvinputdevice) window.tizen.tvinputdevice.registerKey('Menu'); } catch (e) { /* visible App options remains available */ }
  document.addEventListener('visibilitychange', function () { if (!document.hidden) { clock(); launching = false; clearTimeout(launchTimer); focusApp(selected && selected.id); } });
  clock(); setInterval(clock, 30000); loadApps();
}());
