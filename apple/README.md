# apple/ — Nooz for iOS and iPadOS

**Read this first: what this is and is not.** This is **the Nooz web reader (`web/`), packaged as a
native iOS app**, with its two server helpers replaced by native code. It is **not** the Android app
ported. There is no Room store, no on-device llama.cpp or Kokoro, no Lens, no home-screen widgets, no
background fetch. The plan's real native route (Compose Multiplatform iOS over a `commonMain`
`:core:model`, `PORTING_PLAN.md` section 6.3, I1 to I5, about 12 engineer-weeks and a Mac) is **not
started**. This shell is the honest first step the owner's 2026-11-17 request ("iOS builds on GitHub
CI") allows from a Linux container, and it is labelled a **companion shell, not a port of the Android
app**, wherever it is described.

Evidence key: **BUILT-HERE** (run in the container and seen to pass) / **CI-LANE-WRITTEN** (workflow
exists, has not run) / **CODE-ONLY** (written, never compiled) / **NEEDS-DEVICE-VALIDATION**.

## Layout

| Path | What | Evidence |
| --- | --- | --- |
| `NoozCore/` | Swift package, Foundation only: `HostGuard` (SSRF courtesy guard, same vectors as `web/api/*.js`), `RequestRoute` (what a `nooz://app/...` request means), `AssetPath` (traversal-safe path and MIME), `PageEnvelope` (JSON for the shim, 3 MB cap) | **BUILT-HERE**: `swift test` on Linux, 9 tests, 0 failures (Swift 6.0.3, x86_64). Not yet run on macOS |
| `Nooz/Sources/` | SwiftUI app, `WKWebView` host, `WKURLSchemeHandler` (assets, native `/api/feed`, native `/api/page`) | **CODE-ONLY**: needs UIKit/WebKit, cannot compile here |
| `Nooz/Resources/ios-shim.js` | In-page replacement for the Readability half of `web/api/article.js` | **BUILT-HERE**: parity tests against the real `web/api/article.js` (node, linkedom + Readability) pass; the WebKit runtime is **NEEDS-DEVICE-VALIDATION** |
| `shared/host-guard-vectors.json` | One vector list, three consumers: the JS guards, `HostGuard.swift` | **BUILT-HERE** (node and Swift both pass) |
| `project.yml`, `Config/Identity.xcconfig` | XcodeGen spec and the single identity file | **CODE-ONLY** (YAML parses; XcodeGen has not run) |
| `scripts/` | `stage-web.sh` (BUILT-HERE), `make-appicon.py` (BUILT-HERE), `simulator-smoke.sh`, `verify-archive.sh` (`bash -n` only; macOS only) | see rows |
| `Nooz/PrivacyInfo.xcprivacy` | Truthful empty manifest | **CODE-ONLY**: owner re-checks with Xcode's privacy report |
| `../.github/workflows/ios.yml` | Linux logic lane + macOS simulator/archive lane | **CI-LANE-WRITTEN** |

## How it works

1. `scripts/stage-web.sh` copies `web/` (minus `api/`, tests, `node_modules`, deploy config) into
   `Nooz/Resources/web/` and Readability.js (Apache-2.0, with its licence) from `web/node_modules`.
2. The app serves that folder to a `WKWebView` through a custom scheme, `nooz://app/`. The page is the
   unchanged web reader; its IndexedDB store (`web/js/db.js`) is the app's data store. Nothing is sent
   anywhere about the user.
3. `web/js/feeds.js` calls `/api/feed?url=` (a CORS proxy server in the hosted build). In the shell the
   scheme handler answers it with a native `URLSession` GET: no CORS problem and **no proxy hop through
   anyone's server**, which is a privacy gain over the hosted reader.
4. `/api/article?url=` (server-side Readability) is answered in-page: the shim asks the handler for the
   raw page (`/api/page`, 3 MB cap, 8 s timeout) and runs Readability itself, returning the same JSON
   `web/api/article.js` does.
5. Links that leave the reader open in the system browser. The only hosts native code contacts are the
   feed and article URLs the reader is asked to open. Ephemeral session: no cookies, no cache.

## Decisions (iOS-local, numbered so the owner can overrule)

- **D-iOS-1 No ATS exceptions.** Plain `http://` feeds are refused ("Upstream unreachable"). The
  hosted proxy fetched them server-side; the shell will not weaken transport security app-wide to do
  so. Cost: a few old feeds will not load. Reversible with a per-domain exception.
- **D-iOS-2 No background fetch.** `BGAppRefreshTask` is opportunistic and cannot honestly back the
  "what flowed past" claim (plan NQ-5). The shell fetches only while open; copy must say so (owner).
- **D-iOS-3 Shell over the web reader first.** Chosen because the plan's native route needs a Mac and
  12 weeks, while the web reader exists, is tested (46 node tests) and is already used on the owner's
  iPad (D33). This shell is the packaging step; it does not replace I1 to I5.
- **D-iOS-4 Bundle id `com.asoc.nooz`** in `Config/Identity.xcconfig` only (owner ruling 2026-11-17).

## Telemetry and Apple's reports

No analytics or crash SDK, no network call of our own. The one approved exception (PT:D-BD,
2026-10-14): **crash reports that Apple itself collects for TestFlight and App Store builds**; the app
does not transmit or read them. Naming it here satisfies the "README must name it" condition; the store
listing and an about/privacy page must name it too (owner, before submission).

## Build (macOS)

```bash
( cd web && npm ci )
bash apple/scripts/stage-web.sh
( cd apple && brew install xcodegen && xcodegen generate )
xcodebuild build -project apple/Nooz.xcodeproj -scheme Nooz -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO
```
CI does exactly this (`ios.yml`). Logic checks that run anywhere:
`node --test apple/tests/*.test.mjs` and `( cd apple/NoozCore && swift test )`.

## Signing and release: owner actions

Not in the repo and not in `ios.yml`: Apple Developer Program enrolment, certificates and profiles,
App Store Connect record, TestFlight. Signing material goes in a GitHub *environment* with required
reviewers, applied by a separate signed lane the owner adds once enrolled. See `OWNER_VERIFY.md`.
