# Nooz — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`. Listing text
> lives in `fastlane/metadata/android/en-US/`.

| | |
|---|---|
| applicationId | `dev.asystemofcells.nooz` (already registered in Play, owner-decided 2026-07-20) |
| Flavors | `full` (Play, no applicationId suffix) · `foss` (F-Droid, `.foss` suffix) |
| Version at time of writing | `0.3.1` (versionCode `4`) |
| Category | **News & Magazines** |
| Tags | rss, feed reader, opml, news, offline, privacy |
| Contact email | `nooz@asystemofcells.com` |
| Website | `https://asystemofcells.com/nooz` |
| Privacy policy | `https://asystemofcells.com/nooz/privacy` |

## Deltas from the house defaults

### News app declaration — the one form only this app fills
- **Is your app a news app? YES.** Nooz is the only app in the house that answers
  yes, and answering it wrongly is a policy violation either way.
- Play will then ask for:
  - **Publisher name:** `A System of Cells`
  - **Is the app a news aggregator?** **Yes.** It aggregates third-party feeds the
    user adds; it publishes no journalism of its own.
  - **Do you employ or contract journalists?** No.
  - **Country of primary operation:** India.
  - A link to the publisher's site: `https://asystemofcells.com`.
- The honest framing to keep consistent everywhere: Nooz has **no editorial
  output**. It renders feeds the user chose. That is what makes the aggregator
  answer correct and keeps the listing copy ("it never claims to show all the
  news") consistent with the declaration.

### Data safety
**No data collected. No data shared.**

| Question | Answer |
|---|---|
| Collect or share any user data? | **No** |
| Encrypted in transit? | Yes (feed fetches are HTTPS where the source offers it) |
| Deletion? | Users can delete data in the app |

The nuance worth being precise about: the app **fetches feeds the user added**,
which means those publishers' servers see a request from the user's IP, exactly as
a browser would. That is not collection *by this app* under Google's definition
(nothing is transmitted to us; we run no server), and it is disclosed plainly in
the privacy policy rather than hidden. There is no analytics, no crash reporting
and no ad SDK anywhere in either flavor.

### Permissions
| Permission | Why | Play form? |
|---|---|---|
| `INTERNET` | Fetch the feeds the user added. | No |
| `ACCESS_NETWORK_STATE` | Avoid a fetch when offline. | No |

No sensitive-permission form. Notably absent, and deliberately so:
**no `POST_NOTIFICATIONS`**, because the app has no notifications at all. That is a
product rule, not an oversight, and the listing says so.

### Content rating
- Category `Reference, News, or Educational` is the better fit here than the house
  default of `Utility` — pick it for this app.
- **"Does the app provide access to unmoderated user-generated content or
  unrestricted internet browsing?"** Answer **No**: the user adds specific feed
  URLs, there is no in-app browser and no discovery of arbitrary web pages. If you
  ever add an in-app web view that follows arbitrary links, this answer changes.
- Everything else No. Expected rating **Everyone**.

### Ads
- No. The app additionally has no ad SDK in either flavor, and the `foss` flavor is
  built to have zero proprietary dependencies at all.

## F-Droid

`fastlane/metadata/android/en-US/` already serves F-Droid with no extra work, and
the `foss` flavor is built for it (zero proprietary or Play dependencies).

**The blocker is not the metadata, it is the licence.** `LICENSE.RESERVED` is a
deliberate placeholder for an owner-only decision, and F-Droid will not accept an
app without an OSI/FSF-approved licence and a real `LICENSE` file. Nothing in this
sheet or the fastlane metadata selects, suggests or infers one, per that file's own
instruction. When the owner decides, the F-Droid path opens with no further copy work.

Anti-features to declare at that point: **none**. Nooz has no cloud provider, no
tracking and no non-free dependencies in the `foss` flavor.

## Pre-submit checklist

- [ ] Icon and feature graphic are **already present** in
      `fastlane/metadata/android/en-US/images/` (owner asset drop, 2026-07). Nothing
      to produce.
- [ ] `phoneScreenshots/` still empty. Run `scripts/capture_screenshots.sh` on a
      real device.
- [ ] Privacy page published at `asystemofcells.com/nooz/privacy` and readable
      logged out.
- [ ] Ship `bundleFullRelease`, not `bundleFossRelease` — only the `full` flavor
      carries the applicationId Play has registered.
- [ ] A device with an old `xyz.mdhv.riverwip.full` test build installed must
      uninstall first; Android treats the new applicationId as a different app.
