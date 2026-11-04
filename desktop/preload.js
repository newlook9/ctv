'use strict';
const { contextBridge } = require('electron');
contextBridge.exposeInMainWorld('desktop', { isDesktop: true, platform: process.platform });
