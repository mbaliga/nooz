#!/usr/bin/env bash
# Boot an iPhone simulator, install and launch the app, and fail if it is not still
# running 15 seconds later. macOS only. Usage: simulator-smoke.sh path/to/Nooz.app
set -euo pipefail
app="${1:?path to Nooz.app}"
bundle_id="com.asoc.nooz"

udid="$(xcrun simctl list devices available -j | python3 -c '
import json,sys
d=json.load(sys.stdin)["devices"]
for rt,devs in d.items():
    if "iOS" not in rt: continue
    for x in devs:
        if "iPhone" in x["name"]: print(x["udid"]); sys.exit(0)
sys.exit(1)')"
echo "smoke: using simulator $udid"
xcrun simctl boot "$udid" || true
xcrun simctl bootstatus "$udid" -b
xcrun simctl install "$udid" "$app"
xcrun simctl launch "$udid" "$bundle_id"
sleep 15
if xcrun simctl spawn "$udid" launchctl list | grep -q "$bundle_id"; then
  echo "smoke: $bundle_id still running after 15s"
else
  echo "smoke: $bundle_id is NOT running after 15s (crashed or exited)" >&2
  exit 1
fi
mkdir -p apple/build
xcrun simctl io "$udid" screenshot apple/build/smoke.png && echo "smoke: screenshot at apple/build/smoke.png"
