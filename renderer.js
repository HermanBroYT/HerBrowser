'use strict';
// ====================================================================== общее
const $ = (s, r) => (r || document).querySelector(s);
const el = (tag, cls, html) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html != null) e.innerHTML = html;
  return e;
};
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const hostOf = (u) => { try { return new URL(u).hostname; } catch (e) { return ''; } };
const letter = (s) => (String(s).trim()[0] || '?').toUpperCase();
const PAL = ['#8b3fe8', '#e0409a', '#2787f5', '#1fa67a', '#f29d38', '#e5484d', '#6e56cf', '#0ea5b7'];
const colorOf = (s) => PAL[Math.abs([...String(s)].reduce((a, c) => (a * 31 + c.charCodeAt(0)) | 0, 7)) % PAL.length];

let DB = {};
const save = (k, v) => { DB[k] = v; api.storeSet(k, v); };
const list = (k) => (Array.isArray(DB[k]) ? DB[k] : []);
const settings = () => DB.settings || {};
const setSetting = (k, v) => save('settings', Object.assign({}, settings(), { [k]: v }));

const MAIN_PART = 'persist:herbrowser';
const tabs = [];
let cur = null;
let seq = 0;
const trustedHttp = new Set();

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.hidden = true; }, 2200);
}

function modal(o) {
  const box = $('#modalBox');
  box.innerHTML = '';
  if (o.title) box.append(el('h3', '', esc(o.title)));
  if (o.text) box.append(el('p', '', esc(o.text)));
  let input = null;
  if (o.input) {
    input = el('input');
    input.placeholder = o.input;
    box.append(input);
  }
  (o.buttons || [{ label: 'Закрыть' }]).forEach((b, i) => {
    const bt = el('button', 'btn' + (i === 0 && o.buttons ? '' : ' alt'), esc(b.label));
    if (o.buttons && i > 0) bt.classList.add('alt');
    bt.onclick = () => { closeModal(); if (b.run) b.run(input ? input.value : ''); };
    box.append(bt);
  });
  $('#modal').hidden = false;
  if (input) input.focus();
}
function closeModal() { $('#modal').hidden = true; }
$('#modal').addEventListener('mousedown', (e) => { if (e.target.id === 'modal') closeModal(); });

// ====================================================================== тема
const mixC = (a, b, t) => {
  const p = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
  const x = p(a), y = p(b);
  return '#' + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, '0')).join('');
};
function applyTheme() {
  const s = settings();
  let dark = s.mode === 'dark' || (s.mode === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  let bg = dark ? '#15131f' : '#f3f0ff';
  if (s.bg) {
    bg = s.bg;
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(bg.slice(i, i + 2), 16));
    dark = r * 0.299 + g * 0.587 + b * 0.114 < 128;
  }
  const accent = s.accent || (dark ? '#b9a8ff' : '#5b4fa0');
  const r = document.documentElement.style;
  r.setProperty('--bg', bg);
  r.setProperty('--bar', s.bg ? mixC(bg, '#ffffff', dark ? 0.05 : 0.4) : (dark ? '#1c1a29' : '#f8f6ff'));
  r.setProperty('--card', s.bg ? mixC(bg, '#ffffff', dark ? 0.1 : 0.65) : (dark ? '#2a2740' : '#ffffff'));
  r.setProperty('--text', dark ? '#edeaff' : '#1b1b4b');
  r.setProperty('--sub', dark ? '#a7a1cf' : '#7a74a8');
  r.setProperty('--accent', accent);
  r.setProperty('--brand', s.accent || '#8b3fe8');
  r.setProperty('--light', mixC(bg, accent, dark ? 0.35 : 0.22));
  r.setProperty('--soft', mixC(bg, accent, dark ? 0.25 : 0.13));
  r.setProperty('--danger', dark ? '#5a2530' : '#ffe0e3');
}

// ====================================================================== адреса
function toUrl(s) {
  s = String(s || '').trim();
  if (!s) return null;
  const looks = !/\s/.test(s) && (/^https?:\/\//i.test(s) || /^localhost(:\d+)?(\/|$)/i.test(s)
    || /^[^\s/]+\.[^\s/.]{2,}(:\d+)?([/?#]|$)/.test(s));
  if (!looks) return 'https://yandex.ru/search/?text=' + encodeURIComponent(s);
  return /^https?:\/\//i.test(s) ? s : 'https://' + s;
}

function translateUrl(u) {
  try {
    const p = new URL(u);
    const th = p.hostname.replace(/-/g, '--').replace(/\./g, '-') + '.translate.goog';
    const q = p.search ? p.search + '&' : '?';
    return 'https://' + th + p.pathname + q + '_x_tr_sl=auto&_x_tr_tl=ru&_x_tr_hl=ru&_x_tr_pto=wapp' + p.hash;
  } catch (e) {
    return null;
  }
}

// ====================================================================== вкладки
function newTab(priv, url) {
  const t = { id: ++seq, priv: !!priv, home: true, title: 'Новая вкладка', url: '', favicon: '', loading: false,
    wv: null, ready: false, error: null, orig: null, resume: false, zoom: 0 };
  if (t.priv) {
    t.part = 'private-' + Date.now() + '-' + t.id;
    api.registerPrivate(t.part);
  }
  tabs.push(t);
  select(t);
  if (url) go(url, t);
  return t;
}

function ensureView(t) {
  if (t.wv) return t.wv;
  const wv = document.createElement('webview');
  wv.className = 'view';
  wv.setAttribute('partition', t.priv ? t.part : MAIN_PART);
  $('#views').append(wv);
  wv.addEventListener('dom-ready', () => { t.ready = true; });
  wv.addEventListener('did-start-loading', () => { t.loading = true; t.error = null; refresh(t); });
  wv.addEventListener('did-stop-loading', () => { t.loading = false; refresh(t); });
  wv.addEventListener('did-navigate', (e) => onNav(t, e.url));
  wv.addEventListener('did-navigate-in-page', (e) => { if (e.isMainFrame) onNav(t, e.url); });
  wv.addEventListener('page-title-updated', (e) => { t.title = e.title; setHistTitle(t); renderTabs(); });
  wv.addEventListener('page-favicon-updated', (e) => { t.favicon = (e.favicons && e.favicons[0]) || ''; renderTabs(); });
  wv.addEventListener('will-navigate', (e) => {
    if (/^http:\/\//i.test(e.url) && !trustedHttp.has(hostOf(e.url))) { wv.stop(); warnInsecure(t, e.url); }
  });
  wv.addEventListener('did-fail-load', (e) => {
    if (e.isMainFrame && e.errorCode !== -3 && e.errorCode !== 0) showError(t);
  });
  wv.addEventListener('found-in-page', (e) => {
    const r = e.result;
    $('#findCount').textContent = r.matches ? r.activeMatchOrdinal + ' из ' + r.matches : 'не найдено';
  });
  t.wv = wv;
  return wv;
}

function load(t, u) {
  t.home = false;
  t.error = null;
  t.url = u;
  const wv = ensureView(t);
  if (t.ready) wv.loadURL(u).catch(() => {});
  else wv.setAttribute('src', u);
  select(t);
}

function go(input, t) {
  t = t || cur;
  const u = toUrl(input);
  if (!u) return;
  if (/^http:\/\//i.test(u) && !trustedHttp.has(hostOf(u))) { warnInsecure(t, u); return; }
  load(t, u);
}

function goHome(t) {
  t = t || cur;
  t.resume = !!t.wv && !!t.url;
  t.home = true;
  t.error = null;
  select(t);
}

let lastHist = { u: '', d: 0 };
function onNav(t, url) {
  t.url = url;
  t.home = false;
  if (!t.priv && /^https?:/i.test(url)) {
    const now = Date.now();
    if (lastHist.u !== url || now - lastHist.d > 3000) {
      const h = list('history').slice(0, 1999);
      h.unshift({ t: t.title || url, u: url, d: now });
      save('history', h);
      lastHist = { u: url, d: now };
    }
  }
  refresh(t);
  saveSession();
}

function setHistTitle(t) {
  if (t.priv) return;
  const h = list('history');
  if (h.length && h[0].u === t.url && t.title) { h[0].t = t.title; save('history', h); }
}

function closeTab(t) {
  t = t || cur;
  const i = tabs.indexOf(t);
  if (i < 0) return;
  if (t.wv) t.wv.remove();
  tabs.splice(i, 1);
  if (!tabs.length) { newTab(false); return; }
  if (cur === t) select(tabs[Math.min(i, tabs.length - 1)]);
  else renderTabs();
  saveSession();
}

function select(t) {
  cur = t;
  tabs.forEach((x) => x.wv && x.wv.classList.toggle('active', x === t && !x.home && !x.error));
  $('#home').hidden = !t.home;
  $('#error').hidden = !(t.error && !t.home);
  if (t.home) renderHome();
  $('#findbar').hidden = true;
  renderTabs();
  updateToolbar();
}

function refresh(t) { if (t === cur) { select(t); } else renderTabs(); }

function renderTabs() {
  const box = $('#tabs');
  box.innerHTML = '';
  tabs.forEach((t) => {
    const e = el('div', 'tab' + (t === cur ? ' active' : '') + (t.priv ? ' priv' : '') + (t.loading ? ' loading' : ''));
    const fav = el('span', 'fav');
    if (t.favicon && !t.home) fav.innerHTML = '<img src="' + esc(t.favicon) + '" alt="">';
    else fav.textContent = t.priv ? '🕶' : (t.home ? '♥' : letter(t.title));
    e.append(fav, el('span', 'title', esc(t.home ? 'Новая вкладка' : (t.title || t.url))));
    const x = el('span', 'x', '✕');
    x.onclick = (ev) => { ev.stopPropagation(); closeTab(t); };
    e.append(x);
    e.onclick = () => select(t);
    e.onauxclick = (ev) => { if (ev.button === 1) closeTab(t); };
    box.append(e);
  });
}

function saveSession() {
  save('session', tabs.filter((t) => !t.priv).map((t) => (t.home ? '' : t.url)).slice(0, 12));
}

// ====================================================================== панель инструментов
function isBookmarked(u) { return list('bookmarks').some((b) => b.u === u); }

function updateToolbar() {
  const t = cur;
  const url = t.home ? '' : t.url;
  if (document.activeElement !== $('#url')) $('#url').value = url;
  $('#reload').textContent = t.loading ? '✕' : '↻';
  const wv = t.wv;
  $('#back').disabled = t.home;
  $('#fwd').disabled = !(t.home ? t.resume : (wv && t.ready && wv.canGoForward()));
  const sh = $('#shield');
  if (t.home || !/^https?:/i.test(url)) sh.hidden = true;
  else {
    const bad = url.startsWith('http://');
    sh.hidden = false;
    sh.className = 'chip' + (bad ? ' bad' : '');
    sh.textContent = 'Защита сайтов  ' + (bad ? '🚫' : '✅');
  }
  $('#star').textContent = isBookmarked(url) ? '★' : '☆';
  document.title = (t.home ? 'HerBrowser' : (t.title || url) + ' — HerBrowser');
}

$('#back').onclick = () => {
  if (cur.home) return;
  if (cur.wv && cur.ready && cur.wv.canGoBack()) cur.wv.goBack(); else goHome(cur);
};
$('#fwd').onclick = () => {
  if (cur.home && cur.resume) { cur.home = false; select(cur); }
  else if (cur.wv && cur.wv.canGoForward()) cur.wv.goForward();
};
$('#reload').onclick = () => reload();
$('#homeBtn').onclick = () => goHome(cur);
$('#newTab').onclick = () => newTab(false);
$('#newPriv').onclick = () => newTab(true);
$('#errRetry').onclick = () => { if (cur.url) load(cur, cur.url); };
$('#url').addEventListener('focus', (e) => e.target.select());
$('#url').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { go(e.target.value); e.target.blur(); }
  if (e.key === 'Escape') { e.target.value = cur.home ? '' : cur.url; e.target.blur(); }
});
$('#homeSearch').addEventListener('keydown', (e) => { if (e.key === 'Enter') go(e.target.value); });
$('#star').onclick = () => toggleBookmark();
$('#copyLink').onclick = () => copyLink();
$('#translate').onclick = () => translatePage();
$('#shield').onclick = () => showShield();
$('#weather').onclick = () => go('https://yandex.ru/pogoda/');

function reload() {
  if (cur.home) return;
  if (cur.loading) { cur.wv.stop(); return; }
  if (cur.error || !cur.ready) load(cur, cur.url); else cur.wv.reload();
}

function copyLink() {
  if (cur.home || !cur.url) { toast('Сначала откройте сайт'); return; }
  api.copy(cur.url);
  toast('Ссылка скопирована');
}

function toggleBookmark() {
  if (cur.home || !cur.url) { toast('Сначала откройте страницу'); return; }
  const b = list('bookmarks');
  const i = b.findIndex((x) => x.u === cur.url);
  if (i >= 0) { b.splice(i, 1); toast('Удалено из закладок'); }
  else { b.unshift({ t: cur.title || cur.url, u: cur.url }); toast('Добавлено в закладки'); }
  save('bookmarks', b);
  updateToolbar();
}

function translatePage() {
  if (cur.home || !cur.url) { toast('Сначала откройте сайт'); return; }
  if (hostOf(cur.url).endsWith('.translate.goog')) {
    if (cur.orig) go(cur.orig); else toast('Это уже переведённая страница');
    return;
  }
  const t = translateUrl(cur.url);
  if (!t) { toast('Не удалось перевести эту страницу'); return; }
  cur.orig = cur.url;
  toast('Переводим страницу на русский…');
  load(cur, t);
}

function showShield() {
  const u = cur.url;
  const bad = u.startsWith('http://');
  modal({
    title: 'Защита сайтов',
    text: (bad ? '🚫 Небезопасный сайт\n\nСоединение не защищено (HTTP). Не вводите пароли и данные карт: могут быть злоумышленники.'
      : '✅ Безопасный сайт\n\nСоединение защищено (HTTPS).') + '\n\n' + u,
    buttons: [{ label: 'Копировать ссылку', run: () => copyLink() }, { label: 'Закрыть' }]
  });
}

function warnInsecure(t, url) {
  const host = hostOf(url);
  modal({
    title: '🚫 Небезопасный сайт',
    text: 'Сайт ' + host + ' не использует защищённое соединение (HTTP). Данные могут перехватить, могут быть злоумышленники.',
    buttons: [
      { label: 'Перейти на страницу (рекомендую)', run: () => load(t, 'https://' + url.slice(7)) },
      { label: 'Рискнуть для проверки (не советую)', run: () => { trustedHttp.add(host); load(t, url); } }
    ]
  });
}

// ====================================================================== ошибки: нет интернета / белый список
const probe = (u) => new Promise((res) => {
  const ac = new AbortController();
  const timer = setTimeout(() => { ac.abort(); res(false); }, 3000);
  fetch(u, { mode: 'no-cors', cache: 'no-store', signal: ac.signal })
    .then(() => { clearTimeout(timer); res(true); }).catch(() => { clearTimeout(timer); res(false); });
});

async function showError(t) {
  t.error = 'checking';
  t.loading = false;
  if (t === cur) { setErr('⏳', 'Проверяем соединение…'); select(t); }
  let kind = 'offline';
  if (navigator.onLine) {
    const [ru, world] = await Promise.all([probe('https://ya.ru'), probe('https://www.gstatic.com/generate_204')]);
    kind = (!ru && !world) ? 'offline' : (ru && !world ? 'whitelist' : 'other');
  }
  t.error = kind;
  if (t === cur) {
    if (kind === 'offline') setErr('📡', 'Нет подключения к интернету, включите Wi-Fi или подключите кабель');
    else if (kind === 'whitelist') setErr('🚫', 'Вы не можете зайти на иностранный сайт или наши во время белого списка, заходите на российские сайты');
    else setErr('⚠', 'Не удалось загрузить страницу. Проверьте адрес и попробуйте ещё раз');
    select(t);
  }
}
function setErr(icon, msg) { $('#errIcon').textContent = icon; $('#errMsg').textContent = msg; }

// ====================================================================== главная страница
const DEFAULT_SC = [
  { n: 'Google', u: 'https://www.google.com', c: '#4285f4', s: 'G' },
  { n: 'YouTube', u: 'https://www.youtube.com', c: '#e53935', s: '▶' },
  { n: 'ВКонтакте', u: 'https://vk.com', c: '#2787f5', s: 'VK', w: 1 },
  { n: 'Instagram', u: 'https://www.instagram.com', c: '#d62976', s: 'IG', w: 1 }
];

function renderHome() {
  $('#privLabel').hidden = !cur.priv;
  const box = $('#shortcuts');
  box.innerHTML = '';
  const add = (n, s, c, w, fn, del) => {
    const e = el('div', 'sc');
    const ic = el('div', 'ico', esc(s));
    ic.style.color = w ? '#fff' : c;
    if (w) ic.style.background = c;
    e.append(ic, el('div', 'nm', esc(n)));
    e.onclick = fn;
    if (del) e.oncontextmenu = (ev) => { ev.preventDefault(); modal({ title: 'Удалить ярлык «' + n + '»?', buttons: [{ label: 'Удалить', run: del }, { label: 'Отмена' }] }); };
    box.append(e);
  };
  DEFAULT_SC.forEach((d) => add(d.n, d.s, d.c, d.w, () => go(d.u)));
  add('Избранное', '♥', '#e0409a', 0, () => openPanel('bookmarks'));
  list('shortcuts').forEach((s, i) => add(s.n, letter(s.n), colorOf(s.n), 0, () => go(s.u), () => {
    const l = list('shortcuts'); l.splice(i, 1); save('shortcuts', l); renderHome();
  }));
  add('Добавить ссылку', '+', '#5b4fa0', 0, () => modal({
    title: 'Добавить ссылку', input: 'Адрес, например example.com',
    buttons: [{ label: 'Сохранить', run: (v) => {
      const u = toUrl(v);
      if (!u || !/^https?:/.test(u) || u.includes('yandex.ru/search')) { toast('Введите адрес сайта'); return; }
      const l = list('shortcuts'); l.push({ n: hostOf(u).replace(/^www\./, ''), u }); save('shortcuts', l); renderHome();
    } }, { label: 'Отмена' }]
  }));
  loadWeather();
}

async function loadWeather() {
  const w = DB.weather;
  const chip = $('#weather');
  if (w && Date.now() - w.ts < 20 * 60 * 1000) { chip.textContent = '🌡 ' + w.t; return; }
  if (w) chip.textContent = '🌡 ' + w.t;
  const t = await api.weather();
  if (t) { save('weather', { t, ts: Date.now() }); chip.textContent = '🌡 ' + t; }
  else if (!w) chip.textContent = '🌡 —';
}

// ====================================================================== поиск на странице, меню, горячие клавиши
function openFind() {
  if (cur.home) return;
  $('#findbar').hidden = false;
  $('#findInput').focus();
  $('#findInput').select();
}
function closeFind() {
  $('#findbar').hidden = true;
  if (cur.wv && cur.ready) cur.wv.stopFindInPage('clearSelection');
}
$('#findInput').addEventListener('input', (e) => {
  if (cur.wv && cur.ready && e.target.value) cur.wv.findInPage(e.target.value);
  else if (cur.wv && cur.ready) { cur.wv.stopFindInPage('clearSelection'); $('#findCount').textContent = ''; }
});
$('#findInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && cur.wv && e.target.value) cur.wv.findInPage(e.target.value, { forward: !e.shiftKey, findNext: true });
  if (e.key === 'Escape') closeFind();
});
$('#findNext').onclick = () => { const v = $('#findInput').value; if (v && cur.wv) cur.wv.findInPage(v, { forward: true, findNext: true }); };
$('#findPrev').onclick = () => { const v = $('#findInput').value; if (v && cur.wv) cur.wv.findInPage(v, { forward: false, findNext: true }); };
$('#findClose').onclick = closeFind;

function zoom(d) {
  if (cur.home || !cur.wv || !cur.ready) return;
  cur.zoom = d === 0 ? 0 : Math.max(-3, Math.min(5, cur.zoom + d));
  cur.wv.setZoomLevel(cur.zoom);
  toast('Масштаб: ' + Math.round(Math.pow(1.2, cur.zoom) * 100) + '%');
}

function menu() {
  const m = $('#menu');
  if (!m.hidden) { m.hidden = true; return; }
  m.innerHTML = '';
  const item = (label, key, fn) => {
    if (label === '-') { m.append(el('hr')); return; }
    const e = el('div', 'mi', '<span>' + esc(label) + '</span><small>' + esc(key || '') + '</small>');
    e.onclick = () => { m.hidden = true; fn(); };
    m.append(e);
  };
  item('Новая вкладка', 'Ctrl+T', () => newTab(false));
  item('Приватная вкладка', '', () => newTab(true));
  item('-');
  item('Закладки', 'Ctrl+D', () => openPanel('bookmarks'));
  item('История', 'Ctrl+H', () => openPanel('history'));
  item('Загрузки', 'Ctrl+J', () => openPanel('downloads'));
  item('-');
  item('Найти на странице', 'Ctrl+F', openFind);
  item('Перевести страницу', '', translatePage);
  item('Копировать ссылку', '', copyLink);
  item('Печать', 'Ctrl+P', () => { if (!cur.home && cur.wv) cur.wv.print(); });
  item('Масштаб +', 'Ctrl +', () => zoom(1));
  item('Масштаб −', 'Ctrl −', () => zoom(-1));
  item('Масштаб 100%', 'Ctrl 0', () => zoom(0));
  item('-');
  item('Настройки', '', () => openPanel('settings'));
  item('О приложении', '', () => openPanel('about'));
  m.hidden = false;
}
$('#menuBtn').onclick = (e) => { e.stopPropagation(); menu(); };
document.addEventListener('mousedown', (e) => { if (!$('#menu').hidden && !e.target.closest('#menu') && e.target.id !== 'menuBtn') $('#menu').hidden = true; });

function onShortcut(s) {
  const k = s.key;
  if (k === 'escape') { if (!$('#modal').hidden) closeModal(); else if (!$('#menu').hidden) $('#menu').hidden = true; else if (!$('#findbar').hidden) closeFind(); else if (!$('#panel').hidden) closePanel(); return; }
  if (k === 'f5' || (s.ctrl && k === 'r')) reload();
  else if (k === 'f12') { if (!cur.home && cur.wv) cur.wv.openDevTools(); }
  else if (s.ctrl && k === 't') newTab(s.shift);
  else if (s.ctrl && k === 'w') closeTab(cur);
  else if (s.ctrl && k === 'l') $('#url').focus();
  else if (s.ctrl && k === 'f') openFind();
  else if (s.ctrl && k === 'd') toggleBookmark();
  else if (s.ctrl && k === 'h') openPanel('history');
  else if (s.ctrl && k === 'j') openPanel('downloads');
  else if (s.ctrl && k === 'p') { if (!cur.home && cur.wv) cur.wv.print(); }
  else if (s.ctrl && (k === '=' || k === '+')) zoom(1);
  else if (s.ctrl && k === '-') zoom(-1);
  else if (s.ctrl && k === '0') zoom(0);
  else if (s.ctrl && k === 'tab') select(tabs[(tabs.indexOf(cur) + (s.shift ? tabs.length - 1 : 1)) % tabs.length]);
}
document.addEventListener('keydown', (e) => {
  const k = e.key.toLowerCase();
  const ctrl = e.ctrlKey || e.metaKey;
  const hot = ['t', 'w', 'l', 'f', 'r', 'd', 'h', 'j', 'tab', '=', '+', '-', '0', 'p'];
  if ((ctrl && hot.includes(k)) || k === 'f5' || k === 'f12' || k === 'escape') {
    if (k !== 'escape') e.preventDefault();
    onShortcut({ key: k, ctrl, shift: e.shiftKey });
  }
});

// ====================================================================== запуск
(async function init() {
  DB = (await api.storeAll()) || {};
  applyTheme();
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);
  api.on('open-tab', (u) => newTab(false, u));
  api.on('shortcut', onShortcut);
  initDownloads();
  const sess = list('session').filter((u, i) => i < 12);
  if (!sess.length) newTab(false);
  else sess.forEach((u) => newTab(false, u || null));
  select(tabs[0]);
  window.addEventListener('beforeunload', saveSession);
})();
