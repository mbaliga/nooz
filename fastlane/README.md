# fastlane/

Store-listing metadata for both Google Play and F-Droid.

`fastlane/metadata/android/<locale>/` is a single, shared convention both
[F-Droid][fdroid-docs] (which reads it directly from an app's own source repo) and
Google Play (via fastlane's `supply`, or the Triple-T Gradle Play Publisher plugin)
understand, so one directory serves both listings.

The house-wide answers, publisher identity and deploy runbook live in
`Personal-Tracker/store/`. This app's own deltas are in `docs/store/play-console.md`.

## Status

- `title.txt` — **"Nooz"**, the owner's decision (D5, 2026-07-10). No longer a
  placeholder.
- `short_description.txt`, `full_description.txt` — written and current.
- `changelogs/1.txt`, `changelogs/4.txt` — `4` matches the current
  `versionCode = 4`. Play needs a file named for the exact versionCode being shipped.
- `images/icon.png`, `images/featureGraphic.png` — **present** (owner asset drop,
  2026-07). See `images/README.md`.
- `images/phoneScreenshots/` — **empty.** Needs a real device; run
  `scripts/capture_screenshots.sh`.

## The one open decision

The **licence** is still RESERVED (`LICENSE.RESERVED`), and it is owner-only.
F-Droid will not include an app without an OSI/FSF-approved licence and a real
`LICENSE` file, so the F-Droid half of this metadata cannot go live until that
lands. Play is unaffected and can ship today. Nothing here selects, suggests or
infers a licence.

[fdroid-docs]: https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/
