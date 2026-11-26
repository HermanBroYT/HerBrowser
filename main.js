'use strict';
const { app, BrowserWindow, ipcMain, session, shell, dialog, Menu, clipboard } = require('electron');
const path = require('path');
const fs = require('fs');

// Сайты видят обычный Chrome, а не «Electron»
app.userAgentFallback = app.userAgentFallback.replace(/\s(Electron|herbrowser)\/\S+/gi, '');
const UA = app.userAgentFallback;
const MAIN_PART = 'persist:herbrowser';

let win = null;
let dlCounter = 0;

if (!app.requestSingleInstanceLock()) app.quit();

// ---------------------------------------------------------------- хранилище (JSON-файл в профиле пользователя)
let data = {};
let saveTimer = null;
const storeFile = () => path.join(app.getPath('userData'), 'herbrowser-data.json');

function loadData() {
  try {
    data = JSON.parse(fs.readFileSync(storeFile(), 'utf8'));
  } catch (e) {
    data = {};
  }
}

function saveData() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try {
      fs.writeFileSync(storeFile(), JSON.stringify(data));
    } catch (e) {
      // не страшно
    }
  }, 300);
}

const send = (ch, payload) => {
  if (win && !win.isDestroyed()) win.webContents.send(ch, payload);
};

// ---------------------------------------------------------------- загрузки: нормальные имена без «.bin»
const MIME_EXT = {
  'image/jpeg': 'jpg', 'image/png': 'png', 'image/gif': 'gif', 'image/webp': 'webp', 'image/svg+xml': 'svg',
  'video/mp4': 'mp4', 'video/webm': 'webm', 'video/x-matroska': 'mkv', 'audio/mpeg': 'mp3', 'audio/ogg': 'ogg',
  'audio/wav': 'wav', 'audio/mp4': 'm4a', 'application/pdf': 'pdf', 'application/zip': 'zip',
  'application/x-zip-compressed': 'zip', 'application/vnd.android.package-archive': 'apk',
  'application/x-msdownload': 'exe', 'application/x-7z-compressed': '7z', 'application/x-rar-compressed': 'rar',
  'text/html': 'html', 'text/plain': 'txt', 'application/json': 'json'
};
const UNKNOWN_EXT = ['bin', 'bit', 'dat', 'tmp', 'php', 'asp', 'aspx', 'jsp', 'cgi', 'ashx', 'download', 'file', ''];

function fixName(name, mime) {
  name = (name || 'file').replace(/[\\/:*?"<>|]/g, '_').trim() || 'file';
  const dot = name.lastIndexOf('.');
  const ext = dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
  const base = dot > 0 ? name.slice(0, dot) : name;
  if (UNKNOWN_EXT.includes(ext)) {
    const m = MIME_EXT[(mime || '').split(';')[0].trim().toLowerCase()];
    if (m) return base + '.' + m;
    if (ext === 'bin' || ext === 'bit') return base;
  }
  return name;
}

function uniquePath(dir, name) {
  let p = path.join(dir, name);
  const dot = name.lastIndexOf('.');
  const base = dot > 0 ? name.slice(0, dot) : name;
  const ext = dot > 0 ? name.slice(dot) : '';
  let n = 1;
  while (fs.existsSync(p)) p = path.join(dir, base + ' (' + (n++) + ')' + ext);
  return p;
}

const defaultDlDir = () => path.join(app.getPath('downloads'), 'HerBrowser');

// ---------------------------------------------------------------- сессии: загрузки и разрешения сайтов
const hooked = new WeakSet();

function hookSession(ses) {
  if (!ses || hooked.has(ses)) return;
  hooked.add(ses);

  ses.on('will-download', (event, item) => {
    const dir = (data.settings && data.settings.dlDir) || defaultDlDir();
    try {
      fs.mkdirSync(dir, { recursive: true });
    } catch (e) {
      // используем папку по умолчанию ниже
    }
    const name = fixName(item.getFilename(), item.getMimeType());
    const target = uniquePath(fs.existsSync(dir) ? dir : defaultDlDir(), name);
    item.setSavePath(target);
    const id = ++dlCounter;
    send('dl:start', { id, name: path.basename(target), url: item.getURL(), path: target, total: item.getTotalBytes() });
    item.on('updated', (e, state) => {
      send('dl:progress', { id, received: item.getReceivedBytes(), total: item.getTotalBytes(), state });
    });
    item.once('done', (e, state) => {
      send('dl:done', { id, state, path: target });
    });
  });

  ses.setPermissionRequestHandler(async (wc, permission, cb, details) => {
    if (['fullscreen', 'clipboard-sanitized-write', 'pointerLock'].includes(permission)) return cb(true);
    const names = {
      media: 'камере и микрофону', geolocation: 'местоположению',
      notifications: 'уведомлениям', 'clipboard-read': 'буферу обмена'
    };
    if (!names[permission]) return cb(false);
    let host = '';
    try {
      host = new URL((details && details.requestingUrl) || wc.getURL()).host;
    } catch (e) {
      host = 'сайт';
    }
    const key = 'perm:' + host + ':' + permission;
    if (data[key] === '1') return cb(true);
    if (data[key] === '0') return cb(false);
    const r = await dialog.showMessageBox(win, {
      type: 'question',
      buttons: ['Разрешить', 'Запретить'],
      defaultId: 1,
      cancelId: 1,
      title: 'Разрешение сайта',
      message: 'Сайт ' + host + ' запрашивает доступ к ' + names[permission],
      checkboxLabel: 'Больше не спрашивать'
    });
    const ok = r.response === 0;
    if (r.checkboxChecked) {
      data[key] = ok ? '1' : '0';
      saveData();
    }
    cb(ok);
  });

  ses.setPermissionCheckHandler((wc, permission, origin) => {
    if (['fullscreen', 'clipboard-sanitized-write'].includes(permission)) return true;
    try {
      return data['perm:' + new URL(origin).host + ':' + permission] === '1';
    } catch (e) {
      return false;
    }
  });
}

// ---------------------------------------------------------------- окно
function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 760,
    minHeight: 480,
    backgroundColor: '#f3f0ff',
    title: 'HerBrowser',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webviewTag: true
    }
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'index.html'));
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  win.on('closed', () => {
    win = null;
  });
}

// Вкладки (webview): ссылки в новых окнах, контекстное меню, горячие клавиши
app.on('web-contents-created', (event, contents) => {
  if (contents.getType() !== 'webview') return;

  contents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) send('open-tab', url);
    return { action: 'deny' };
  });

  contents.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown') return;
    const k = String(input.key).toLowerCase();
    const ctrl = input.control || input.meta;
    const hot = ['t', 'w', 'l', 'f', 'r', 'd', 'h', 'j', 'tab', '=', '+', '-', '0', 'p'];
    if ((ctrl && hot.includes(k)) || k === 'f5' || k === 'f12' || k === 'escape') {
      send('shortcut', { key: k, ctrl: !!ctrl, shift: !!input.shift });
      if (k !== 'escape') e.preventDefault();
    }
  });

  contents.on('context-menu', (e, p) => {
    const nh = contents.navigationHistory;
    const canBack = nh ? nh.canGoBack() : contents.canGoBack();
    const canFwd = nh ? nh.canGoForward() : contents.canGoForward();
    const tpl = [];
    if (p.linkURL) {
      tpl.push({ label: 'Открыть в новой вкладке', click: () => send('open-tab', p.linkURL) });
      tpl.push({ label: 'Копировать ссылку', click: () => clipboard.writeText(p.linkURL) });
      tpl.push({ type: 'separator' });
    }
    if (p.mediaType === 'image') {
      tpl.push({ label: 'Копировать картинку', click: () => contents.copyImageAt(p.x, p.y) });
      tpl.push({ label: 'Сохранить картинку как…', click: () => contents.downloadURL(p.srcURL) });
      tpl.push({ type: 'separator' });
    }
    if (p.selectionText) tpl.push({ role: 'copy', label: 'Копировать' });
    if (p.isEditable) {
      tpl.push({ role: 'cut', label: 'Вырезать' });
      tpl.push({ role: 'paste', label: 'Вставить' });
    }
    if (tpl.length && tpl[tpl.length - 1].type !== 'separator') tpl.push({ type: 'separator' });
    tpl.push({ label: 'Назад', enabled: canBack, click: () => contents.goBack() });
    tpl.push({ label: 'Вперёд', enabled: canFwd, click: () => contents.goForward() });
    tpl.push({ label: 'Обновить', click: () => contents.reload() });
    tpl.push({ type: 'separator' });
    tpl.push({ label: 'Просмотреть код элемента', click: () => contents.inspectElement(p.x, p.y) });
    Menu.buildFromTemplate(tpl).popup({ window: win });
  });
});

// ---------------------------------------------------------------- погода (Яндекс.Погода, запасной источник — Open-Meteo)
async function fetchText(url, ms) {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), ms || 8000);
  try {
    const r = await fetch(url, { signal: ac.signal, headers: { 'User-Agent': UA, 'Accept-Language': 'ru-RU,ru;q=0.9' } });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return await r.text();
  } finally {
    clearTimeout(timer);
  }
}

const fmtT = (n) => (n > 0 ? '+' : (n < 0 ? '\u2212' : '')) + Math.abs(n) + '\u00B0';
const toInt = (s) => parseInt(String(s).replace(/[\u2212\u2013]/g, '-').replace('+', ''), 10);

async function weather() {
  try {
    const h = await fetchText('https://yandex.ru/pogoda/');
    const pats = [
      '"fact"\\s*:\\s*\\{[^}]{0,400}?"temp"\\s*:\\s*(-?\\d+)',
      'temp__value[^>]*>\\s*([+\\-\\u2212\\u2013]?\\d+)',
      'fact__temp[^>]*>\\s*(?:<[^>]+>\\s*)*([+\\-\\u2212\\u2013]?\\d+)'
    ];
    for (const p of pats) {
      const m = new RegExp(p, 's').exec(h);
      if (m) return fmtT(toInt(m[1]));
    }
  } catch (e) {
    // пробуем запасной источник
  }
  try {
    const g = JSON.parse(await fetchText('https://get.geojs.io/v1/ip/geo.json'));
    const w = JSON.parse(await fetchText('https://api.open-meteo.com/v1/forecast?latitude=' + g.latitude
      + '&longitude=' + g.longitude + '&current=temperature_2m'));
    if (w.current && typeof w.current.temperature_2m === 'number') return fmtT(Math.round(w.current.temperature_2m));
  } catch (e) {
    // нет данных
  }
  return null;
}

// ---------------------------------------------------------------- связь с окном
ipcMain.handle('store:all', () => data);
ipcMain.on('store:set', (e, k, v) => {
  data[k] = v;
  saveData();
});
ipcMain.handle('app:info', () => ({
  version: app.getVersion(),
  electron: process.versions.electron,
  chrome: process.versions.chrome,
  platform: process.platform
}));
ipcMain.handle('weather', () => weather());
ipcMain.handle('dl:defdir', () => defaultDlDir());
ipcMain.handle('dialog:dir', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openDirectory', 'createDirectory'] });
  return r.canceled || !r.filePaths.length ? null : r.filePaths[0];
});
ipcMain.handle('dl:open', (e, p) => shell.openPath(p));
ipcMain.on('dl:show', (e, p) => shell.showItemInFolder(p));
ipcMain.on('copy', (e, t) => clipboard.writeText(String(t)));
ipcMain.handle('cache:clear', async () => {
  await session.fromPartition(MAIN_PART).clearCache();
  return true;
});
ipcMain.on('session:private', (e, name) => hookSession(session.fromPartition(String(name))));

app.on('second-instance', (e, argv) => {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.focus();
  const u = argv.find((a) => /^https?:\/\//i.test(a));
  if (u) send('open-tab', u);
});

app.whenReady().then(() => {
  loadData();
  hookSession(session.fromPartition(MAIN_PART));
  createWindow();
  app.on('activate', () => {
    if (!win) createWindow();
  });
});

app.on('window-all-closed', () => app.quit());
