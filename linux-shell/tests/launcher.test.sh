#!/usr/bin/env bash
# Launcher and installer tests that need no Qt and no display: a fake qmlscene records its
# argv; the installer is run against a throwaway HOME and the installed symlink launcher is
# invoked through it (the symlink case is the one that breaks naive dirname logic).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
fail() { echo "launcher.test: FAIL: $*" >&2; exit 1; }

# 1. missing qmlscene -> exit 127 with install hints (PATH holds only coreutils shims)
mkdir -p "$tmp/emptybin"; for t in bash dirname readlink cat env; do ln -s "$(command -v $t)" "$tmp/emptybin/$t"; done
mkdir -p "$tmp/tree/bin" "$tmp/tree/qml"; cp "$here/bin/nooz" "$tmp/tree/bin/nooz"; : > "$tmp/tree/qml/Desktop.qml"
set +e; msg="$(PATH="$tmp/emptybin" "$tmp/emptybin/bash" "$tmp/tree/bin/nooz" 2>&1)"; rc=$?; set -e
[ "$rc" = 127 ] || fail "expected exit 127 without qmlscene, got $rc"
echo "$msg" | grep -q "qml-module-qtwebengine" || fail "no install hint"

# 2. fake qmlscene via NOOZ_QMLSCENE receives exactly the Desktop.qml path, no extra args
printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$@" > "%s/argv"\n' "$tmp" > "$tmp/fakeqml"; chmod +x "$tmp/fakeqml"
NOOZ_QMLSCENE="$tmp/fakeqml" "$tmp/tree/bin/nooz" extra-arg
[ "$(cat "$tmp/argv")" = "$tmp/tree/qml/Desktop.qml" ] || fail "argv was: $(cat "$tmp/argv")"

# 3. installer: needs a built tree; fabricate the minimum layout
mkdir -p "$tmp/pkg/bin" "$tmp/pkg/qml" "$tmp/pkg/www" "$tmp/pkg/shims" "$tmp/pkg/assets" "$tmp/home"
cp "$here/bin/nooz" "$tmp/pkg/bin/nooz"; cp "$here/scripts/install.sh" "$tmp/pkg/install.sh"; cp "$here/README.md" "$tmp/pkg/README.md"
: > "$tmp/pkg/qml/Desktop.qml"
HOME="$tmp/home" bash "$tmp/pkg/install.sh" >/dev/null
[ -L "$tmp/home/.local/bin/nooz" ] || fail "no symlink"
HOME="$tmp/home" NOOZ_QMLSCENE="$tmp/fakeqml" "$tmp/home/.local/bin/nooz"
[ "$(cat "$tmp/argv")" = "$tmp/home/.local/opt/nooz-shell/qml/Desktop.qml" ] || fail "symlink launch resolved wrongly: $(cat "$tmp/argv")"
# refuses to clobber a real file
rm "$tmp/home/.local/bin/nooz"; echo x > "$tmp/home/.local/bin/nooz"
if HOME="$tmp/home" bash "$tmp/pkg/install.sh" >/dev/null 2>&1; then fail "clobbered a non-symlink"; fi
rm "$tmp/home/.local/bin/nooz"; HOME="$tmp/home" bash "$tmp/pkg/install.sh" >/dev/null
HOME="$tmp/home" bash "$tmp/pkg/install.sh" --uninstall >/dev/null
[ ! -e "$tmp/home/.local/opt/nooz-shell" ] && [ ! -e "$tmp/home/.local/bin/nooz" ] || fail "uninstall left files"
echo "launcher.test: PASS"
