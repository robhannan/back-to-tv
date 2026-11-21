// Runs ON THE TV (root, webOS's own Node; plain JavaScript). LG's screensaver started as an app (as Back to TV does from the
// box, because the TV's own screensaver never starts over a live HDMI picture) ignores the TV's remotes: tvpowerd didn't start
// it, so nothing dismisses it on a key. This closes it on the first key from the Magic Remote or LG's IR remote, like a normal
// screensaver. It only reads the remotes' input devices, never grabs them, so LG's input handling is unchanged.
//
// Started at boot by Homebrew Channel (/var/lib/webosbrew/init.d/80-back-to-tv-saver-dismiss, see install.sh). Log:
// /tmp/back-to-tv-saver-dismiss.log.

'use strict';
const fs = require('fs');
const execFile = require('child_process').execFile;

const REMOTES = /^LGE (M-RCU|RCU)\b/; // the Magic Remote ("LGE M-RCU - Builtin [0]") and LG's IR remote ("LGE RCU")
const SAVER = 'com.webos.app.screensaver';
const SIZE = process.arch === 'arm64' || process.arch === 'x64' ? 24 : 16; // input_event; 32-bit userspace gets the compat layout
const T = SIZE - 8;
const EV_KEY = 1;
const log = s => {
  try {
    fs.appendFileSync('/tmp/back-to-tv-saver-dismiss.log', new Date().toISOString() + ' ' + s + '\n');
  } catch (_) {}
};

function luna(uri, body, cb) {
  execFile('luna-send', ['-n', '1', '-w', '3000', uri, JSON.stringify(body)], { timeout: 5000 }, (e, out, err) => cb(String(out) + String(err)));
}

// on a key press: is the screensaver running? then close it. At most one check in flight, so a held key spawns nothing extra.
let checking = false;
function onKey() {
  if (checking) return;
  checking = true;
  luna('luna://com.webos.applicationManager/running', {}, out => {
    if (out.indexOf('"' + SAVER + '"') < 0) return (checking = false);
    luna('luna://com.webos.applicationManager/closeByAppId', { id: SAVER }, r => {
      log('key while the screensaver was up: closed it ' + r.replace(/\s+/g, ' ').slice(0, 120));
      checking = false;
    });
  });
}

function devices() {
  const out = [];
  for (const block of fs.readFileSync('/proc/bus/input/devices', 'utf8').split(/\n\n+/)) {
    const name = (/^N: Name="([^"]*)"/m.exec(block) || [])[1] || '';
    const ev = (/^H: Handlers=.*\b(event\d+)\b/m.exec(block) || [])[1];
    if (ev && REMOTES.test(name)) out.push({ dev: '/dev/input/' + ev, name });
  }
  return out;
}

function watch(d) {
  let buf = Buffer.alloc(0);
  const s = fs.createReadStream(d.dev);
  s.on('data', chunk => {
    buf = buf.length ? Buffer.concat([buf, chunk]) : chunk;
    let off = 0;
    for (; off + SIZE <= buf.length; off += SIZE) {
      if (buf.readUInt16LE(off + T) === EV_KEY && buf.readInt32LE(off + T + 4) === 1) onKey();
    }
    buf = buf.slice(off);
  });
  const again = () => setTimeout(() => watch(d), 5000);
  s.on('error', e => (log(d.dev + ': ' + e.message), again()));
  s.on('end', again);
}

const list = devices();
log('watching ' + list.map(d => d.dev + ' "' + d.name + '"').join(', '));
list.forEach(watch);
