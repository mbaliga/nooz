#!/usr/bin/env bash
# Static checks for linux-shell: bash syntax, shellcheck (when installed), qmllint, and the
# no-app-id / no-.desktop lint shared with packaging/linux (plan R11).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for f in "$here"/scripts/*.sh "$here"/tests/*.sh "$here/bin/nooz"; do bash -n "$f"; done
echo "bash -n ok"
if command -v shellcheck >/dev/null 2>&1; then
  shellcheck "$here"/scripts/*.sh "$here"/tests/*.sh "$here/bin/nooz"; echo "shellcheck ok"
else
  echo "shellcheck not installed; skipped"
fi
if command -v qmllint >/dev/null 2>&1; then
  qmllint "$here/qml/Desktop.qml"; echo "qmllint ok"
else
  echo "qmllint not installed; skipped"
fi
python3 -I - "$here" <<'PY'
import pathlib, re, sys
root = pathlib.Path(sys.argv[1])
rdns = re.compile(r"\b(?:com|org|net|io|xyz|app|dev|me|eu)\.[a-z0-9][a-z0-9_-]*\.[a-z0-9_.-]+", re.I)
bad = []
for p in sorted(root.rglob("*")):
    if not p.is_file() or "build" in p.relative_to(root).parts or p.name == "check.sh":
        continue
    if p.suffix == ".desktop":
        bad.append(f"{p.relative_to(root)}: .desktop file")
    try:
        text = p.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    for n, line in enumerate(text.splitlines(), 1):
        hits = [m.group(0) for m in rdns.finditer(line)
                if not re.search(r"\.(js|json|md|png|sh|qml|yaml|yml|txt|env)$", m.group(0), re.I)]
        if hits or "[Desktop Entry]" in line:
            bad.append(f"{p.relative_to(root)}:{n}: {line.strip()}")
if bad:
    print("Identifier lint failed (R11):", *bad, sep="\n  ", file=sys.stderr); sys.exit(1)
print("identifier lint ok")
PY
