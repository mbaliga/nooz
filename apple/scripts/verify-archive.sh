#!/usr/bin/env bash
# Sanity-check an unsigned xcarchive: privacy manifest present, bundle id as ruled, the web
# reader and shim staged, server helpers absent, no ATS exceptions. macOS only (plutil).
set -euo pipefail
arc="${1:?path to .xcarchive}"
app="$(echo "$arc"/Products/Applications/*.app)"
[ -d "$app" ] || { echo "verify: no .app inside $arc" >&2; exit 1; }
id="$(plutil -extract CFBundleIdentifier raw "$app/Info.plist")"
[ "$id" = "com.asoc.nooz" ] || { echo "verify: bundle id is $id, want com.asoc.nooz" >&2; exit 1; }
[ -f "$app/PrivacyInfo.xcprivacy" ] || { echo "verify: PrivacyInfo.xcprivacy missing from bundle" >&2; exit 1; }
[ "$(plutil -extract NSPrivacyTracking raw "$app/PrivacyInfo.xcprivacy")" = "false" ] || { echo "verify: NSPrivacyTracking not false" >&2; exit 1; }
for f in web/index.html web/js/app.js Readability.js Readability-LICENSE.md ios-shim.js; do
  [ -e "$app/$f" ] || { echo "verify: $f missing from bundle" >&2; exit 1; }
done
[ ! -e "$app/web/api" ] || { echo "verify: web/api must not ship" >&2; exit 1; }
if plutil -extract NSAppTransportSecurity raw "$app/Info.plist" >/dev/null 2>&1; then
  echo "verify: NSAppTransportSecurity present; the README says there are no ATS exceptions" >&2; exit 1
fi
echo "verify: ok ($id)"
