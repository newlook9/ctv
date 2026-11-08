'use strict';
const { app, BrowserWindow, Menu, shell, ipcMain, Tray, nativeImage, globalShortcut } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

// A normal browser identity: some stream servers refuse unknown programs.
app.userAgentFallback = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
// Use the graphics card for video decoding and drawing: smoother playback, less processor use.
app.commandLine.appendSwitch('ignore-gpu-blocklist');
app.commandLine.appendSwitch('enable-gpu-rasterization');
app.commandLine.appendSwitch('enable-zero-copy');
// Media keys are handled by this program itself (see below), so the browser must not also react to them.
app.commandLine.appendSwitch('disable-features', 'CalculateNativeWinOcclusion,HardwareMediaKeyHandling');
app.setAppUserModelId('com.livetv.desktop');

if (!app.requestSingleInstanceLock()) {
  app.quit();
}

let win = null;
let rec = null;
let tray = null;
let quitting = false;
let playerState = { playing: false, audio: false };
let labels = { show: 'Show Live TV', toggle: 'Play / Pause', quit: 'Quit' };

function send(channel) {
  if (win && !win.isDestroyed()) win.webContents.send(channel);
}
function showWin() {
  if (!win) { createWindow(); return; }
  win.show();
  if (win.isMinimized()) win.restore();
  win.focus();
}
function trayMenu() {
  return Menu.buildFromTemplate([
    { label: labels.show, click: showWin },
    { label: labels.toggle, click: () => send('media:toggle') },
    { type: 'separator' },
    { label: labels.quit, click: () => { quitting = true; app.quit(); } }
  ]);
}
function ensureTray() {
  if (tray) { tray.setContextMenu(trayMenu()); return; }
  const img = nativeImage.createFromPath(path.join(__dirname, 'build', 'icon.png'));
  tray = new Tray(img.resize({ width: 16, height: 16 }));
  tray.setToolTip('Live TV');
  tray.on('click', showWin);
  tray.setContextMenu(trayMenu());
}

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 780,
    minWidth: 760,
    minHeight: 520,
    backgroundColor: '#0F172A',
    title: 'Live TV',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      // The player page talks to many different stream servers. Turning the browser
      // cross-origin rule off is what lets far more channels play than in a normal browser.
      webSecurity: false,
      autoplayPolicy: 'no-user-gesture-required',
      backgroundThrottling: false
    }
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('before-input-event', (event, input) => {
    if (input.type === 'keyDown' && input.key === 'F11') {
      win.setFullScreen(!win.isFullScreen());
      event.preventDefault();
    }
  });
  // Closing the window while a radio station is playing keeps the sound going from the tray.
  win.on('close', (e) => {
    if (!quitting && playerState.playing && playerState.audio) {
      e.preventDefault();
      win.hide();
      ensureTray();
    }
  });
  win.on('closed', () => { win = null; });
}

// ---------- recording: ffmpeg saves the stream straight into a file ----------
function ffmpegPath() {
  let p = require('ffmpeg-static');
  if (p && app.isPackaged) p = p.replace('app.asar', 'app.asar.unpacked');
  return p;
}
function stampStr() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}
function safeName(n) {
  return String(n || 'rec').replace(/[^\w-]+/g, '_').slice(0, 40) || 'rec';
}
function sendDone(ok, file, err) {
  if (win && !win.isDestroyed()) {
    win.webContents.send('rec:done', { ok: ok, file: ok ? file : '', error: ok ? '' : String(err || '') });
  }
}
function launchRec(url, base, radio, ext, codecArgs) {
  const out = base + ext;
  const args = ['-hide_banner', '-loglevel', 'error', '-y', '-user_agent', app.userAgentFallback];
  if (/^https?:/i.test(url)) {
    args.push('-reconnect', '1', '-reconnect_streamed', '1', '-reconnect_delay_max', '5');
  }
  args.push('-i', url);
  codecArgs.forEach((a) => args.push(a));
  args.push(out);
  const me = { out: out, stopping: false, finished: false, proc: null };
  rec = me;
  let err = '';
  try {
    me.proc = spawn(ffmpegPath(), args, { windowsHide: true, stdio: ['pipe', 'ignore', 'pipe'] });
  } catch (e) {
    rec = null;
    sendDone(false, '', e);
    return;
  }
  me.proc.stderr.on('data', (d) => { if (err.length < 2000) err += d.toString(); });
  me.proc.on('error', (e) => {
    if (me.finished) return;
    me.finished = true;
    if (rec === me) rec = null;
    sendDone(false, '', e);
  });
  me.proc.on('close', (code) => {
    if (me.finished) return;
    me.finished = true;
    let size = 0;
    try { size = fs.existsSync(out) ? fs.statSync(out).size : 0; } catch (e) {}
    const good = size > 5000 && (code === 0 || me.stopping);
    if (!good && !radio && ext === '.mp4' && !me.stopping) {
      // Some streams cannot be put into MP4 as they are, so keep them as MKV instead.
      try { fs.unlinkSync(out); } catch (e) {}
      launchRec(url, base, radio, '.mkv', ['-c', 'copy']);
      return;
    }
    if (rec === me) rec = null;
    sendDone(good, out, err);
  });
}
function stopRec() {
  const r = rec;
  if (!r || !r.proc) return;
  r.stopping = true;
  try { r.proc.stdin.write('q'); } catch (e) {}
  setTimeout(() => { try { r.proc.kill(); } catch (e) {} }, 8000);
}
ipcMain.handle('rec:start', (event, o) => {
  if (rec) return { ok: false, error: 'busy' };
  try {
    const dir = path.join(app.getPath(o.radio ? 'music' : 'videos'), 'LiveTV');
    fs.mkdirSync(dir, { recursive: true });
    const base = path.join(dir, safeName(o.name) + '_' + stampStr());
    if (o.radio) {
      launchRec(o.url, base, true, '.mp3', ['-vn', '-c:a', 'libmp3lame', '-q:a', '4']);
    } else {
      launchRec(o.url, base, false, '.mp4', ['-c', 'copy', '-movflags', '+faststart']);
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, error: String(e) };
  }
});
ipcMain.handle('rec:stop', () => {
  if (!rec) return { ok: false };
  stopRec();
  return { ok: true };
});
ipcMain.handle('rec:open', () => {
  const dir = path.join(app.getPath('videos'), 'LiveTV');
  try { fs.mkdirSync(dir, { recursive: true }); } catch (e) {}
  return shell.openPath(dir);
});

app.on('second-instance', showWin);

ipcMain.on('player:state', (event, s) => {
  if (s) playerState = { playing: !!s.playing, audio: !!s.audio };
});
ipcMain.on('tray:labels', (event, l) => {
  if (l) {
    labels = { show: String(l.show || labels.show), toggle: String(l.toggle || labels.toggle), quit: String(l.quit || labels.quit) };
    if (tray) tray.setContextMenu(trayMenu());
  }
});
ipcMain.handle('app:version', () => app.getVersion());
ipcMain.handle('app:open', (event, url) => {
  if (typeof url === 'string' && /^https:\/\/(github\.com|api\.github\.com|objects\.githubusercontent\.com)\//.test(url)) {
    shell.openExternal(url);
    return true;
  }
  return false;
});

app.on('before-quit', () => {
  quitting = true;
  if (rec) stopRec();
});
app.on('will-quit', () => { globalShortcut.unregisterAll(); });

app.whenReady().then(() => {
  createWindow();
  [['MediaPlayPause', 'toggle'], ['MediaNextTrack', 'next'], ['MediaPreviousTrack', 'prev']].forEach((k) => {
    try { globalShortcut.register(k[0], () => send('media:' + k[1])); } catch (e) {}
  });
});

app.on('window-all-closed', () => {
  app.quit();
});
