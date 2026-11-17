#!/usr/bin/env bash
# stage -> cmake install -> `click build`  =>  build/out/<name>_<version>_all.click (UNSIGNED).
# Needs: node 20 + `npm ci` in web/, cmake, and the Ubuntu `click` tool. On a host whose
# default python3 is not the distro one, `click` fails importing gi: this script retries with
# the distro interpreter (/usr/bin/python3.12 on Ubuntu 24.04).
# This is the container-mode route; Clickable-in-Docker (clickable.yaml) is the route that
# also runs click-review, and is a separate CI job.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
out="$here/build/out"
root="$here/build/clickroot"
bash "$here/scripts/stage.sh"
rm -rf "$root" "$here/build/cmake" "$out"; mkdir -p "$out"
cmake -S "$here" -B "$here/build/cmake" -DCMAKE_INSTALL_PREFIX="$root" -DCLICK_ARCH=all >/dev/null
cmake --install "$here/build/cmake" >/dev/null
python3 "$here/scripts/check-click.py" "$root"
( cd "$out" && { click build "$root" 2>/dev/null || /usr/bin/python3.12 /usr/bin/click build "$root"; } )
ls -l "$out"/*.click
