'use strict';
const { contextBridge, ipcRenderer } = require('electron');

const events = ['open-tab', 'shortcut', 'dl:start', 'dl:progress', 'dl:done'];

contextBridge.exposeInMainWorld('api', {
  storeAll: () => ipcRenderer.invoke('store:all'),
  storeSet: (k, v) => ipcRenderer.send('store:set', k, v),
  info: () => ipcRenderer.invoke('app:info'),
  weather: () => ipcRenderer.invoke('weather'),
  chooseDir: () => ipcRenderer.invoke('dialog:dir'),
  defaultDlDir: () => ipcRenderer.invoke('dl:defdir'),
  openPath: (p) => ipcRenderer.invoke('dl:open', p),
  showItem: (p) => ipcRenderer.send('dl:show', p),
  copy: (t) => ipcRenderer.send('copy', t),
  clearCache: () => ipcRenderer.invoke('cache:clear'),
  registerPrivate: (name) => ipcRenderer.send('session:private', name),
  on: (ch, fn) => {
    if (events.includes(ch)) ipcRenderer.on(ch, (e, p) => fn(p));
  }
});
