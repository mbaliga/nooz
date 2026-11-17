#!/usr/bin/env python3
"""Fail if anything under packaging/linux names a reverse-DNS app id or ships a
.desktop file. No per-platform identifier may exist before the owner adds a NAMES.md
row (R11, OQ-25). Standard library only."""
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
REVERSE_DNS = re.compile(r"\b(?:com|org|net|io|xyz|app|dev|me|eu)\.[a-z0-9][a-z0-9_-]*\.[a-z0-9_.-]+", re.I)
DESKTOP_ENTRY = re.compile(r"\[Desktop Entry\]", re.I)


def main() -> int:
    bad = []
    for path in sorted(HERE.rglob("*")):
        if not path.is_file() or path.name == pathlib.Path(__file__).name:
            continue
        rel = path.relative_to(HERE)
        if path.suffix == ".desktop":
            bad.append(f"{rel}: .desktop file")
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for n, line in enumerate(text.splitlines(), 1):
            if REVERSE_DNS.search(line) or DESKTOP_ENTRY.search(line):
                bad.append(f"{rel}:{n}: {line.strip()}")
    if bad:
        print("Identifier lint failed (R11/OQ-25):", *bad, sep="\n  ", file=sys.stderr)
        return 1
    print("identifier lint ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
