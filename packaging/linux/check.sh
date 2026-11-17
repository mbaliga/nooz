#!/usr/bin/env bash
# Static checks for packaging/linux: bash syntax, shellcheck (when installed), identifier lint.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for f in "$here"/*.sh; do
  bash -n "$f"
done
echo "bash -n ok"
if command -v shellcheck >/dev/null 2>&1; then
  shellcheck "$here"/*.sh
  echo "shellcheck ok"
else
  echo "shellcheck not installed; skipped"
fi
python3 -I "$here/lint_ids.py"
