#!/usr/bin/env bash
# Stage the web reader into the iOS bundle's resource folder. Run before `xcodegen generate`.
#   * copies web/ minus server helpers, tests, node_modules, deploy config -> Nooz/Resources/web
#   * copies @mozilla/readability's Readability.js (Apache-2.0) from web/node_modules
# Needs `npm ci` to have run in web/. Fails loudly if anything expected is missing.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(cd "$here/.." && pwd)"
dest="$here/Nooz/Resources/web"

[ -f "$repo/web/node_modules/@mozilla/readability/Readability.js" ] || {
  echo "stage-web: run 'npm ci' in web/ first (Readability.js missing)" >&2; exit 1; }

rm -rf "$dest"
mkdir -p "$dest"
( cd "$repo/web" && tar \
    --exclude=./node_modules --exclude=./tests --exclude=./api \
    --exclude=./package.json --exclude=./package-lock.json --exclude=./vercel.json \
    -cf - . ) | ( cd "$dest" && tar -xf - )
cp "$repo/web/node_modules/@mozilla/readability/Readability.js" "$here/Nooz/Resources/Readability.js"
# Apache-2.0 asks that the licence travel with the code.
cp "$repo/web/node_modules/@mozilla/readability/LICENSE.md" "$here/Nooz/Resources/Readability-LICENSE.md"

for f in index.html style.css manifest.webmanifest js/app.js js/db.js fonts i18n icons; do
  [ -e "$dest/$f" ] || { echo "stage-web: expected $f in staged web, missing" >&2; exit 1; }
done
[ ! -e "$dest/api" ] || { echo "stage-web: server helpers must not ship in the app" >&2; exit 1; }
echo "stage-web: staged $(find "$dest" -type f | wc -l | tr -d ' ') files into ${dest#$repo/}"
