#!/usr/bin/env python3
"""Derive the iOS 1024px App Store icon from the one master (tools/icon/master.png).

Apple wants an opaque, square, un-rounded 1024x1024 PNG (iOS applies the mask), so the
master's rounded black plate is flattened onto black and scaled. Output is committed;
re-run after replacing the master. Like tools/icon/generate.py, never hand-edit the output.
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
src = Image.open(root / "tools/icon/master.png").convert("RGBA")
plate = Image.new("RGBA", src.size, (0, 0, 0, 255))
plate.alpha_composite(src)
out = plate.convert("RGB").resize((1024, 1024), Image.LANCZOS)
dest = root / "apple/Nooz/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
out.save(dest, optimize=True)
print(dest, out.size, out.mode)
