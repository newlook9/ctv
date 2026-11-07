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
  }
});
