#!/bin/bash
set -euo pipefail
if [ "$#" -lt 2 ]; then
  echo "Usage: bash smoke_linux.sh APP OUTPUT_DIRECTORY [EMULATOR ARGUMENTS...]" >&2
  exit 2
fi
app=$(realpath "$1")
output=$(realpath -m "$2")
shift 2
test -x "$app"
test -n "${DISPLAY:-}"
test -n "${DBUS_SESSION_BUS_ADDRESS:-}"
mkdir -p "$output"
scratch=$(mktemp -d /tmp/nekoplayer-gui.XXXXXX)
app_pid=''
keyring_pid=''
cleanup() {
  for process in "$app_pid" "$keyring_pid"; do
    if [ -n "$process" ]; then
      kill "$process" 2>/dev/null || true
      wait "$process" 2>/dev/null || true
    fi
  done
  rm -rf "$scratch"
}
trap cleanup EXIT
export XDG_DATA_HOME="$scratch/data" XDG_CONFIG_HOME="$scratch/config" XDG_CACHE_HOME="$scratch/cache" XDG_RUNTIME_DIR="$scratch/runtime"
export FONTCONFIG_FILE="$scratch/fonts.conf" FONTCONFIG_PATH="$scratch"
export GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1
mkdir -p "$XDG_RUNTIME_DIR" "$scratch/keyring" "$scratch/no-system-fonts" "$XDG_DATA_HOME/top.nekoh2o.nekoplayer_desktop"
chmod 700 "$XDG_RUNTIME_DIR" "$scratch/keyring"
python3 - <<'PY'
import json
import os
from pathlib import Path
root = Path(os.environ['XDG_DATA_HOME']) / 'top.nekoh2o.nekoplayer_desktop'
track = {'path': '/missing/gui-smoke.mp3', 'title': '中文歌词与音乐库验证', 'artist': 'Neko'}
library = {'version': 1, 'tracks': [track], 'favorites': [], 'history': [], 'playlists': [], 'volume': 75, 'rate': 1, 'session': {'paths': [track['path']], 'index': 0, 'position': 23000}}
settings = {'version': 1, 'overlay': True, 'locked': True, 'fontSize': 30, 'opacity': 1}
(root / 'shared_preferences.json').write_text(json.dumps({'nekoplayer.flutter.library.v1': json.dumps(library), 'nekoplayer.flutter.desktop.v1': json.dumps(settings)}))
fontconfig = Path(os.environ['FONTCONFIG_FILE'])
fontconfig.write_text(f'<fontconfig><dir>{fontconfig.parent}/no-system-fonts</dir><cachedir>{fontconfig.parent}/font-cache</cachedir></fontconfig>')
PY
gnome-keyring-daemon --foreground --components=secrets --control-directory="$scratch/keyring" > "$output/keyring.log" 2>&1 &
keyring_pid=$!
for attempt in $(seq 1 20); do
  if gdbus call --session --dest org.freedesktop.secrets --object-path /org/freedesktop/secrets --method org.freedesktop.Secret.Service.ReadAlias default >/dev/null 2>&1; then break; fi
  kill -0 "$keyring_pid"
  sleep .2
done
printf '\n' | gnome-keyring-daemon --unlock --control-directory="$scratch/keyring" >/dev/null 2>&1
"$@" "$app" > "$output/app.log" 2>&1 &
app_pid=$!
destination="org.mpris.MediaPlayer2.NekoPlayer.instance$app_pid"
call() { gdbus call --session --dest "$destination" --object-path /org/mpris/MediaPlayer2 --method "$@"; }
get() { call org.freedesktop.DBus.Properties.Get org.mpris.MediaPlayer2.Player "$1"; }
for attempt in $(seq 1 90); do
  if metadata=$(get Metadata 2>/dev/null) && [[ "$metadata" == *'中文歌词与音乐库验证'* ]]; then break; fi
  kill -0 "$app_pid"
  sleep 1
done
[[ "$metadata" == *'中文歌词与音乐库验证'* ]]
[[ "$(get Position)" == *23000000* ]]
[[ "$(get CanSeek)" == *false* ]]
[[ "$(get PlaybackStatus)" == *Paused* ]]
main_window=''
lyric_window=''
for attempt in $(seq 1 90); do
  main_window=$(xdotool search --onlyvisible --name '^NekoPlayer$' | head -1 || true)
  lyric_window=$(xdotool search --onlyvisible --name '^NekoPlayer 桌面歌词$' | head -1 || true)
  if [ -n "$main_window" ] && [ -n "$lyric_window" ]; then break; fi
  kill -0 "$app_pid"
  sleep 1
done
test -n "$main_window"
test -n "$lyric_window"
xdotool windowmove "$main_window" 0 0 windowsize "$main_window" 1280 720
xdotool windowmove "$lyric_window" 0 800 windowsize "$lyric_window" 760 120
sleep 5
capture() {
  xwd -silent -id "$1" -out "$output/$2.xwd"
  ffmpeg -y -loglevel error -threads 1 -filter_threads 1 -i "$output/$2.xwd" -frames:v 1 "$output/$2.png"
  rm "$output/$2.xwd"
}
capture "$main_window" main
capture "$lyric_window" lyrics
call org.mpris.MediaPlayer2.Player.Pause
track_id="/top/nekoh2o/track/t$(printf '/missing/gui-smoke.mp3' | sha256sum | cut -d' ' -f1)"
call org.mpris.MediaPlayer2.Player.SetPosition "$track_id" 10000000
sleep .5
[[ "$(get Position)" == *23000000* ]]
call org.mpris.MediaPlayer2.Player.Stop
sleep .5
[[ "$(get Position)" == *'int64 0'* ]]
kill -0 "$app_pid"
python3 - <<'PY'
import json
import os
from pathlib import Path
root = Path(os.environ['XDG_DATA_HOME']) / 'top.nekoh2o.nekoplayer_desktop'
preferences = json.loads((root / 'shared_preferences.json').read_text())
library = json.loads(preferences['nekoplayer.flutter.library.v1'])
assert library['session']['position'] == 0
assert library['history'] == []
print('PASS: real GUI windows, MPRIS metadata/position/control, persisted stop and no phantom history. Review main.png and lyrics.png for CJK rendering without system fonts.')
PY
