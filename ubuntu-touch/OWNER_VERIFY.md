# Ubuntu Touch: what only you can verify

Nothing in `ubuntu-touch/` has run on a phone. Everything below is **NEEDS-DEVICE-VALIDATION**.
Evidence from the container is desktop Qt 5.15 on x86_64 only. Device in the plan: OnePlus 6,
read as 20.04-only (OQ-37); the click targets `ubuntu-sdk-20.04`.

Get the click from the draft release the `ubuntu-touch` workflow attaches (tag `v*` or a manual
run with a tag), or build it: `bash ubuntu-touch/scripts/build-click.sh`. It is **unsigned**.
Install: `clickable install` over adb/ssh, or copy to the phone and `pkcon install-local`.

Tick each line; write what you saw, not what you expect.

1. [ ] **Installs.** The click installs; the icon shows as "Nooz".
2. [ ] **Launches.** The app opens to the reader, not a blank or error page.
3. [ ] **Sandbox (D-UT-4).** Edit the installed `.desktop` (or rebuild) to drop
       `QTWEBENGINE_DISABLE_SANDBOX=1`. Does it still launch? If yes, ship without it.
4. [ ] **AppArmor.** `journalctl`/`dmesg` shows no DENIED lines during a normal session, with
       `networking` + `webview` only. List any denial here: ______
5. [ ] **Feeds.** Add a real https source (e.g. a BBC or HN feed). Items appear. Does a
       pull/refresh work? An unreachable host shows the reader's normal error?
6. [ ] **Articles.** Open an item: full-article extraction runs (title, body, lead image), not
       just the feed excerpt. Try one site with a redirect (expect possibly wrong relative links).
7. [ ] **Internal addresses refused.** Add `https://192.168.x.x/feed` as a source: it must fail
       with "Refused: internal address", not fetch.
8. [ ] **Persistence.** Add sources, read some items, force-close, relaunch from the app drawer:
       sources and read history survive (IndexedDB under the app's storage).
9. [ ] **Suspend.** Background the app for a minute, return: it resumes without a blank page.
10. [ ] **Links.** Tap an external link in an article: it opens in the browser (Morph), not inside
        the app. Tap a reader-internal route: it stays.
11. [ ] **Fonts and scripts.** Hyle fonts load; a non-Latin locale (Hindi, Arabic) renders. Note
        any tofu per locale.
12. [ ] **Gestures/keyboard.** The on-screen keyboard does not cover the add-source field; the
        edge-swipe back gesture does something sane.
13. [ ] **Known gaps behave as documented** (README): OPML import/export and clipping export
        **fail** (expected, not written). Confirm they fail without crashing the app.
14. [ ] **Offline.** Airplane mode: cached items still read; the reader's offline copy shows.

## Decisions waiting on you

- Click name: `nooz.asoc` is provisional (NAMES.md row first, plan R11) and the maintainer
  field is `OWNER-FILL`: both live only in `ubuntu-touch/config/identity.env`.
- Is "thin client over the web reader, native fetch, no proxy" the shape you want for UT (NQ-1)?
  Or do you still want the QML-over-JVM-core primary (U1 to U4, needs a 24.04 device and S-UT1)?
- OpenStore: listing needs a declared licence (OQ-12; Nooz is licence-RESERVED, so a sideloaded
  click is the only route until that is ruled). Not attempted.
- Whether a 24.04 device/image arrives (OQ-37); then the framework string and a canary lane.
