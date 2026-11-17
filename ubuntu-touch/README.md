# ubuntu-touch/ — Nooz thin client for Ubuntu Touch

**Read this first: what this is and is not.** This is **the Nooz web reader (`web/`) bundled in a
click and hosted by a small QML + QtWebEngine shell**, with the two server helpers
(`/api/feed`, `/api/article`) replaced by native fetching inside the shell. Per plan rule R12 it
is a **thin client** and is **never called a port of the Android app**. There is no Room store, no
on-device llama.cpp or Kokoro, no Lens, no widgets, no background fetch, no BYOK keys. The plan's
primary shape (Lomiri QML pages over a headless jlinked JVM core, steps U1 to U4, about 5
engineer-weeks, blocked on the Linux conversion L2 and the S-UT1 spike) is **not started**; see
D-UT-3 for why this shell exists first.

Evidence key: **BUILT-HERE** (run in the container, seen to pass) / **CI-LANE-WRITTEN** (workflow
exists, never run) / **CODE-ONLY** (written, never compiled or run) / **NEEDS-DEVICE-VALIDATION**.

## Layout

| Path | What | Evidence |
| --- | --- | --- |
| `qml/js/HostBridge.js` | Pure logic: URL parse, SSRF courtesy guard (same vectors as `web/api/*.js`), console-line wire format, feed/page handler, deadline tracker. QML `.pragma library`; runs under node too | **BUILT-HERE**: `node --test`, 15 tests in `tests/` pass |
| `shim/ut-shim.js` | Page side: answers `/api/feed` and `/api/page` over the bridge; XHR fallback for relative reads on `file:` (Chromium `fetch` refuses `file:`) | **BUILT-HERE**: node chain tests; and loaded by real QtWebEngine in the smoke below |
| `qml/Main.qml` | `WebEngineView` + one injected script + `XMLHttpRequest` transport + navigation policy (external links go to the system) | **BUILT-HERE**: `qmllint` clean; booted and bridge round-tripped under desktop Qt 5.15 + Xvfb (`tests/smoke.sh`). **Never run on a phone** |
| `scripts/stage.sh` | Bundles the reader's ES-module graph into one classic script (Chromium blocks `type="module"` from `file:`), rewrites `index.html`, assembles `build/payload/` | **BUILT-HERE** |
| `CMakeLists.txt`, `clickable.yaml`, `click/` | Click layout: manifest template, AppArmor (`networking`, `webview` only), desktop file | **BUILT-HERE**: `cmake --install`, then a real `.click` via Ubuntu's `click build` |
| `scripts/check-click.py` | Policy lint (groups, identity, hooks, telemetry/secret tripwires) | **BUILT-HERE**, incl. a negative case |
| `scripts/build-click.sh` | stage → cmake install → lint → `click build` → `build/out/*.click` (unsigned) | **BUILT-HERE** |
| `../.github/workflows/ubuntu-touch.yml` | Job `click` (tests, lint, build, smoke, draft-release attach) and advisory `clickable-review` (Clickable in Docker + click-review) | **CI-LANE-WRITTEN** |
| `OWNER_VERIFY.md` | The device checklist | NEEDS-DEVICE-VALIDATION |

## How it works

1. `scripts/stage.sh` copies `web/` (minus `api/`, tests, deploy config), bundles `js/app.js` with
   esbuild 0.24.0 into `app.bundle.js`, and builds `shims/nooz-shims.js` = `ut-shim.js` +
   Readability.js (Apache-2.0, licence shipped) + `apple/Nooz/Resources/ios-shim.js`
   (the single source of the article-extraction code; do not fork it).
2. `Main.qml` loads `www/index.html` from the click (`file:`) in a `WebEngineView`, with the one
   script injected at document creation in the main world.
3. The reader calls `fetch('/api/feed?url=…')`. The shim logs `NOOZ-HOST/1 {"id","op","url"}`;
   `Main.qml` catches it (`onJavaScriptConsoleMessage`), `HostBridge.handle` validates the target
   (https only, no userinfo, no internal addresses) and QML's `XMLHttpRequest` fetches it:
   no CORS, **no proxy hop through anyone's server** (the hosted reader's Vercel proxy sees every
   feed URL; this does not). The reply returns via `runJavaScript` as one double-encoded string.
4. `/api/article` is answered in-page by Readability over a `DOMParser` document, fed by
   `/api/page` (same bridge, 3 MB cap, 8 s). Same JSON as `web/api/article.js`.
5. Links that leave the reader open in the system browser. The only hosts native code contacts are
   the feed and article URLs the reader asks for. Cookies are not persisted; no HTTP cache.
   Article images load directly from their publishers' hosts, exactly as in the hosted reader.

## Decisions (UT-local, numbered so the owner can overrule)

- **D-UT-1 Thin client, labelled as one.** No Flash, Cast, Lens, translation, BYOK (plan 6.2). No
  keys are stored, so owner ruling "app-private file allowed" for UT keys is not exercised.
- **D-UT-2 https only.** Plain `http://` feeds are refused ("Unsupported protocol"), same as the
  iOS shell (D-iOS-1). A few old feeds will not load. Reversible per-domain.
- **D-UT-3 Shell before the QML-over-JVM core.** The plan's primary shape needs the Linux
  conversion (L2), S-UT1 (a jlinked JVM inside a click, never run) and a UT 24.04 device. The
  plan itself lists a "QML WebView shell with a native fetch bridge" as the cheaper shape; this
  is that. It ships the reader and loom as they are, unmodified, so it cannot drift from `web/`.
  It does not replace U1 to U4. Record: NQ-1 (shape and proxy posture) is answered here by
  **native fetch, no proxy**, which is the stricter privacy posture; the owner can still rule.
- **D-UT-4 `QTWEBENGINE_DISABLE_SANDBOX=1` in the `.desktop` Exec.** Chromium's own sandbox does
  not start inside Ubuntu Touch's AppArmor confinement for third-party apps, and webview apps
  commonly set this. **Unverified here and a real trade-off**: the renderer then relies on AppArmor
  alone. On device, try without it first (`OWNER_VERIFY.md` step 3); drop it if the app starts.
- **D-UT-5 Click name `nooz.asoc` is provisional.** One place: `config/identity.env`. Plan rule
  R11 wants a NAMES.md row first (NAMES.md is outside this repo). Maintainer is a placeholder
  (`OWNER-FILL`); click-review will flag it.
- **D-UT-6 Framework `ubuntu-sdk-20.04`**, because the owner's OnePlus 6 reads as 20.04-only
  (OQ-37). The 24.04 framework string and the plan's 26.04 canary lane are not done.
- **D-UT-7 No Lomiri.Components.** Plain QtQuick + QtWebEngine, so the shell also runs under
  desktop `qmlscene` (that is how it is smoke-tested). The cost: no Suru styling, no native
  header; the reader's own chrome (Hyle fonts, its tabs) is the UI.

## Known gaps (code not written, not "unverified")

- **File exchange.** OPML import/export and the clipping image export use a file input and a
  download; neither `onFileDialogRequested` nor a download handler (Content Hub) exists, and
  `content_exchange` is deliberately **not** in the AppArmor policy until it does. Expect these
  to fail on device. Adding them is the next piece of work.
- **No background fetch** (confined apps are suspended): the loom counts what flowed past while
  Nooz was open (plan NQ-5). The in-app copy for that is web copy, not reworded for UT (owner).
- **`finalUrl` after redirects** is the requested URL (QML's XHR cannot report the final one), so
  relative links in a redirected article may resolve against the pre-redirect host.
- **Size caps are post-hoc** for QML's buffered XHR (it cannot abort mid-body): a very large
  response is held in memory before being refused or truncated.
- **DNS rebinding** is not detected (shared with the server guards).
- No UT keyboard/back-gesture handling, no `Qt.application.state` handling, no QML tests under a
  UT image, no 24.04 canary. Locale: no new UI strings were added (the shell has no chrome of its
  own), so the 30-locale i18n gate is untouched.

## Telemetry

None. No analytics or crash SDK; no network call of our own beyond the feed and article URLs the
reader is asked to open. `scripts/check-click.py` greps the shipped files for known telemetry
hosts as a tripwire (not a proof). Nothing is signed here, no signing material or secret exists
in the repo or the workflow; the OpenStore signs on upload.

## Build

```
cd web && npm ci && cd ..
bash ubuntu-touch/scripts/build-click.sh          # needs node 20, cmake, Ubuntu `click`
node --test ubuntu-touch/tests/*.test.mjs         # pure logic + page chain
bash ubuntu-touch/tests/smoke.sh                  # needs xvfb, mesa, qmlscene, qml-module-qtwebengine
# on a Mac/Linux with Docker + a phone: cd ubuntu-touch && clickable build --arch all && clickable install
```
