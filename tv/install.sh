#!/bin/sh
# Installs the screensaver dismisser on the TV (root SSH, Homebrew Channel) and starts it; it then starts at every boot.
# Usage: TV_HOST=<tv-ip> [TV_KEY=~/.ssh/key] tv/install.sh        tv/install.sh remove   to take it off again
set -eu
TV=${TV_HOST:?set TV_HOST to the TV address}
KEYOPT=""; [ -n "${TV_KEY:-}" ] && KEYOPT="-i $TV_KEY -o IdentitiesOnly=yes"
tv() { ssh $KEYOPT -o BatchMode=yes -o ConnectTimeout=8 -o LogLevel=ERROR root@"$TV" "$@"; }
D=/media/developer/back-to-tv
LINK=/var/lib/webosbrew/init.d/80-back-to-tv-saver-dismiss
if [ "${1:-}" = remove ]; then
  tv "kill \$(cat /tmp/back-to-tv-saver-dismiss.pid 2>/dev/null) 2>/dev/null; rm -f $LINK; rm -rf $D; echo removed"
  exit 0
fi
DIR=$(cd "$(dirname "$0")" && pwd)
tv "mkdir -p $D"
tv "cat > $D/saver-dismiss.js" < "$DIR/saver-dismiss.js"
tv "cat > $D/start.sh" <<'SH'
#!/bin/sh
# started by Homebrew Channel at boot (init.d), or by install.sh: one copy at a time
P=/tmp/back-to-tv-saver-dismiss.pid
[ -f $P ] && kill "$(cat $P)" 2>/dev/null
UV_THREADPOOL_SIZE=8 nohup node /media/developer/back-to-tv/saver-dismiss.js >/dev/null 2>&1 </dev/null &
echo $! > $P
SH
tv "chmod 755 $D/start.sh && ln -sf $D/start.sh $LINK && sh $D/start.sh && sleep 2 && tail -n 1 /tmp/back-to-tv-saver-dismiss.log"
