Empty on purpose. This is where a real device/emulator (or the final icon,
once RESERVED decisions land) would put:

- `icon.png` — 512×512, needs the final RESERVED icon.
- `featureGraphic.png` — 1024×500, Play only.
- `phoneScreenshots/` — populate with `scripts/capture_screenshots.sh` run
  against a connected device or emulator with a debug build installed; this
  build session has neither, so none are captured here yet.

See `fastlane/README.md` for the full gating picture.
