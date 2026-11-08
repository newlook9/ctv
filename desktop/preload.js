'use strict';
const { contextBridge, ipcRenderer, webUtils } = require('electron');
contextBridge.exposeInMainWorld('desktop', {
  isDesktop: true,
  platform: process.platform,
  recStart: (o) => ipcRenderer.invoke('rec:start', o),
  recStop: () => ipcRenderer.invoke('rec:stop'),
  openRecordings: () => ipcRenderer.invoke('rec:open'),
  onRecDone: (cb) => { ipcRenderer.on('rec:done', (e, d) => cb(d)); },
  getPath: (file) => {
    try { return webUtils.getPathForFile(file); } catch (e) { return file && file.path ? file.path : ''; }
  },
  version: () => ipcRenderer.invoke('app:version'),
  openExternal: (url) => ipcRenderer.invoke('app:open', url),
  setState: (s) => ipcRenderer.send('player:state', s),
  setLabels: (l) => ipcRenderer.send('tray:labels', l),
  onMedia: (cb) => {
    ['toggle', 'next', 'prev'].forEach((c) => ipcRenderer.on('media:' + c, () => cb(c)));
  }
});
