#!/usr/bin/env python3
"""
Turn the owner's icon artwork into every icon file Nooz ships.

WHY A GENERATOR
One picture, three platforms, and each wants something different from it:

  Android       an adaptive icon: the paper alone on a transparent layer (the
                black plate behind it is a flat colour,
                @color/ic_launcher_background), scaled so the furthest drawn
                pixel lands just inside the circle mask, plus a monochrome
                layer for Android 13+ themed icons.
  Play/F-Droid  a full 512px square. The store rounds the corners itself.
  Web reader    the artwork as drawn, a "maskable" copy Android can crop to
                any shape, and a full square for iOS to round.

Done by hand, the copies drift: the icon changed twice on Android while the
web reader kept showing the original wordmark. With one master and one
command, they can't.

SOURCE OF TRUTH
  tools/icon/master.png   the owner's artwork exactly as supplied: a square
                          canvas with the drawing on a black plate (corners
                          outside the plate may be transparent). To change the
                          icon, replace this file and re-run. Never edit the
                          outputs.

OUTPUTS (all generated)
  app/src/main/res/mipmap-<density>/ic_launcher.png              legacy flat icon
  app/src/main/res/mipmap-<density>/ic_launcher_foreground.png   adaptive layer
  app/src/main/res/mipmap-<density>/ic_launcher_monochrome.png   themed-icon layer
  fastlane/metadata/android/en-US/images/icon.png                store icon
  web/icons/*.png, plus the icon references in web/manifest.webmanifest and
  web/index.html. vercel.json serves web/icons/ as immutable for a year, so
  each web icon's filename carries a hash of its pixels: new artwork gets new
  URLs rather than a year of cached old icons. web/icons/ is wholly generated;
  stale files there are deleted.

Run:  python3 tools/icon/generate.py      (needs Pillow and numpy)
"""

import hashlib
import json
import pathlib
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

REPO = pathlib.Path(__file__).resolve().parents[2]
MASTER = REPO / "tools" / "icon" / "master.png"
ANDROID_RES = REPO / "app" / "src" / "main" / "res"
STORE_ICON = REPO / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png"
WEB_ICONS = REPO / "web" / "icons"
WEB_MANIFEST = REPO / "web" / "manifest.webmanifest"
WEB_INDEX = REPO / "web" / "index.html"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_ICON_DP = 48

# Each adaptive layer is 108dp square, and launchers show its central 72dp
# through their mask. A circle is the tightest common mask, so the furthest
# drawn pixel goes 35.5dp from the centre: just inside the 36dp radius, so
# nothing is clipped and the drawing still fills the aperture.
ADAPTIVE_LAYER_DP = 108
MASK_RADIUS_DP = 36
CONTENT_RADIUS_DP = 35.5

# A maskable web icon's safe zone is a circle 40% of its width in radius.
# Same margin inside it as on Android.
MASKABLE_CONTENT_RADIUS = 0.40 * CONTENT_RADIUS_DP / MASK_RADIUS_DP

# A pixel this dark (0-255 luma) that the canvas edge can reach through other
# pixels this dark is the plate, not the drawing. The supplied plate carries
# compression noise up to 6; the paper's outline is ~36.
PLATE_THRESHOLD = 8

# After downscaling, alpha this faint is resampling ringing, not drawing.
FAINT_ALPHA = 3


def load_master():
    if not MASTER.exists():
        sys.exit(f"missing {MASTER.relative_to(REPO)}")
    art = Image.open(MASTER).convert("RGBA")
    if art.width != art.height:
        sys.exit(f"{MASTER.name} must be square, not {art.width}x{art.height}")
    return art


def separate(art):
    """Split the artwork into the drawing and the black plate behind it.

    Returns the artwork composited over black with the plate's noise cleaned
    to pure black (an RGB array), and a mask of the drawing: every pixel the
    canvas edge cannot reach through near-black. A flood fill rather than a
    plain threshold, so dark linework inside the paper stays part of it."""
    flat = Image.new("RGBA", art.size, (0, 0, 0, 255))
    flat.alpha_composite(art)
    flat = flat.convert("RGB")
    dark = np.asarray(flat.convert("L")) <= PLATE_THRESHOLD
    # copy(): an image made from an array is read-only, and floodfill swallows
    # the write error, leaving the plate unfilled without a word.
    plate = Image.fromarray(np.where(dark, 255, 0).astype(np.uint8)).copy()
    n = art.width
    for corner in ((0, 0), (n - 1, 0), (0, n - 1), (n - 1, n - 1)):
        if plate.getpixel(corner) == 255:
            ImageDraw.floodfill(plate, corner, 128)
    drawing = np.asarray(plate) != 128
    # Just inside the plate's left edge, halfway down, must be plate.
    if drawing[n // 2, n // 50] or not drawing.any():
        sys.exit(f"{MASTER.name}: expected a drawing on a black plate")
    rgb = np.asarray(flat).copy()
    rgb[~drawing] = 0
    return rgb, drawing


def drawing_radius(drawing):
    """Distance from the canvas centre to the furthest corner of any drawn pixel."""
    c = drawing.shape[0] / 2
    ys, xs = np.nonzero(drawing)
    dx = np.maximum(np.abs(xs - c), np.abs(xs + 1 - c))
    dy = np.maximum(np.abs(ys - c), np.abs(ys + 1 - c))
    return float(np.sqrt(dx * dx + dy * dy).max())


def scaled(img, size, fit=None, fill=(0, 0, 0, 0)):
    """img resampled to size x size, keeping the master's centre at the centre.

    Without `fit`, the master's canvas maps exactly onto the output. With
    `fit=(radius_px, drawing_radius)`, the drawing is scaled so its furthest
    pixel lands radius_px from the centre, padding with `fill` as needed."""
    n = img.width
    half = n / 2
    if fit:
        radius_px, reach = fit
        half = (size / 2) * reach / radius_px
    pad = max(0, int(np.ceil(half - n / 2)) + 1)
    if pad:
        canvas = Image.new(img.mode, (n + 2 * pad, n + 2 * pad), fill)
        canvas.paste(img, (pad, pad))
        img = canvas
    c = img.width / 2
    # Pillow premultiplies alpha for this, so transparent black never bleeds.
    return img.resize(
        (size, size), Image.Resampling.LANCZOS, box=(c - half, c - half, c + half, c + half)
    )


def clear_faint(img):
    px = np.asarray(img).copy()
    px[px[..., 3] < FAINT_ALPHA] = 0
    return Image.fromarray(px)


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.info = {}  # don't carry the master's EXIF/sRGB chunks into every icon
    img.save(path, optimize=True)
    return path


def rgba(rgb, alpha):
    return Image.fromarray(np.dstack([rgb, alpha]).astype(np.uint8))


def main():
    art = load_master()
    rgb, drawing = separate(art)
    reach = drawing_radius(drawing)
    luma = np.asarray(Image.fromarray(rgb).convert("L"))
    white = np.full_like(rgb, 255)

    # The drawing alone, for the adaptive foreground.
    paper = rgba(rgb, np.where(drawing, 255, 0))
    # Luminance as alpha: the paper is solid, the ruled columns and the
    # nameplate are knocked out. A filled silhouette of a plain rectangle
    # would be a featureless slab once tinted.
    themed = rgba(white, luma)
    # The artwork as drawn, rounded plate and all, minus the plate's noise.
    drawn = rgba(rgb, np.asarray(art.getchannel("A")))
    # Full bleed, for surfaces that apply their own corner mask.
    square = Image.fromarray(rgb)

    written = []
    for density, f in DENSITIES.items():
        folder = ANDROID_RES / f"mipmap-{density}"
        layer = round(ADAPTIVE_LAYER_DP * f)
        fit = (CONTENT_RADIUS_DP * f, reach)
        written.append(save(clear_faint(scaled(paper, layer, fit)), folder / "ic_launcher_foreground.png"))
        written.append(save(clear_faint(scaled(themed, layer, fit)), folder / "ic_launcher_monochrome.png"))
        written.append(save(clear_faint(scaled(drawn, round(LEGACY_ICON_DP * f))), folder / "ic_launcher.png"))

    # Play asks for a 32-bit PNG; fully opaque, since it applies its own mask.
    written.append(save(scaled(square, 512, fill=(0, 0, 0)).convert("RGBA"), STORE_ICON))

    web = {
        "icon-192": clear_faint(scaled(drawn, 192)),
        "icon-512": clear_faint(scaled(drawn, 512)),
        "icon-maskable-512": scaled(square, 512, (MASKABLE_CONTENT_RADIUS * 512, reach), fill=(0, 0, 0)),
        "apple-touch-icon": scaled(square, 180, fill=(0, 0, 0)),
    }
    digest = hashlib.sha256()
    for key in sorted(web):
        digest.update(key.encode())
        digest.update(web[key].tobytes())
    version = digest.hexdigest()[:8]
    names = {key: f"{key}.{version}.png" for key in web}

    for stale in WEB_ICONS.glob("*.png"):
        if stale.name not in names.values():
            stale.unlink()
            print(f"removed {stale.relative_to(REPO)}")
    for key, img in web.items():
        written.append(save(img, WEB_ICONS / names[key]))

    manifest = json.loads(WEB_MANIFEST.read_text(encoding="utf-8"))
    manifest["icons"] = [
        {"src": f"icons/{names['icon-192']}", "sizes": "192x192", "type": "image/png"},
        {"src": f"icons/{names['icon-512']}", "sizes": "512x512", "type": "image/png"},
        {
            "src": f"icons/{names['icon-maskable-512']}",
            "sizes": "512x512",
            "type": "image/png",
            "purpose": "maskable",
        },
    ]
    WEB_MANIFEST.write_bytes((json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))

    html = WEB_INDEX.read_bytes().decode("utf-8")
    for rel, key in (("icon", "icon-192"), ("apple-touch-icon", "apple-touch-icon")):
        link = re.compile(rf'(<link rel="{rel}"[^>]*?href=")[^"]*(")')
        html, found = link.subn(rf"\g<1>icons/{names[key]}\g<2>", html)
        if found != 1:
            sys.exit(f'web/index.html: expected one <link rel="{rel}">, found {found}')
    WEB_INDEX.write_bytes(html.encode("utf-8"))

    for path in written:
        print(f"wrote {path.relative_to(REPO)}")
    print(f"updated {WEB_MANIFEST.relative_to(REPO)}, {WEB_INDEX.relative_to(REPO)}")
    scale = CONTENT_RADIUS_DP / reach
    ys, xs = np.nonzero(drawing)
    print(
        f"drawing reaches {reach:.1f}px from the master's centre; on the launcher it is "
        f"{(xs.max() + 1 - xs.min()) * scale:.1f} x {(ys.max() + 1 - ys.min()) * scale:.1f}dp "
        f"of the {ADAPTIVE_LAYER_DP}dp layer"
    )


if __name__ == "__main__":
    main()
