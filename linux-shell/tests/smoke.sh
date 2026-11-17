#!/usr/bin/env bash
# Headless desktop smoke. Builds the tarball, extracts it, runs the window under qmlscene in
# xvfb (Mesa software GL), and checks: the reader booted from file:, both shims installed,
# the bridge refused an internal target with the web/api/feed.js error shape, a blob: download
# was saved under $HOME/Downloads, and the data directory is ~/.local/share/nooz.
# Needs: xvfb, libgl1-mesa-dri, qmlscene + qml-module-qtwebengine (Qt 5), node + `npm ci` in web/.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
bash "$here/scripts/build-tarball.sh" "$tmp/out" >/dev/null
tar -C "$tmp" -xzf "$tmp/out/"*.tar.gz
tree="$(echo "$tmp"/nooz-linux-thin-client-*)"
cp "$here/tests/smoke/Smoke.qml" "$tree/qml/Smoke.qml"
mkdir -p "$tmp/home/Downloads" "$tmp/run"; chmod 700 "$tmp/run"
log="$tmp/out.log"
cd "$tree"
# Container/CI only: no GPU and (in containers) no usable Chromium sandbox. The shipped
# launcher sets neither of these.
xvfb-run -a -s "-screen 0 1280x900x24" env QT_QPA_PLATFORM=xcb LIBGL_ALWAYS_SOFTWARE=1 \
    QTWEBENGINE_DISABLE_SANDBOX=1 QTWEBENGINE_CHROMIUM_FLAGS="--disable-gpu --no-sandbox" \
    XDG_RUNTIME_DIR="$tmp/run" HOME="$tmp/home" XDG_DATA_HOME= XDG_CONFIG_HOME= \
    timeout 90 qmlscene qml/Smoke.qml >"$log" 2>&1 || true
echo "--- log tail"; tail -12 "$log"; grep -aE "SMOKE-" "$log" || true
state="$(grep -a -m1 'SMOKE-STATE' "$log" || true)"
res="$(grep -a -m1 'SMOKE-RESULT' "$log" || true)"
[ -n "$state" ] && [ -n "$res" ] || { echo "smoke: no result line (see log above)"; exit 1; }
echo "$state" | grep -q '"hasApp":true'      || { echo "smoke: #app missing"; exit 1; }
echo "$state" | grep -q '"utShim":true'      || { echo "smoke: ut-shim not installed"; exit 1; }
echo "$state" | grep -q '"articleShim":true' || { echo "smoke: article-shim not installed"; exit 1; }
echo "$state" | grep -q '"protocol":"file:"' || { echo "smoke: not served from file:"; exit 1; }
echo "$res" | grep -q '400 {"error":"Refused: internal address"}' || { echo "smoke: bridge did not refuse"; exit 1; }
[ -s "$tmp/home/Downloads/smoke.opml" ] || { echo "smoke: blob download not saved"; ls -la "$tmp/home/Downloads"; exit 1; }
[ -d "$tmp/home/.local/share/nooz" ] || { echo "smoke: data dir ~/.local/share/nooz missing"; find "$tmp/home" -maxdepth 4 -type d | head; exit 1; }
echo "smoke: PASS"
