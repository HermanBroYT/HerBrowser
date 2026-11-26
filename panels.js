'use strict';
// ====================================================================== боковые панели: закладки, история, загрузки, настройки, о приложении
let panelName = null;

function closePanel() { $('#panel').hidden = true; panelName = null; }
$('#panelClose').onclick = closePanel;

function openPanel(name) {
  if (panelName === name && !$('#panel').hidden) { closePanel(); return; }
  panelName = name;
  $('#panelActions').innerHTML = '';
  $('#panel').hidden = false;
  renderPanel();
}

function renderPanel() {
  const body = $('#panelBody');
  const titles = { bookmarks: 'Закладки', history: 'История', downloads: 'Загрузки', settings: 'Настройки', about: 'О приложении' };
  $('#panelTitle').textContent = titles[panelName] || '';
  $('#panelActions').innerHTML = '';
  body.innerHTML = '';
  if (panelName === 'bookmarks') renderBookmarks(body);
  else if (panelName === 'history') renderHistory(body);
  else if (panelName === 'downloads') renderDownloads(body);
  else if (panelName === 'settings') renderSettings(body);
  else if (panelName === 'about') renderAbout(body);
}

function row(av, color, t1, t2, onClick, acts) {
  const r = el('div', 'row');
  const a = el('div', 'av', av);
  a.style.background = color;
  const tx = el('div', 'tx');
  tx.append(el('div', 't1', esc(t1)), el('div', 't2', esc(t2)));
  r.append(a, tx);
  (acts || []).forEach((x) => {
    const b = el('span', 'act', x.label);
    b.title = x.title || '';
    b.onclick = (ev) => { ev.stopPropagation(); x.run(); };
    r.append(b);
  });
  r.onclick = onClick;
  return r;
}

function searchBox(ph, onInput) {
  const s = el('input', 'search');
  s.placeholder = ph;
  s.oninput = () => onInput(s.value.trim().toLowerCase());
  return s;
}

// ---------------------------------------------------------------------- закладки
function renderBookmarks(body) {
  const box = el('div');
  const draw = (f) => {
    box.innerHTML = '';
    const l = list('bookmarks').filter((b) => !f || (b.t + ' ' + b.u).toLowerCase().includes(f));
    if (!l.length) box.append(el('div', 'empty', f ? 'Ничего не найдено' : 'Закладок пока нет.<br>Нажмите ☆ в адресной строке.'));
    l.forEach((b) => box.append(row(esc(letter(b.t)), colorOf(hostOf(b.u)), b.t, b.u,
      () => { go(b.u); },
      [{ label: '↗', title: 'Открыть в новой вкладке', run: () => newTab(false, b.u) },
       { label: '📋', title: 'Копировать ссылку', run: () => { api.copy(b.u); toast('Ссылка скопирована'); } },
       { label: '✕', title: 'Удалить', run: () => { save('bookmarks', list('bookmarks').filter((x) => x !== b && x.u !== b.u)); draw(f); updateToolbar(); } }])));
  };
  body.append(searchBox('Поиск по закладкам', draw), box);
  draw('');
}

// ---------------------------------------------------------------------- история (очистка только здесь)
function dayLabel(ts) {
  const d = new Date(ts), n = new Date();
  const t0 = new Date(n.getFullYear(), n.getMonth(), n.getDate()).getTime();
  if (ts >= t0) return 'Сегодня';
  if (ts >= t0 - 86400000) return 'Вчера';
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
}

function renderHistory(body) {
  const clear = el('button', 'icon', '🗑');
  clear.title = 'Очистить историю';
  clear.onclick = () => modal({
    title: 'Очистить историю браузера?', text: 'Все посещённые страницы будут удалены.',
    buttons: [{ label: 'Очистить', run: () => { save('history', []); renderPanel(); toast('История очищена'); } }, { label: 'Отмена' }]
  });
  $('#panelActions').append(clear);
  const box = el('div');
  const draw = (f) => {
    box.innerHTML = '';
    const l = list('history').filter((h) => !f || (h.t + ' ' + h.u).toLowerCase().includes(f));
    if (!l.length) { box.append(el('div', 'empty', f ? 'Ничего не найдено' : 'История пуста')); return; }
    let last = '';
    l.slice(0, 400).forEach((h) => {
      const dl = dayLabel(h.d);
      if (dl !== last) { last = dl; box.append(el('div', 'day', esc(dl))); }
      box.append(row(esc(letter(h.t)), colorOf(hostOf(h.u)), h.t, h.u, () => go(h.u),
        [{ label: '📋', title: 'Копировать ссылку', run: () => { api.copy(h.u); toast('Ссылка скопирована'); } },
         { label: '✕', title: 'Удалить', run: () => { save('history', list('history').filter((x) => x !== h)); draw(f); } }]));
    });
  };
  body.append(searchBox('Поиск по истории', draw), box);
  draw('');
}

// ---------------------------------------------------------------------- загрузки
const fmtSize = (b) => (!b ? '' : (b < 1048576 ? Math.round(b / 1024) + ' КБ' : (b / 1048576).toFixed(1) + ' МБ'));

function openDownload(d) {
  const ex = (d.name.split('.').pop() || '').toLowerCase();
  const run = () => api.openPath(d.path).then((err) => { if (err) toast('Не удалось открыть файл'); });
  if (ex === 'html' || ex === 'htm') {
    modal({
      title: '🚫 Файл HTML может быть опасным',
      text: 'Такие файлы могут содержать вредоносные страницы, могут быть злоумышленники.',
      buttons: [{ label: 'Не открывать (рекомендую)' }, { label: 'Рискнуть для проверки (не советую)', run }]
    });
  } else run();
}

function renderDownloads(body) {
  const set = el('button', 'icon', '⚙');
  set.title = 'Настройки загрузок';
  set.onclick = () => { panelName = 'settings'; renderPanel(); };
  $('#panelActions').append(set);
  const l = list('downloads');
  if (!l.length) { body.append(el('div', 'empty', 'Загрузок пока нет')); return; }
  l.forEach((d) => {
    const done = d.state === 'completed';
    const st = done ? 'Готово — нажмите, чтобы открыть' : (d.state === 'progressing' ? 'Загрузка… ' + (d.total ? Math.round(d.received * 100 / d.total) + '%' : fmtSize(d.received)) :
      (d.state === 'cancelled' ? 'Отменено' : 'Ошибка загрузки'));
    const r = row(esc((d.name.split('.').pop() || 'file').slice(0, 4).toUpperCase()), colorOf(d.name.split('.').pop()), d.name, st,
      () => { if (done) openDownload(d); },
      [{ label: '📂', title: 'Показать в папке', run: () => api.showItem(d.path) },
       { label: '✕', title: 'Убрать из списка', run: () => { save('downloads', list('downloads').filter((x) => x !== d)); renderPanel(); } }]);
    if (d.state === 'progressing' && d.total) {
      const bar = el('div', 'bar', '<i style="width:' + Math.round(d.received * 100 / d.total) + '%"></i>');
      r.querySelector('.tx').append(bar);
    }
    body.append(r);
  });
}

function initDownloads() {
  api.on('dl:start', (e) => {
    const l = list('downloads').slice(0, 199);
    l.unshift({ uid: Date.now() + '-' + e.id, id: e.id, name: e.name, path: e.path, url: e.url, total: e.total, received: 0, state: 'progressing' });
    save('downloads', l);
    toast('Загрузка началась: ' + e.name);
    if (panelName === 'downloads') renderPanel();
  });
  api.on('dl:progress', (e) => {
    const d = list('downloads').find((x) => x.id === e.id && x.state === 'progressing');
    if (!d) return;
    d.received = e.received;
    d.total = e.total;
    if (panelName === 'downloads') renderPanel();
  });
  api.on('dl:done', (e) => {
    const l = list('downloads');
    const d = l.find((x) => x.id === e.id && x.state === 'progressing');
    if (!d) return;
    d.state = e.state;
    save('downloads', l);
    toast(e.state === 'completed' ? 'Готово: ' + d.name : 'Загрузка не завершена: ' + d.name);
    if (panelName === 'downloads') renderPanel();
  });
}

// ---------------------------------------------------------------------- настройки
function renderSettings(body) {
  const s = settings();
  const sec = (t) => body.append(el('div', 'sec', esc(t)));

  sec('Оформление');
  const chips = el('div', 'chips');
  [['light', 'Светлая'], ['dark', 'Тёмная'], ['system', 'Как в системе']].forEach(([v, n]) => {
    const c = el('button', 'chipbtn' + ((s.mode || 'light') === v && !s.bg ? ' on' : ''), n);
    c.onclick = () => { const x = Object.assign({}, settings(), { mode: v }); delete x.bg; save('settings', x); applyTheme(); renderPanel(); };
    chips.append(c);
  });
  body.append(chips);

  const colorBlock = (title, key, def, presets) => {
    const card = el('div', 'card');
    card.append(el('div', '', esc(title)));
    const rw = el('div', 'colorrow');
    const inp = el('input');
    inp.type = 'color';
    inp.value = s[key] || def;
    inp.onchange = () => { setSetting(key, inp.value); applyTheme(); renderPanel(); };
    rw.append(inp);
    presets.forEach((c) => {
      const sw = el('span', 'swatch');
      sw.style.background = c;
      sw.onclick = () => { setSetting(key, c); applyTheme(); renderPanel(); };
      rw.append(sw);
    });
    card.append(rw);
    body.append(card);
  };
  colorBlock('Основной цвет', 'accent', '#8b3fe8', ['#8b3fe8', '#e0409a', '#2787f5', '#1fa67a', '#f29d38', '#e5484d', '#0ea5b7']);
  colorBlock('Цвет фона', 'bg', '#f3f0ff', ['#f3f0ff', '#ffffff', '#fff1f5', '#eaf6ff', '#effaf2', '#15131f', '#1b2433']);
  const reset = el('button', 'btn alt', 'Сбросить цвета');
  reset.onclick = () => { const x = Object.assign({}, settings(), { mode: 'light' }); delete x.accent; delete x.bg; save('settings', x); applyTheme(); renderPanel(); };
  body.append(reset);

  sec('Загрузки');
  const card = el('div', 'card', 'Папка: ' + esc(s.dlDir || 'Загрузки\\HerBrowser (по умолчанию)'));
  body.append(card);
  const pick = el('button', 'btn', 'Выбрать папку');
  pick.onclick = async () => {
    const d = await api.chooseDir();
    if (d) { setSetting('dlDir', d); toast('Сохранено: новые загрузки будут появляться здесь'); renderPanel(); }
  };
  const std = el('button', 'btn alt', 'Стандартная папка');
  std.onclick = () => { const x = Object.assign({}, settings()); delete x.dlDir; save('settings', x); toast('Сохранено: стандартная папка'); renderPanel(); };
  body.append(pick, std);

  sec('Память');
  const cache = el('button', 'btn alt', 'Очистить кэш');
  cache.onclick = async () => { await api.clearCache(); toast('Кэш очищен'); };
  body.append(cache);
  const perms = el('button', 'btn alt', 'Сбросить запомненные разрешения сайтов');
  perms.onclick = () => toast('Разрешения сайтов хранятся в профиле: удалите файл herbrowser-data.json, чтобы сбросить всё');
  body.append(perms);
}

// ---------------------------------------------------------------------- о приложении
async function renderAbout(body) {
  const info = await api.info();
  const v = 'HerBrowser ' + info.version;
  body.innerHTML = '';
  const box = el('div', '', '');
  box.style.textAlign = 'center';
  box.style.padding = '20px 6px';
  box.innerHTML = '<div class="logo" style="margin:0 auto">H♥</div>'
    + '<div class="brand" style="font-size:28px"><span class="her">Her</span>Browser</div>'
    + '<p style="color:var(--sub);line-height:1.5">HerBrowser — это приложение-браузер, которое вы сможете настроить под себя, открывать в нём любые сайты без ограничений, сохранять важное и работать в интернете так, как удобно именно вам.</p>';
  body.append(box);
  const c = el('div', 'card', '<b>Версия ' + esc(info.version) + '</b><br><span style="color:var(--sub)">Windows 10 / 11 · Chromium ' + esc(info.chrome) + '</span>');
  body.append(c);
  const b = el('button', 'btn', 'Скопировать версию');
  b.onclick = () => { api.copy(v); toast('Скопировано'); };
  body.append(b);
}
