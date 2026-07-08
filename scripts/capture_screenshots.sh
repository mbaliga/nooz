#!/usr/bin/env bash
# Captures store-listing screenshots (brief §P7) from a connected device or
# emulator running a debug build. This cannot run in the sandbox this app was
# built in — there is no Android SDK/emulator/device here — so it has never
# been executed; it is real, ready-to-run tooling for whoever has one, not a
# stand-in for one.
#
# Requirements:
#   - `adb` on PATH, exactly one connected device or emulator
#   - the target flavor already installed, e.g.:
#       ./gradlew installFossDebug
#   - the app already launched at least once (so sources/data exist —
#     screenshots of an empty first-run state aren't useful store images)
#
# The app is a single Activity with in-memory Compose navigation between its
# three tabs (Reader/River/Sources) and no deep-link support to jump straight
# to one, so this script captures whatever is currently on screen rather than
# driving navigation itself: launch the app, get it into the state you want
# (tap to the tab, open an article, scrub a river week — whatever the shot
# calls for), then run this script once per shot.
set -euo pipefail

OUT_DIR="${1:-fastlane/metadata/android/en-US/images/phoneScreenshots}"
mkdir -p "$OUT_DIR"

device_count=$(adb devices | awk 'NR>1 && NF>0' | wc -l | tr -d ' ')
if [ "$device_count" -eq 0 ]; then
  echo "No connected device/emulator found. Start one and install a debug build first." >&2
  exit 1
fi
if [ "$device_count" -gt 1 ]; then
  echo "Multiple devices connected; set ANDROID_SERIAL to pick one." >&2
  exit 1
fi

stamp=$(date +%Y%m%d-%H%M%S)
out_file="$OUT_DIR/screenshot-$stamp.png"
adb exec-out screencap -p > "$out_file"
echo "Saved $out_file"
