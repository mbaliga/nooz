- `icon.png` — present: the owner's 512×512 Play Store icon (2026-07 asset
  drop); the launcher mipmaps under `app/src/main/res/mipmap-*` are downscaled
  from the same file.
- `featureGraphic.png` — present (1024×500, Play only, opaque RGB — no alpha):
  plain paperField background, the "Nooz" wordmark in Playfair Display Black
  (matching the app's actual wordmark — see `third_party/fonts/README.md`),
  and the short_description.txt tagline beneath it in Hyle Grotesk Classic —
  written out in full ("from your chosen sources") rather than an em-dashed
  clause (owner: no em dash). No greeked newsprint bleed — plain paper, just
  the mark and the line (owner: also drop the text above and below).
  Bundled fonts only, no external asset. Regenerated 2026-07 when the
  wordmark font changed from Hyle Print to Playfair Display Black.
- `phoneScreenshots/` — still empty; populate with
  `scripts/capture_screenshots.sh` on a device/emulator.
