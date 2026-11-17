#!/usr/bin/env bash
# Per-user install of the unsigned Nooz Linux thin client. Run from the extracted tarball.
#   ./install.sh              copy to ~/.local/opt/nooz-shell, link ~/.local/bin/nooz
#   ./install.sh --uninstall  remove both (the reader's data under ~/.local/share/nooz stays)
# Writes only under $HOME. No network, no sudo, no desktop entry (none until the owner adds
# a NAMES.md row for the app id: plan R11).
set -euo pipefail

if [ -z "${HOME:-}" ] || [ ! -d "$HOME" ]; then
  echo "HOME is not set to a directory; refusing to run." >&2
  exit 1
fi
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dest="$HOME/.local/opt/nooz-shell"
link="$HOME/.local/bin/nooz"

case "${1:-}" in
  --uninstall)
    if [ -L "$link" ] && [ "$(readlink "$link")" = "$dest/bin/nooz" ]; then rm -f "$link"; fi
    rm -rf "$dest"
    echo "Removed $dest and $link. Reader data, if any, is under ~/.local/share/nooz."
    ;;
  "")
    [ -x "$here/bin/nooz" ] || { echo "Run this from the extracted tarball (bin/nooz not found)." >&2; exit 1; }
    if [ -e "$link" ] && [ ! -L "$link" ]; then
      echo "$link exists and is not a symlink; leaving it alone." >&2; exit 1
    fi
    rm -rf "$dest"
    mkdir -p "$dest" "$(dirname "$link")"
    for d in bin qml www shims assets; do cp -a "$here/$d" "$dest/$d"; done
    cp -a "$here/README.md" "$dest/README.md"
    ln -sfn "$dest/bin/nooz" "$link"
    echo "Installed to $dest (UNSIGNED thin client). Launcher: $link"
    command -v qmlscene >/dev/null 2>&1 || echo "Note: qmlscene not found; see README.md for the Qt 5 packages."
    case ":$PATH:" in *":$HOME/.local/bin:"*) ;; *) echo "Note: $HOME/.local/bin is not on your PATH." ;; esac
    ;;
  *) echo "Usage: $0 [--uninstall]" >&2; exit 2 ;;
esac
