#!/usr/bin/env bash
# Packages the Compose Desktop app-image of the desktop skeleton into a tarball.
# UNSIGNED, scaffold only: the app is an empty window (see README.md).
# Usage: packaging/linux/build-tarball.sh [OUT_DIR]   (default: desktop/build/packaging)
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
out_dir="${1:-$root/desktop/build/packaging}"
name="nooz-linux-x64-UNSIGNED-not-for-release"
app_src="${NOOZ_APP_IMAGE:-$root/desktop/build/compose/binaries/main/app/nooz-desktop}"

if [ ! -x "$app_src/bin/nooz-desktop" ]; then
  echo "No app image at $app_src." >&2
  echo "Run: ./gradlew -p desktop createDistributable   (or set NOOZ_APP_IMAGE)" >&2
  exit 1
fi

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/$name" "$out_dir"
cp -a "$app_src" "$stage/$name/app"
cp "$here/install.sh" "$stage/$name/install.sh"
cp "$here/README.md" "$stage/$name/README.md"
chmod +x "$stage/$name/install.sh"

tar -C "$stage" -czf "$out_dir/$name.tar.gz" "$name"
echo "Wrote $out_dir/$name.tar.gz (UNSIGNED, not for release)"
