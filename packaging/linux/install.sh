#!/usr/bin/env bash
# Per-user install of the unsigned Nooz desktop skeleton. Run from the extracted tarball.
#   ./install.sh              install to ~/.local/opt/nooz, link ~/.local/bin/nooz
#   ./install.sh --uninstall  remove both
# Writes only under $HOME. No network, no sudo, no desktop entry.
set -euo pipefail

if [ -z "${HOME:-}" ] || [ ! -d "$HOME" ]; then
  echo "HOME is not set to a directory; refusing to run." >&2
  exit 1
fi

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dest="$HOME/.local/opt/nooz"
link="$HOME/.local/bin/nooz"

case "${1:-}" in
  --uninstall)
    # Only remove the link if it points at our install, never a file we did not make.
    if [ -L "$link" ] && [ "$(readlink "$link")" = "$dest/bin/nooz-desktop" ]; then
      rm -f "$link"
    fi
    rm -rf "$dest"
    echo "Removed $dest and $link"
    ;;
  "")
    if [ ! -x "$here/app/bin/nooz-desktop" ]; then
      echo "Run this from the extracted tarball (app/bin/nooz-desktop not found next to it)." >&2
      exit 1
    fi
    if [ -e "$link" ] && [ ! -L "$link" ]; then
      echo "$link exists and is not a symlink; leaving it alone." >&2
      exit 1
    fi
    rm -rf "$dest"
    mkdir -p "$(dirname "$dest")" "$(dirname "$link")"
    cp -a "$here/app" "$dest"
    ln -sfn "$dest/bin/nooz-desktop" "$link"
    echo "Installed to $dest (UNSIGNED skeleton). Launcher: $link"
    case ":$PATH:" in *":$HOME/.local/bin:"*) ;; *) echo "Note: $HOME/.local/bin is not on your PATH." ;; esac
    ;;
  *)
    echo "Usage: $0 [--uninstall]" >&2
    exit 2
    ;;
esac
