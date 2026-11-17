#!/usr/bin/env bash
# Headless QML smoke: installs the click layout via CMake into a temp dir, then runs the
# shell under qmlscene inside xvfb (Mesa software GL; QtWebEngine cannot start without a GL context). Needs: xvfb, libgl1-mesa-dri, qmlscene + qml-module-qtwebengine (Qt 5), cmake,
# stage.sh already run. Exit 0 only if the page booted, both shims installed and the
# bridge refused an internal target with the web/api/feed.js error shape.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cmake -S "$here" -B "$tmp/b" -DCMAKE_INSTALL_PREFIX="$tmp/click" -DCLICK_ARCH=all >/dev/null
cmake --install "$tmp/b" >/dev/null
cp "$here/tests/smoke/Smoke.qml" "$tmp/click/qml/Smoke.qml"
log="$tmp/out.log"
cd "$tmp/click"
# Container/CI only: no AppArmor-confined sandbox host, no GPU. (The click's .desktop sets
# QTWEBENGINE_DISABLE_SANDBOX=1 for its own reason; see README D-UT-4.)
xvfb-run -a -s "-screen 0 800x1280x24" env QT_QPA_PLATFORM=xcb LIBGL_ALWAYS_SOFTWARE=1 QTWEBENGINE_DISABLE_SANDBOX=1 \
    QTWEBENGINE_CHROMIUM_FLAGS="--disable-gpu --no-sandbox" \
    XDG_RUNTIME_DIR="$tmp/run" HOME="$tmp/home" \
    timeout 60 qmlscene qml/Smoke.qml >"$log" 2>&1 || true
echo "--- log tail"; tail -15 "$log"; grep -E "SMOKE-" "$log" || true
state="$(grep -m1 'SMOKE-STATE' "$log" || true)"
res="$(grep -m1 'SMOKE-RESULT' "$log" || true)"
[ -n "$state" ] && [ -n "$res" ] || { echo "smoke: no result line (see log above)"; exit 1; }
echo "$state" | grep -q '"hasApp":true'     || { echo "smoke: #app missing"; exit 1; }
echo "$state" | grep -q '"utShim":true'     || { echo "smoke: ut-shim not installed"; exit 1; }
echo "$state" | grep -q '"articleShim":true' || { echo "smoke: article-shim not installed"; exit 1; }
echo "$state" | grep -q '"protocol":"file:"' || { echo "smoke: not served from file:"; exit 1; }
echo "$res" | grep -q '400 {"error":"Refused: internal address"}' || { echo "smoke: bridge did not refuse"; exit 1; }
echo "smoke: PASS"
