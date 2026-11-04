'use strict';
// Puts the video engine (hls.js) next to the page so the app works without any download.
const fs = require('fs');
const path = require('path');
const src = path.join(__dirname, '..', 'node_modules', 'hls.js', 'dist', 'hls.min.js');
const dst = path.join(__dirname, '..', 'renderer', 'hls.min.js');
fs.copyFileSync(src, dst);
console.log('hls.min.js copied:', fs.statSync(dst).size, 'bytes');
