#!/usr/bin/env bash
# Assembles the Linux desktop thin client into one arch-independent, UNSIGNED tarball:
#   nooz-linux-thin-client-UNSIGNED-not-for-release.tar.gz
# It is the Ubuntu Touch payload (web reader bundled by ubuntu-touch/scripts/stage.sh) plus
# the desktop window (qml/Desktop.qml), the launcher and the installer. Nothing is compiled;
# the runtime is the system's Qt 5 qmlscene + QtWebEngine.
# Needs: node 20 and `npm ci` in web/, python3. Usage: build-tarball.sh [OUT_DIR]
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/.." && pwd)"
ut="$repo/ubuntu-touch"
out_dir="${1:-$here/build/out}"
name="nooz-linux-thin-client-UNSIGNED-not-for-release"

bash "$ut/scripts/stage.sh"

stage="$(mktemp -d)"; trap 'rm -rf "$stage"' EXIT
root="$stage/$name"
mkdir -p "$root/bin" "$root/qml/js" "$out_dir"
cp "$here/bin/nooz" "$root/bin/nooz"
cp "$here/qml/Desktop.qml" "$ut/qml/Main.qml" "$root/qml/"
cp "$ut/qml/js/HostBridge.js" "$root/qml/js/"
cp -r "$ut/build/payload/www" "$ut/build/payload/shims" "$ut/build/payload/assets" "$root/"
cp "$here/scripts/install.sh" "$root/install.sh"
cp "$here/README.md" "$root/README.md"
chmod +x "$root/bin/nooz" "$root/install.sh"

for f in bin/nooz qml/Desktop.qml qml/Main.qml qml/js/HostBridge.js www/index.html www/app.bundle.js shims/nooz-shims.js shims/Readability-LICENSE.md install.sh; do
  [ -s "$root/$f" ] || { echo "build-tarball: $f missing or empty" >&2; exit 1; }
done
# Reproducible-ish: fixed owner, sorted names, mtime pinned to the HEAD commit when in git.
mtime="$(git -C "$repo" log -1 --format=%ct 2>/dev/null || date +%s)"
tar -C "$stage" --sort=name --owner=0 --group=0 --numeric-owner --mtime="@$mtime" \
    -czf "$out_dir/$name.tar.gz" "$name"
echo "Wrote $out_dir/$name.tar.gz (UNSIGNED, not for release)"
