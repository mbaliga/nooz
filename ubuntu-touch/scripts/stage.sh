#!/usr/bin/env bash
# Builds ubuntu-touch/build/payload/ (what CMakeLists.txt installs into the click):
#   www/     the web reader with its ES-module graph bundled into ONE classic script, because
#            Chromium refuses <script type="module"> from file:// (CORS on an opaque origin)
#            and a click has no web server. index.html is rewritten to load it.
#   shims/   nooz-shims.js = ut-shim.js + Readability.js + apple/Nooz/Resources/ios-shim.js
#            (the one article-extraction source), concatenated; plus Readability's licence
#   assets/  icon.png, derived from the reader's own 512px icon
# Needs: node 20, `npm ci` already run in web/ (Readability.js), network for npx esbuild.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(cd "$here/.." && pwd)"
out="$here/build/payload"
ESBUILD_VERSION="0.24.0"   # pinned; fetched by npx, not vendored

[ -f "$repo/web/node_modules/@mozilla/readability/Readability.js" ] || {
  echo "stage: run 'npm ci' in web/ first (Readability.js missing)" >&2; exit 1; }

rm -rf "$out"
mkdir -p "$out/www" "$out/shims" "$out/assets"

# 1. static reader, minus server helpers, tests, deploy config and the unbundled js/
( cd "$repo/web" && tar \
    --exclude=./node_modules --exclude=./tests --exclude=./api --exclude=./js \
    --exclude=./package.json --exclude=./package-lock.json --exclude=./vercel.json \
    -cf - . ) | ( cd "$out/www" && tar -xf - )

# 2. one classic script. iife + es2020: no top-level await or import() in js/ (checked here:
#    esbuild fails loudly if that ever changes).
( cd "$repo/web" && npx --yes "esbuild@$ESBUILD_VERSION" js/app.js \
    --bundle --format=iife --target=es2020 --legal-comments=none --outfile="$out/www/app.bundle.js" )

# 3. index.html: the single module script tag becomes a deferred classic one.
python3 - "$out/www/index.html" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
tag = '<script type="module" src="js/app.js"></script>'
if s.count(tag) != 1:
    sys.exit("stage: expected exactly one module script tag in web/index.html")
open(p, "w", encoding="utf-8").write(s.replace(tag, '<script defer src="app.bundle.js"></script>'))
PY

# 4. shims + licence
#    ONE injected script, in a fixed order: QtWebEngine does not promise the order of several
#    user scripts (the first smoke run saw article-shim run before Readability existed).
#    ut-shim wraps fetch; Readability must be a global before article-shim installs on top.
{
  cat "$here/shim/ut-shim.js"; printf '\n;\n'
  cat "$repo/web/node_modules/@mozilla/readability/Readability.js"; printf '\n;\n'
  cat "$repo/apple/Nooz/Resources/ios-shim.js"; printf '\n'
} > "$out/shims/nooz-shims.js"
cp "$repo/web/node_modules/@mozilla/readability/LICENSE.md" "$out/shims/Readability-LICENSE.md"

# 5. icon
cp "$repo/web/icons/"icon-512.*.png "$out/assets/icon.png"

for f in www/index.html www/app.bundle.js www/style.css www/i18n/en.json shims/nooz-shims.js shims/Readability-LICENSE.md assets/icon.png; do
  [ -s "$out/$f" ] || { echo "stage: expected $f, missing or empty" >&2; exit 1; }
done
[ ! -e "$out/www/api" ] && [ ! -e "$out/www/js" ] || { echo "stage: server helpers/unbundled js must not ship" >&2; exit 1; }
grep -q 'type="module"' "$out/www/index.html" && { echo "stage: module script survived" >&2; exit 1; }
echo "stage: $(find "$out" -type f | wc -l | tr -d ' ') files in ${out#$repo/}"
