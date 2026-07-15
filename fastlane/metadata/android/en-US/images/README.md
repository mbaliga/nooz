- `icon.png` — present: the owner's 512×512 Play Store icon (2026-07 asset
  drop); the launcher mipmaps under `app/src/main/res/mipmap-*` are downscaled
  from the same file.
- `featureGraphic.png` — present (1024×500, Play only, opaque RGB — no alpha):
  drawn programmatically from the exact splash-screen recipe (SplashScreen.kt)
  — paperField background, greeked newsprint bleeding off both edges in the
  splash's own onBackground-at-32%-alpha grey, the "Nooz" wordmark in Hyle
  Print Medium, and the short_description.txt tagline beneath it in Hyle
  Grotesk Classic — so it reads as the same object as the app icon and splash,
  not a separate marketing graphic. Bundled fonts only, no external asset.
- `phoneScreenshots/` — still empty; populate with
  `scripts/capture_screenshots.sh` on a device/emulator.
