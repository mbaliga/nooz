# Nooz — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). This file is owned by the lead planning session; a platform
> track updates only its own §4 row and appends to its own entries in `STATE.md`.
> The licence of this repository is RESERVED (`LICENSE.RESERVED`). This plan neither selects nor
> suggests one, and assumes **direct downloads only** until the owner rules (OQ-12).

## 0. Evidence labels (never dropped)

`LAB` · `CI (hosted VM) evidence` · `EMULATOR EVIDENCE` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` ·
`SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` · `SYNTHETIC` · `CI-ONLY / NOT RUN` ·
`NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV) · `PLAN` · `NOT-APPLICABLE (<reason>)` ·
`CONTAINER-BUILD-ONLY` (the planning container: JVM x86_64 compile and tests, nothing else) · `BROWSER-HEADLESS`.

Nooz's own rule applies throughout (`STATE.md`, `README.md`): this build sandbox has no device or emulator, so
audio output, model quality, gestures and rendering on any target are owner-verified only. Every estimate below
is an estimate; every unknown is marked unknown.

## 1. What this repo is, in porting terms

**Product.** Nooz is a news reader whose subject is omission: it shows the shape of what flowed past from the
user's own chosen sources against what they actually read, over time (the day loom, contrast and cross-section
metrics), with a typography-first reader, clippings, a non-generative dictionary lens, a guarded affect-span
"defuse" lens, on-device digest (Nooz Flash, llama.cpp) and narration (Nooz Cast, Kokoro on ONNX), 30 interface
locales and a verified feed starter set (`README.md`, `STATE.md`). It also hosts two shared data assets,
`ai-catalogue/` (the constellation's model and free-tier catalogue, read by Aarso over a raw GitHub URL) and
`catalogue/` (the news-source sentry mirror), and a second client, the vanilla-JS web reader in `web/`.

**State.** Shipping. `app/build.gradle.kts`: `versionCode 4`, `versionName "0.3.1"`, `applicationId`
`dev.asystemofcells.nooz` (registered in Play Console, `STATE.md` §RESERVED); a real Room migration 3 to 4
protects shipped readers' data (D37). Which Play track the listing is on is not verifiable from the repo. The web
reader is live at asystemofcells.com/nooz/read and is used on iPadOS by the owner (D33). The default branch is
`claude/app-build-d1f9s6` (program §9, GitHub API 2026-10-06); `origin` also has a `main` (`git ls-remote --heads origin`,
2026-10-06) whose relation to it was not examined. Current targets: Android
(API 31+, arm64-v8a only; flavors `foss` and `full`) and the web reader. No KMP, Compose Multiplatform, desktop,
iOS, Windows or Ubuntu Touch work exists anywhere in the build files, README or STATE (grep-verified by the
profile reader on 2026-10-06).

**Stack.**

| Layer | Today (`gradle/libs.versions.toml`, module build files) |
|---|---|
| Languages | Kotlin 2.0.21 (Android plus one pure `kotlin("jvm")` module); C++17/C11 (JNI bridge, vendored llama.cpp); JavaScript ES modules (web, no bundler) and CommonJS Vercel functions; Python 3 stdlib (i18n generator, two sentries) |
| UI | Jetpack Compose (BOM 2024.12.01), Material3, custom Canvas visualisations, no layout XML; navigation is in-memory state in `MainActivity`; web: hand-rolled DOM views and a hash router |
| Build | Gradle 8.14.3, AGP 8.7.3, KSP 2.0.21-1.0.28, JDK 17 toolchain, CMake 3.22.1 and NDK 27.2.12479018 for `:core:inference`; web: npm, Node 20, Vercel |
| Libraries | Room 2.6.1 (with an `@Fts4` unicode61 index), DataStore 1.1.1, WorkManager 2.10.0, kotlinx.serialization 1.7.3, coroutines 1.9.0, jsoup 1.18.3, Coil 2.7.0, onnxruntime-android 1.27.0, JUnit 4 and Robolectric 4.14.1 |
| Native | llama.cpp at pinned commit `360e1349f0009c5ad99d21e3c4546b707addc68a` via CMake FetchContent (`GGML_NATIVE`, `LLAMAFILE`, `OPENMP` off); onnxruntime prebuilt AAR; ten bundled TTFs (Hyle Grotesk Classic/Plus, Hyle Print, PT Serif); Kokoro lexicon asset |
| Design | Hyle tokens mirrored by value in `core/design/.../Tokens.kt`; no Gradle dependency on Hyle (`settings.gradle.kts` has no `includeBuild`) |

**Size** (measured 2026-10-06 from the checkout root; the profile's larger totals counted all languages and generated resources):

```
find . -name '*.kt' -not -path '*/build/*' -not -path '*/src/test/*' -not -path '*/src/androidTest/*' | wc -l          # 154 files
find . -name '*.kt' -not -path '*/build/*' -not -path '*/src/test/*' -not -path '*/src/androidTest/*' -print0 | xargs -0 cat | wc -l   # 22,971 lines
find . -name '*.kt' -not -path '*/build/*' \( -path '*/src/test/*' -o -path '*/src/androidTest/*' \) | wc -l           # 42 test files, 4,146 lines
grep -rE '^\s*@Test' --include=*.kt . | grep -v /build/ | wc -l                                                      # 308 tests (215 in :core:model)
find core/inference/src/main/cpp -type f | xargs wc -l | tail -1                                                      # 523 lines C++/CMake
find web/js web/api -name '*.js' | xargs cat | wc -l ; wc -l web/style.css                                            # 7,937 JS (1,013 generated), 2,707 CSS
git ls-files | wc -l                                                                                                  # 447 tracked files
```

## 2. Portable core vs platform-bound layers

Lines are `src/main` (all non-test source sets) / `src/test`; "android.\* files" counts files with an `import android.` line.

| Module / dir | Role | Portability | Lines (main / test) | Notes |
|---|---|---|---|---|
| `core/model` (`:core:model`) | Feed parsing (XXE-hardened), simhash dedup, evidence-carrying classifier, river/loom layout and supply-vs-drift, lens guard, CVD palette, OPML, autodiscovery, starters, source health, search, dictionary catalogues, generated locale tables | **pure Kotlin/JVM** (`kotlin("jvm")`), 0 android.\* files | 5,359 / 2,437 (27 test files, 215 tests) | Runs unchanged on any JVM host. Five files block a `commonMain` move: `FeedParser.kt`, `FeedAutodiscovery.kt` (javax.xml, org.w3c.dom), `CanonicalUrl.kt`, `FeedUrls.kt` (java.net.URI), `WeekBucketing.kt` (java.time; java.time is also in `FeedUrls.kt` and `FeedParser.kt`). CI already runs it (`ci.yml` job `core-tests`, `./gradlew :core:model:test`, no Android setup step) |
| `core/data` | Room (`@Fts4` at `Entities.kt:132`, `MIGRATION_3_4`), 6 DataStore stores, `HttpClient` (HttpURLConnection, gzip, conditional GET), jsoup `ArticleExtractor`, file LRU cache, `DataExporter`, WorkManager fetch, model-catalogue repository | Android-bound, about half portable: 18 of 36 files have no android/androidx import | 2,963 / 1,051 (9 test files) | Plain JVM: HttpClient, FeedProbe, ArticleExtractor, FullTextCache, `mapping/*`, DataExporter, six repositories that depend only on DAO interfaces. Bound: `db/*`, `work/*`, six repositories taking `Context` |
| `core/inference` | `InferenceProvider`/`InferenceRouter`/`TtsProvider`; llama.cpp provider and JNI engine, Kokoro TTS (phonemizer, lexicon, vocab, WAV writer), BYOK OpenAI-compatible provider, Urbana discovery stub, ML Kit stub (`full` only), `ModelManager` | Android-bound, mostly portable: 8 files import android.\* (all `Context` or `Uri`) | 1,540 / 167 (2 test files) plus 523 C++/CMake | Router, prompt templates, BYOK, ModelManager and the Kokoro pipeline are plain JVM. **The Kokoro files have no dedicated tests** (only `InferenceRouterTest`, `ModelManagerTest` exist) |
| `core/design` | Tokens, `RiverTheme`, `Type.kt` font binding, PaperGrain, GlobeCanvas, bars and search, register-correct copy; hosts the generated 30-locale `strings.xml` | Compose-only, 0 android.\* files; bound by `R.font`, `stringResource`, `painterResource` and the `res/` tree | 1,211 / 255 | Moves to compose-resources once the generator emits a second resource tree |
| `feature/river` | Day loom canvas, contrast and cross-section panels, `LoomViewModel` | Compose-only, 0 android.\* files | 2,086 / 236 (2 test files) | The most directly portable UI module. Carries the RTL canvas-coordinate lesson (`STATE.md`) |
| `feature/lens` | Tap-to-defuse and definition sheets, `LensViewModel` | Compose-only, 0 android.\* files | 808 / 0 | Flavored because it depends on `:core:inference` |
| `feature/sources` | Edit screen, autodiscovery, starters, OPML import/export | Compose UI; `SourcesViewModel` has no Android SDK imports | 1,078 / 0 | SAF `OpenDocument`/`CreateDocument` in `SourcesUtilities.kt`; `ACTION_VIEW` in `EditScreen.kt` |
| `feature/reader` | Stand, immersive reader, two-pane (`TWO_PANE_MIN_WIDTH = 840.dp`), clippings, Flash/Cast card, halftone images, newspaper share | Android-bound (5 android.\* files); `ReaderViewModel` has no SDK imports | 4,536 / 0 | Heaviest platform surface: `NewspaperShare.kt`, `FlashCard.kt`, `FeedImage.kt`, `BackHandler` |
| `app` | Composition root (`AppContainer`), `MainActivity`, onboarding, settings, model chooser, `AppLocale`, `crash/CrashRecovery.kt` | Android shell (6 android.\* files); replaced per platform, not ported | 3,390 / 0 | Edge-to-edge, `WindowCompat`, `LocaleManager`, FileProvider, `locales_config.xml` |
| `web` | Second client: views, `feeds.js`, IndexedDB, 30 locales, classifier port, canvas clipping share; Vercel `api/feed.js` (CORS proxy, SSRF guard) and `api/article.js` (Readability) | web | 7,937 JS (1,013 generated) + 2,707 CSS; `web/tests` (6 files, node:test, linkedom, Playwright axe) | Hard dependency on same-origin `/api/feed` and `/api/article`. No service worker, so no offline mode. The live deployment keeps its own copy of the API functions in another repo (`web/api/feed.js` header comment) |
| `ai-catalogue`, `catalogue` | Shared model and free-tier data with a weekly sentry; news-source mirror | data | n/a | Consumer contract in `ai-catalogue/README.md` is binding cross-repo. Unrelated to each other |
| `i18n`, `tools/i18n/generate.py` | One source (30 locales, 11 lexicons) generating `strings.xml`, `locales_config.xml`, `LocaleCoverage.kt`, `TopicLexiconL10n.kt`, `web/i18n/*`, `web/js/topics-l10n.js`; `--check` runs in CI | Python 3 stdlib | n/a | Already multi-target by design (D47, D48, D50): a port adds outputs, not a new source |

**Platform-bound APIs that matter.**

| API | Where | Porting impact |
|---|---|---|
| Room 2.6.1, `@Fts4(unicode61)` index, exported schemas v3/v4, real `MIGRATION_3_4` | `core/data/.../db/*`, `core/data/schemas/`, `RiverDatabaseMigrationTest.kt` (Robolectric, pins real MATCH semantics) | Needs Room-KMP or an alternative off Android. **Whether Room-KMP supports `@Fts4` with unicode61 is unverified.** FTS parity for Indic scripts is a correctness requirement (D37); shipped Android databases must keep upgrading |
| WorkManager periodic fetch (15-min floor, network constraint, ~60-day purge) | `core/data/.../work/*`, `RiverApplication.kt` | No equivalent off Android. Desktop: in-process coroutine scheduler while open. iOS: `BGAppRefreshTask`, opportunistic. Ubuntu Touch: none. The "what flowed past" claim must be restated per platform (NQ-5) |
| JNI bridge to llama.cpp, `System.loadLibrary("nooz-llama")`, links `android/log`, arm64-v8a only | `core/inference/src/main/cpp/*`, `LlamaCppEngine.kt` | Desktop: rebuild the same CMake project per host (drop `android/log`, `GGML_NATIVE` stays off). iOS: no JNI, so a C ABI shim for cinterop. Model downloads are 1.1 to 9.1 GB GGUF from `ai-catalogue` |
| onnxruntime-android (Kokoro-82M) and `Context.assets` lexicon | `LocalKokoroTtsProvider.kt`, `local/kokoro/*` | Desktop: the JVM artifact has the same Java API. iOS: onnxruntime ObjC/C via cinterop. Asset loading needs expect/actual |
| `TextToSpeech`, `MediaPlayer` | `FlashCard.kt` | No JVM system TTS. Desktop: Kokoro WAV through `javax.sound`. iOS: `AVSpeechSynthesizer`, `AVAudioPlayer`. Ubuntu Touch: none from the web reader |
| `android.graphics` clipping renderer, FileProvider, `ACTION_SEND` | `NewspaperShare.kt`, `res/xml/file_paths.xml` | Rewrite with Compose `ImageBitmap`/`TextMeasurer`; share becomes expect/actual (save PNG or clipboard, `UIActivityViewController`, content-hub). `web/js/newspaperShare.js` is a reference |
| Coil 2.7 and `Bitmap.createScaledBitmap` halftone | `FeedImage.kt` | Coil 3 is multiplatform with a near-identical API; the halftone must be redone on `ImageBitmap`. Low risk |
| SAF file pickers; `ACTION_VIEW` (7 call sites in 5 files) | `SourcesUtilities.kt`, reader and sources screens | Small expect/actual set; OPML logic is already pure (`Opml.kt`) |
| `UrbanaProvider` ContentProvider discovery | `core/inference/.../urbana/UrbanaProvider.kt` | Android IPC, honestly stubbed. Desktop replacement is the asom localhost client (F4, gated, OQ-7d); iOS has no daemon: local plus BYOK only. Cloud provenance marking must survive |
| BYOK key in plain private `SharedPreferences`; `LocaleManager` | `ByokConfig.kt` (Keystore is a logged follow-up), `AppLocale.kt` | A secret store per platform (OQ-22) and an in-app locale switch. Note `README.md` lists Android Keystore in the stack while `ByokConfig.kt` documents plain prefs as the stopgap |
| `R.font`, generated `values-b+<tag>/strings.xml`, adaptive icons | `core/design/.../Type.kt`, `res/` | compose-resources with a third generator output; per-key English fallback (D47) must survive |
| System font fallback for non-Latin scripts | D46: none of the ten bundled faces carries Indic, Arabic or CJK glyphs; Android's fallback chain covers them | **Outside Android, fallback depends on the host's fonts and is unverified** for 30 locales including eleven Indic/Urdu scripts and RTL (NQ-11) |
| `ComponentActivity`, edge-to-edge, `BackHandler`, window brightness gesture | `MainActivity.kt`, `ReaderScreen.kt`, `ReaderGestures.kt` | Replaced by a desktop `main()` and an iOS host. The profile read `BackHandler` as needing a newer CMP than the current pins (unverified here) |
| `androidx.lifecycle` `ViewModel`/`viewModelScope` (the four ViewModels) and `collectAsStateWithLifecycle` (14 files); `Context.preferencesDataStore` delegates (four repositories) | `ReaderViewModel`, `LoomViewModel`, `SourcesViewModel`, `LensViewModel`; `core/data/.../repo/*Repository.kt` | No Android SDK import, but not platform-free either: whether these resolve off Android depends on the lifecycle and DataStore versions the OQ-17 pin allows (unverified here). The DataStore delegates become path-based factories per platform |
| Gesture-defined IA from the owner's Figma mocks (D6, D20, D21, D30) | `ReaderScreen.kt`, `ReaderGestures.kt`, `MainActivity.kt` | Not an API, but the largest desktop design question: no pointer or keyboard equivalents exist (NQ-2) |
| `CrashRecovery` (local report only) | `app/.../crash/CrashRecovery.kt` | Needs the KMP crash-recovery (F2); never transmits on any platform |
| Robolectric Compose a11y tests, `MigrationTestHelper`; `unitTests` and `verifyI18n` Gradle scripts | `gradle/verification/unit-tests.gradle.kts`, `gradle/i18n/verify-i18n.gradle.kts` | `unitTests` selects `test*DebugUnitTest` else `test`, so a KMP module's `jvmTest` would be missed or fail loudly; `verifyI18n` scans fixed `src/main` roots. Both must learn the new source sets or the ratchets silently stop binding (D47, D52) |

## 3. Binding rules this port must not break

Repo rules (source in parentheses):

- **No telemetry, analytics or transmitting crash reporters, ever.** Nothing leaves the device except the user's own fetches to their own chosen sources (`README.md` Refusals; `CrashRecovery` writes locally). Routing fetches through a server, as the web reader's `/api/feed` does, is a posture change the owner must approve (NQ-1).
- **No push notifications, no engagement ranking, no streaks or gamification; the river has banks** (`README.md`).
- **Denominator honesty:** never claim to show all the news; read events are coarse buckets, never durations (`README.md`, `STATE.md` P3).
- **Pure logic stays in `:core:model`/`:core:inference` with no Android, and the `core-tests` job stays SDK-free** (D2, `ci.yml`).
- **Licence is RESERVED.** Do not select, suggest or infer one; no SPDX headers; no `licenses` block in store metadata; F-Droid for `foss` waits on the owner (`LICENSE.RESERVED`). Channels that require a declared licence are therefore blocked, and the program's SPDX-header option (F5) stays off for this repo.
- **Owner-decided and closed:** the name Nooz (D5), the icon (D6), `applicationId` `dev.asystemofcells.nooz`; `riverwip.packageBase = xyz.mdhv.riverwip` must not be renamed (`gradle.properties`, `STATE.md` §RESERVED). No Marvel, Loki or TVA references; Hyle Deco is never used; the wordmark is PT Serif Regular at -2% (D7).
- **Colour is never the only signal.** Provenance is fixed: warm radium `#C7EF9E` on-device, cyan `#35E0FF` cloud, always with a non-colour channel; `PaletteCvdTest` is build-failing (D1; the CVD row of the phase table in `STATE.md`). Any native chrome a port adds (menu bar, SwiftUI shell, QML pages) inherits this (program I-3).
- **The owner's Figma mocks define the IA:** no bottom navigation, lift-and-part edge drag, pull-down loom; Compose over platform themes (D6, D21, D34, D47). Ad-libbed mock content is never copied.
- **Inference is never faked:** a stub says so in the UI (D3). Model downloads come only from `ai-catalogue/models.json`; only `policySafe` `LLM_GGUF` entries are offered for Flash (D18). The Flash router is on-device first with BYOK as the only fallback, never Urbana or ML Kit (D20).
- **BYOK:** the key never leaves the device, is never logged, never enters the data export; cloud results always carry CLOUD provenance (`ByokConfig.kt`, `DataExporter`).
- **`ai-catalogue` honesty rules and consumer contract:** never fabricate a `downloadUrl` or `sha256`; consumers fetch only on explicit user action; Aarso reads the files by raw URL, so they are not moved or renamed (`ai-catalogue/README.md`, D13, D16). `ai-catalogue/` is not `catalogue/`.
- **Generated files are never hand-edited:** `strings.xml`, `locales_config.xml`, `LocaleCoverage.kt`, `TopicLexiconL10n.kt`, `web/i18n/*`, `web/js/topics-l10n.js`. `generate.py --check` runs in CI; the `verifyI18n` allowlist may only shrink (D47, D58, D59).
- **Flavor rule:** `foss` carries zero proprietary dependencies; anything depending on the flavored `:core:inference` chain declares the `distribution` dimension (`STATE.md` CI-caught issues). Every module's tests must be discoverable by `unitTests` (D52). Room schemas are exported and every bump ships a tested migration (D37).
- **Environment honesty:** device-only behaviour is reported as unverified, never claimed (`STATE.md` throughout).
- **The build brief** that `STATE.md` cites (brief §0, §3, §4, §5, §9, §10, P6, P7) is not in this repo; this plan cannot be checked against it (NQ-9).

Program rules that bind the work below (`Personal-Tracker/PORTING_PROGRAM.md` §3): disjoint directories (R1), the existing gate stays green (R2), new CI workflow files only and a Windows path lint before any Windows lane (R3), pure core first (R4), scaffolds bind, serve and sign nothing (R5), nothing released and no PR artefacts, with release binaries going to draft GitHub Releases because Actions artifact storage is exhausted (R6; this repo's `cleanup-artifacts.yml` runs every six hours for the same reason), no per-platform identifier before a NAMES.md row (R11), and reframes labelled as such (R12).

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe | "Nooz thin client for Ubuntu Touch" (R12), never called a port. Primary shape: QML (Lomiri.Components) pages over a headless jlinked JVM core (F7, wave P-UT b). Cheaper alternative: the web reader as a click (webapp-container, or a QML WebView shell with a native fetch bridge). Reader, loom, sources and clippings only; no Flash, Cast, dictionary lens, translation or BYOK; fetch on open | OQ-1 (no UT device on record; S-UT1 unrun); no JVM in a click until the jlink recipe is proven; the web reader's `/api` proxy dependency and its privacy posture (NQ-1); no background execution (NQ-5); Qt 5.15 end of road; OpenStore reportedly wants a declared licence (the profile's reading, unverified here; OQ-12), a sideloaded `.click` does not; click name needs a NAMES.md row (OQ-25) | 5 (the web-reader click subset is about 2) | PLAN |
| Linux desktop | straight | Kotlin Multiplatform plus Compose Multiplatform desktop on the JVM: `:core:model` runs unchanged; the Compose and data modules are converted in place (convert, then lift) with `androidTarget` plus `jvm()`; Room-KMP or a DAO-compatible alternative per spike K1; in-process fetch scheduler; about 15 expect/actual points; llama.cpp rebuilt per host; onnxruntime JVM for Cast; desktop shell with pointer and keyboard equivalents. Packaged with `jpackage` as a tarball with `install.sh`; deb/rpm only after OQ-12 settles the licence-metadata field (L4) | OQ-17 pins; Room-KMP `@Fts4` unverified (K1); pointer/keyboard IA has no owner mocks (NQ-2); script fallback unverified off Android (NQ-11); ratchet scripts must learn new source sets; Flathub, Snap and AUR need the licence (OQ-12), direct downloads do not; installer size with native engines | 8 (reader-only parity about 5; Flash, Cast and asom discovery about 3) | PLAN |
| iOS / iPadOS | moderate | Compose Multiplatform iOS in a thin SwiftUI shell on the same conversion, iPadOS first. `:core:model` moves to `commonMain` (kotlinx-datetime, a KMP XML parser with the XXE hardening kept, KMP URL handling); Ksoup, Ktor or NSURLSession, Room-KMP native, Keychain, `BGAppRefreshTask`, llama.cpp as an XCFramework behind a C ABI shim, onnxruntime iOS for Cast. The hosted web reader (home-screen install, D33) is the interim iPadOS surface | OQ-2 (Apple Developer Program, delivery route); OQ-5 (no Mac on record); the five JVM-API files, 27 JUnit 4 test files and `:core:data` must reach `commonMain`; no JNI; no guaranteed background fetch (NQ-5); no asom daemon; edge-swipe-back collides with the 24dp edge zone; App Review of a downloadable-model feature is unverified | 12 (assumes the Linux conversion landed) | PLAN |
| macOS | straight | The Linux JVM build packaged as a `.dmg` with `jpackage` on a hosted macOS runner; hardened runtime with the minimum entitlement set; llama.cpp for macos-arm64 (x86_64 best effort); menu bar and Cmd shortcuts. JVM only: Compose has no stable native macOS head | OQ-3 (Developer ID, notarisation); OQ-5 (no Mac, so every device gate is NOV); everything listed for Linux; Mac App Store not planned (OQ-4) | 3 | PLAN |
| Windows | straight | The Linux JVM build packaged as an MSI with `jpackage`/WiX on a hosted Windows runner; llama.cpp for win-x64 (MSVC); onnxruntime JVM; DPAPI or labelled file tier for BYOK; x64 first | OQ-3 (signing route; unsigned until ruled); OQ-5 (the Dell's fate); everything listed for Linux; arm64 unverified; the R3 path lint and line-ending pinning must precede the lane | 3 | PLAN |

Effort sums to 31 engineer-weeks and overlaps: iOS, macOS, Windows and the QML click all assume the Linux conversion (L2) has landed. Estimates assume one engineer per track who knows the stack, spikes passing first time, and the owner's hardware for device sessions.

## 5. Tier and sequencing

**Tier A (flagship).** Nooz is a shipping, actively developed product with the constellation's cleanest port
shape: a deliberately Android-free pure-JVM core already gated on a bare JDK (D2); Compose-only visualisation,
lens and design modules; ViewModels free of Android SDK imports and a single manual-DI composition root; a multi-target i18n
generator; and an already-live second client. It is the program's cleanest Compose Multiplatform candidate. The
port is still real work: Room, WorkManager, JNI, ONNX, TTS and share form a platform layer, and the owner's
gesture IA needs pointer equivalents. The RESERVED licence gates distribution channels, not the work. This
matches the program's §5 row for nooz (tier A; Ubuntu Touch reframe 5w, Linux straight 8w, iOS moderate
12w, macOS 3w, Windows 3w; gates: licence RESERVED so direct downloads only (OQ-12), pins (OQ-17), catalogue
hosting unaffected).

**Catalogue hosting is unaffected.** No step in §6 edits, moves or renames `ai-catalogue/` or `catalogue/`, or
changes a `schemaVersion`. Consumers embed a branch name in the raw URL (`ai-catalogue/README.md`), so if the
owner later changes the default branch, those URLs must keep resolving; that is an owner step, not a porting step.

**Upstream first.** The web reader lives in `web/` here; asystemofcells only mirrors it. Any change to the web
reader for a port (step U0) lands here first and is mirrored afterwards.

**Build order is not validation order** (program §7). The owner receives device-installable artefacts in the
order Ubuntu Touch, Linux, iOS/iPadOS, macOS, Windows, but the cheapest build order is: foundation, then the
Linux JVM desktop build (the QML click's headless core, the DMG and the MSI are that same artefact), the Ubuntu
Touch subset alongside, then iPadOS, then macOS and Windows as packaging and signing waves.

| Program wave | What Nooz ships there | Build-entry (CI or simulator) | Repo-local gate before it starts |
|---|---|---|---|
| P-0 Foundation | Nothing user-facing. One dependency-free item may land at any time: the XXE regression test (K2), a plain `:core:model` test. Spike K0 follows OQ-17 | OQ-17 ruled; F1, F5, F6 homes approved | This repo's `ci.yml` is green on the default branch. Nothing is moved or converted before OQ-17 |
| P-UT a (web click) | Optional: step U-A, the web reader as a webapp click, in parallel with P-LX | F7's webapp template | NQ-1 ruled (shape and proxy posture); OQ-1 or an explicit CI-only waiver |
| P-UT b (JVM-cored QML click) | U1 to U4 | P-LX core green (L0 to L2); S-UT1 passed or waived; F7 | NQ-1; NQ-5 copy agreed |
| P-LX Linux desktop | L0 to L7 after the program's pre-wave proofs (Typewright pilot, Clavis arm64 and Flatpak); alongside csapp | F1, F5 (F2, F6 where consumed); OQ-17; K1 verdict recorded | `ci.yml` jobs `core-tests`, `web-tests`, `android` green at every conversion PR; NQ-2 ruled before L3 |
| P-iOS | I1 to I5, iPadOS first | The CMP-iOS recipe proven on Clavis in the simulator; F1 iOS targets; hosted macOS runner | OQ-2; OQ-5; L2 landed |
| P-mac | M0 to M3 | P-LX binaries; OQ-3 secrets for any signing lane | OQ-5 (device gates stay NOV without a Mac) |
| P-win | W0 to W3 | P-LX binaries; R3 path lint in place | OQ-3 (a signing route, or artefacts accepted unsigned) |

Nooz is a public repository, so hosted five-OS lanes cost nothing here (program OQ-20 concerns the private repos);
that does not relax R6: package lanes run on tags and manual dispatch, upload nothing to Actions artifact storage,
and write to draft GitHub Releases. `ci.yml` triggers `push` only on `main`, while the default branch is
`claude/app-build-d1f9s6`, so a push to the default branch does not run it (pull requests into any branch do). The
program's "package lanes on `main` or tags" rule is therefore applied as "tags or `workflow_dispatch`" until the
owner settles which branch is trunk (NQ-10).

## 6. Work breakdown

Every step is a pull request into the default branch through the repo's normal process; steps that add only
files under a new platform directory touch no existing build. New directories (R1): `desktop/` (the Compose
Desktop head, with `desktop/native/` for the host llama.cpp build), `packaging/{linux,macos,windows}/`, `apple/`
(the iOS head, with `apple/native/`) and `ubuntu-touch/`. New workflow files only (R3): `desktop-linux.yml`,
`desktop-macos.yml`, `desktop-windows.yml`, `ios.yml`, `ubuntu-touch.yml`, each SHA-pinned (the existing
workflows use tag pins), compile-and-test on pull requests, package on tags and dispatch. `ci.yml` is not edited.
KMP modules apply the Android plugin only when an SDK is present (F5's conditional inclusion, hnm's
`androidSdkAvailable()` precedent), so `jvmTest` runs on a bare JDK and D2's SDK-free job stays SDK-free.
Container note: this container compiles JVM x86_64 only; nothing below that needs Android, Apple, Windows or a
Clickable image can be verified here.

### 6.1 Linux desktop (the spine; 8 weeks)

| Step | What | Placement | Done when | Evidence | Wk |
|---|---|---|---|---|---|
| L0 | Desktop skeleton: a standalone Gradle build mapping `core/model` by directory, plus a compile-only Compose Desktop `main()`; no change to the root build | `desktop/` (own `settings.gradle.kts`), `desktop-linux.yml` | `:core:model:test` and the skeleton compile pass on a hosted ubuntu runner | CONTAINER-BUILD-ONLY, then CI (hosted VM) | 0.5 |
| L1 | Spikes. **K0:** do the OQ-17 pins build the existing Android project unchanged? **K1:** Room-KMP with `BundledSQLiteDriver` against the existing FTS cases (`@Fts4` unicode61 and the Latin-plus-Indic prefix case `ftsIndexFindsBothLatinAndIndicScriptsByPrefix` in `RiverDatabaseMigrationTest`), `MIGRATION_3_4`, exported-schema check; verdict is Room-KMP, or a desktop-only store implementing the DAO-facing interfaces while Android keeps Room (D37). **K2:** add a `:core:model` JVM test that feeds a DOCTYPE and external-entity payload to `FeedParser` (no such test exists today; grep of `core/model/src/test`), so any later parser swap has an oracle | Throwaway spike under `desktop/spikes/`; K2 is a normal PR in `core/model/src/test` | Verdicts written into `STATE.md` with real output; K2 green on `core-tests` | CI (hosted VM) | 0.5 |
| L2 | Convert, then lift, in this order: `:core:design` (compose-resources for the ten fonts, strings and illustrations; a third output in `tools/i18n/generate.py`, `--check` extended), `:feature:river`, `:feature:lens`, `:core:data` (HttpClient, extractor, cache, exporter and repositories to shared or `jvmMain`; store per K1; DataStore (the `Context` delegates become path-based factories); a scheduler abstraction whose Android actual keeps WorkManager's exact behaviour), `:core:inference` (Kokoro pipeline in `jvmMain`), `:feature:sources`, `:feature:reader`. First PR also teaches `unit-tests.gradle.kts` the `jvmTest` task and `verify-i18n.gradle.kts` the new source roots. Robolectric tests stay in the Android source set | In place in each module (`commonMain`, `androidMain`, `jvmMain`); root `settings.gradle.kts` gains `:desktop` in the PR where the head first needs a converted module | Per PR: Android output unchanged, `ci.yml` jobs green, `unitTests` runs the module's `jvmTest` and Android variants, `verifyI18n` reports the same count, `generate.py --check` passes | CI (hosted VM) | 2.0 |
| L3 | Platform layer and shell: expect/actual for open-URL (7 call sites), OPML file dialogs, clipping share (rewrite the renderer on `ImageBitmap`; save PNG or clipboard), secret store (F6, tier reported in Settings), in-app locale switch, crash directory (F2), app directories; desktop `main()`, window, menu, shortcuts, `ReaderScreenTwoPane` by default on wide windows; pointer and keyboard equivalents per NQ-2; a per-script rendering check for all 30 locales (NQ-11); brightness gesture becomes a no-op or an in-app dim. Until F2 exists the head installs no handler that writes outside the app data directory, and none that transmits | `desktop/`, `jvmMain` of converted modules | Every top-level screen reachable by keyboard and pointer; script check produces a per-locale sheet | CONTAINER-BUILD-ONLY plus CI (hosted VM) (headless render); on a real desktop NDV (Steam Deck, Dell if Linux, RedMagic under Termux:X11) | 1.5 |
| L4 | Packaging: `jpackage` app-image tarball with `install.sh` per architecture; deb (`fakeroot` on the runner) and rpm (`rpm-build` installed in the job) only once the licence metadata question is answered (OQ-12; whether those formats tolerate omitting a licence field is unverified). Labelled `UNSIGNED — not for release`. Compose Desktop is XWayland-only today: ship the X11 path, spike Wayland behind a flag; the app must start without Wayland, Vulkan or Bluetooth (RedMagic-Edge) | `packaging/linux/`, package job in `desktop-linux.yml` on tags and dispatch | A hosted run produces the tarball into a draft release; install and launch on a device is NOV | CI (hosted VM); NDV | 0.5 |
| L5 | Flash: a host CMake wrapper reusing `core/inference/src/main/cpp` (a guarded `#ifdef __ANDROID__` edit to `logging.h`; Android output unchanged), built on `ubuntu-22.04` for the glibc baseline, x86_64 and aarch64, CPU first; same flags as Android (`GGML_NATIVE` off). The commit pin stays Nooz's own until `nooz_llama_jni.cpp`'s C calls are re-read against F8's pin | `desktop/native/llama-host/`, `jvmMain` library loader | Library compiles on each host and loads without a model; no model is downloaded in CI (R5). Output quality is NOV | CI (hosted VM); NOV | 1.5 |
| L6 | Cast: swap `onnxruntime-android` for the JVM artifact in `jvmMain`; Kokoro WAV through `javax.sound`; lexicon read from resources. **First add golden-vector tests for the Kokoro phonemizer, vocab and WAV writer (none exist)**; per-article listen has no `TextToSpeech` analogue and is Kokoro-only on desktop | `core/inference` `jvmMain`, tests | New tests green on every JVM host; audio output is NDV | CI (hosted VM); NDV | 1.0 |
| L7 | asom discovery: replace the Urbana actual with a localhost probe of asystemofmodels (F4). Unusable off Android until asom rules D25(b) and D14 part B (OQ-7d). If unruled, the desktop provider chain is local plus BYOK and the Urbana actual reports "not discoverable", as it does today. Cloud provenance preserved | `core/inference` `jvmMain` | Probe code compiles and unit-tests against a stub; no listener is started in CI | CI (hosted VM) | 0.5 |

### 6.2 Ubuntu Touch (reframe; 5 weeks)

Both shapes depend on NQ-1. Neither stores BYOK keys (no app-reachable keystore; program I-2) and neither claims
background fetching (confined apps are suspended soon after losing focus), so UI copy says what was fetched while
Nooz was open (NQ-5). Policy groups are the common set only: `networking`, plus `content_exchange` for OPML and
clipping export. Qt 5.15 is end of road, so the 26.04 canary lane runs from the first PR (F7). Click name and
OpenStore listing wait for OQ-25 and OQ-12; nothing is written to a manifest before a NAMES.md row exists (R11).

| Step | What | Placement | Done when | Evidence | Wk |
|---|---|---|---|---|---|
| U0 | Web reader host seam, upstream-first and additive: when `window.noozHost` exists, feed and article fetches go through it; otherwise `/api/feed` and `/api/article` as today. Tests in `web/tests`; the mirror in asystemofcells follows | `web/js/feeds.js`, `web/js/app.js`, `web/tests` (normal PR) | `web-tests` green with and without the host stub | CI (hosted VM) | inside U-A |
| U-A | Cheap first (P-UT a): a webapp-container click pointing at the hosted reader. Every fetch goes through the owner's Vercel proxy, a posture change that needs NQ-1; no offline mode | `ubuntu-touch/webapp/` from F7's template, `ubuntu-touch.yml` | Click builds in the Clickable image on a hosted runner | CI-APPROX — NOT DEVICE EVIDENCE; NDV | 2 |
| U1 | Headless core host: JVM module over `:core:model` and the shared `:core:data` pieces (fetch, extract, OPML, loom metrics, clippings) exposing a command set on F7's `asoc-ut-ctl/1`. Persistence is whatever K1 selects, built for linux-aarch64 (unverified). Needs L2 and S-UT1 passed or waived | `ubuntu-touch/core-host/` | Host tests pass on a hosted arm64 runner against fixtures | CI (hosted VM); S-UT1 NDV | 1.5 |
| U2 | QML pages (Lomiri.Components): Stand, Paper, Loom, Sources, Clippings, Settings, with F1's `Tokens.qml`, bundled Hyle fonts and a fourth i18n generator output so the 30 locales keep one source (D47); `verifyI18n` scope extended | `ubuntu-touch/qml/`, `tools/i18n/generate.py` (normal PR) | QML tests run under the canary image; colour never alone | CI-APPROX — NOT DEVICE EVIDENCE | 2 |
| U3 | Click packaging: `clickable.yaml`, `manifest.json.in`, `apparmor.in` (common groups only), click-review and the policy checker | `ubuntu-touch/` | Click builds; checker passes | CI-APPROX — NOT DEVICE EVIDENCE | 1 |
| U4 | Lifecycle: treat "interrupted" as normal, fetch on open, copy per NQ-5 | `ubuntu-touch/core-host/`, QML | Resume-after-suspend test on the arm64 userland image | CI-APPROX — NOT DEVICE EVIDENCE; real lifecycle NDV | 0.5 |

U1 to U4 total 5 weeks (the program row); U-A is the cheaper alternative and is not additive. Waydroid running
the unmodified `foss` APK on a UT phone is owner-device evidence only, never a port (OQ-21).

### 6.3 iOS and iPadOS (12 weeks, after L2)

| Step | What | Placement | Done when | Evidence | Wk |
|---|---|---|---|---|---|
| I1 | `:core:model` to `commonMain` with `jvm()` and iOS targets (no `androidTarget`, so `core-tests` stays SDK-free; K0 confirms Android can consume it): kotlinx-datetime, a KMP XML parser with the XXE hardening (K2 is the oracle), KMP URL handling; 27 JUnit 4 test files move to `kotlin.test`. A `test` task aliasing `jvmTest` is registered so `ci.yml`'s `core-tests` and the README's `./gradlew :core:model:test` keep working without editing `ci.yml` (R3); whether a KMP module exposes `test` natively is unverified, so the alias makes it moot | `core/model` | All 215 tests green on `jvmTest` and `iosSimulatorArm64Test`; `./gradlew :core:model:test` still runs them | CI (hosted VM), SIMULATOR | 1.5 |
| I2 | `:core:data` on native: Ksoup for the extractor, Ktor (Darwin) or NSURLSession preserving ETag, Last-Modified, gzip and redirect behaviour; file cache; exporter; store per K1 on its native driver; DataStore | `core/data` | The pure data tests (extractor, cache, repositories) pass on both targets; the Robolectric migration test stays Android-side | SIMULATOR | 1.5 |
| I3 | Native bridges: llama.cpp as an XCFramework behind a C ABI shim rewritten from `nooz_llama_jni.cpp` and bound by cinterop; onnxruntime iOS for Cast; `AVAudioPlayer`; `AVSpeechSynthesizer` for per-article listen. Inference is foreground-only with a guard that stops work on resign-active; downloads are disclosed and confirmed first (existing storage budget) | `apple/native/`, `core/inference` `iosMain` | Frameworks build; unit tests for the shim; App Review acceptance unverified | SIMULATOR; NDV | 3.0 |
| I4 | iOS layer and shell: XcodeGen head, SwiftUI shell hosting `ComposeUIViewController`; `UIDocumentPicker`, `UIActivityViewController`, Keychain (`WhenUnlockedThisDeviceOnly`, never synchronisable, via F6), in-app locale switch, `BGAppRefreshTask` with honest copy (NQ-5), edge-swipe versus the 24dp zone reconciled per NQ-2, iPad two-pane. Models and caches are excluded from iCloud backup; whether read history is also excluded is owner-visible because Backup is an OS-level egress (OQ-2) | `apple/` | Simulator app launches headless and screens render; on an iPad NDV | SIMULATOR; NDV (iPad Pro M4 is the only Apple device on record) | 4.0 |
| I5 | CI and store setup: `ios.yml` on a hosted macOS runner builds the simulator app, runs `iosSimulatorArm64Test`, produces an unsigned `.xcarchive` with `CODE_SIGNING_ALLOWED=NO`; `PrivacyInfo.xcprivacy` written truthfully (no tracking, no collected data) after the build; signing and TestFlight jobs exist only as disabled templates (R6, OQ-3) | `apple/`, `ios.yml` | Green simulator lane; no signing secret used | CI (hosted VM), SIMULATOR | 2.0 |

### 6.4 macOS (3 weeks, after L2 to L5)

| Step | What | Placement | Done when | Evidence | Wk |
|---|---|---|---|---|---|
| M0 | Disabled templates for entitlements, `Info.plist`, `notarize.sh` (F10); `desktop-macos.yml` runs `jvmTest` on a hosted macOS runner. Nothing signs (R6) | `packaging/macos/` | Lane green; no secret referenced | CI (hosted VM) | 0.5 |
| M1 | `jpackage` `.dmg` on tags and dispatch to a draft release, labelled unsigned (Gatekeeper will block it until OQ-3) | `packaging/macos/` | DMG produced on a hosted runner | CI (hosted VM); NOV | 0.5 |
| M2 | llama.cpp for macos-arm64 (x86_64 best effort; Metal optional; `GGML_NATIVE` off) from L5's wrapper; onnxruntime JVM natives for macOS verified at the pin; Keychain actual via F6 | `desktop/native/llama-host/`, `jvmMain` | Libraries build; load test without a model | CI (hosted VM); NOV | 1.0 |
| M3 | macOS conventions: menu bar, Cmd shortcuts, trackpad mapping per NQ-2; entitlement spike for the minimum set (never jpackage's default sandbox profile) | `desktop/` | Menu and shortcut tests pass headless; behaviour NOV | CI (hosted VM); NOV | 1.0 |

### 6.5 Windows (3 weeks, after L2 to L5)

| Step | What | Placement | Done when | Evidence | Wk |
|---|---|---|---|---|---|
| W0 | R3 path lint and line-ending pinning before any Windows lane. Today the lint finds nothing: 447 tracked files, no `:<>\|?*"`, no reserved device names, no case collisions (`git ls-files`, 2026-10-06). `.gitattributes` pins `kokoro_lexicon_us.tsv.gz` as `-text` and the exported schemas and catalogue JSON as LF; `desktop-windows.yml` runs `jvmTest` on a hosted Windows runner (charset and CRLF traps) | `packaging/windows/check-paths.py`, new `.gitattributes`, `desktop-windows.yml` | Lint and tests green on Windows; not yet run on a real Windows checkout | CI (hosted VM) | 0.5 |
| W1 | `jpackage` MSI with WiX on tags and dispatch to a draft release, unsigned (OQ-3) | `packaging/windows/` | MSI produced on a hosted runner | CI (hosted VM); NOV | 0.5 |
| W2 | llama.cpp for win-x64 with MSVC from L5's wrapper; onnxruntime JVM natives at the pin; x64 only (arm64 unverified) | `desktop/native/llama-host/` | Libraries build; load test without a model | CI (hosted VM); NOV | 1.0 |
| W3 | Platform actuals: DPAPI or a labelled file tier for BYOK (F6, OQ-22), file dialogs, save-PNG and clipboard share; winget manifest drafted as a template only (R11 needs an identifier first) | `jvmMain`, `packaging/windows/` | Actuals unit-test headless; behaviour NOV | CI (hosted VM); NOV | 1.0 |

## 7. Shared foundation this repo consumes or provides

| Item | Nooz role | Notes |
|---|---|---|
| F1 hyle-kmp (tokens, fonts as compose-resources) | Consumes | Until it exists, `Tokens.kt` and the ten TTFs move into `commonMain` and `composeResources` unchanged. Nooz has no Gradle link to Hyle today, so the PT:D-Q lockstep reaches it only when F1 is adopted (§8 item 2) |
| F2 crash-recovery KMP | Consumes (L3, I4) | Gated on PT:D-P's device verification; local report only, never transmitted; Android keeps `CrashRecovery.kt` |
| F3 cell-shell | Not consumed | |
| F4 asom-client | Consumes (L7) | Unusable off Android until OQ-7d; iOS has no daemon |
| F5 kmp-conventions | Consumes | Conditional Android inclusion, android-import ban for common and jvm source sets (D2 already says this for `:core:model`), dependency-licence allowlist. **SPDX headers stay off** (licence RESERVED) |
| F6 platform-ports (secure storage with reported tier, dirs, picker and share) | Consumes | Needs OQ-22. Nooz's stance for the owner to approve: a file tier with owner-only permissions, labelled "file" in Settings, is acceptable where no OS keystore exists, because the shipped Android baseline is plain private preferences; none on Ubuntu Touch |
| F7 ubuntu-touch-shell | Consumes (U1 to U4) | S-UT1 verdict, templates, jlink recipe, `asoc-ut-ctl/1`; OQ-1, OQ-24 |
| F8 native-engines pin | Consumes and may seed | Nooz's FetchContent commit pin and flags are a working precedent. Aligning to F8's pin means re-reading the bridge's C calls first, which touches Android |
| F9 `kmp-matrix.yml`, F10 packaging templates | Consume | Via SHA-pinned `uses:` or generated files (OQ-24); the Flatpak template is not consumed until OQ-12 |
| F11 evidence scheme and device checklists | Consumes | PROGRESS entries go in `STATE.md`; no README evidence table is added by this plan |
| F12 web/brand kit | Optional | `web/manifest.webmanifest` exists; alignment is the owner's call |

Provides to others: the unchanged `ai-catalogue` data contract (Aarso today; desktop and iOS Nooz downloaders
reuse the bundled-snapshot-plus-explicit-refresh pattern); the web reader as the upstream for asystemofcells' mirror;
and patterns other Compose repos can reuse if they work out here: the K1 verdict (Room-KMP or not, with FTS
evidence), the i18n generator's compose-resources output, and the compose-resources move of bundled fonts.

## 8. Open questions for the owner

Ids prefixed OQ are the program's (`Personal-Tracker/PORTING_PROGRAM.md` §8); NQ ids are local to this repo.

1. **OQ-12 Licence.** Assumed: direct downloads only; this plan suggests nothing. Blocks Flathub, Snap, AUR, Homebrew-source formulae, F-Droid for `foss`, OpenStore listing, and any deb/rpm or MSI metadata field that requires one.
2. **OQ-17 Toolchain pins.** Nooz carries Kotlin 2.0.21, KSP 2.0.21-1.0.28, AGP 8.7.3, Compose BOM 2024.12.01, Room 2.6.1. Blocks L0's Compose skeleton, spike K0 and every conversion step (L2 onward). Please also confirm that Nooz's place in the program's lockstep list is prospective, since it has no `includeBuild` of Hyle today.
3. **NQ-1 Ubuntu Touch shape and proxy posture.** Is a thin client acceptable (no Flash, Cast, dictionary lens, translation, BYOK)? Is the QML-over-JVM-core shape the primary, or the web-reader click? May feed and article fetches go through the owner's `/api/feed` and `/api/article` (the live reader already does) or must the click fetch natively? (The program also plans a webapp click for the reader mirror in asystemofcells; one home is enough.) Blocks U-A, U0, U1.
4. **OQ-1 and OQ-21 UT device and Waydroid.** Without a device S-UT1 cannot run and UT evidence stays CI-APPROX — NOT DEVICE EVIDENCE; is Waydroid running the `foss` APK an acceptable extra answer? Blocks U1 and every UT device gate.
5. **NQ-2 Desktop and iPad interaction design.** Will the owner supply pointer and keyboard mocks, or may the port derive equivalents (shortcuts, edge hover zones, trackpad mapping, the iOS edge-swipe collision) and log them as a numbered decision for approval? Blocks L3, I4, M3.
6. **NQ-3 First-release scope per platform.** Reader-only first, or with Flash and Cast (which dominate installer size and native build work)? Blocks the order of L5 to L7 and any installer-size claim.
7. **OQ-2 Apple.** Developer Program, delivery route to the iPad without a Mac, and the OS-level egress this brings (TestFlight tester crash reports, iCloud Backup of reading history) against Nooz's no-telemetry refusal. Also NQ-4: is the hosted web reader an acceptable interim iPadOS surface? Blocks I4 and I5 device entry.
8. **NQ-5 Background fetching and the denominator.** iOS and Ubuntu Touch cannot guarantee periodic fetch, and desktop fetches only while open. Is "what flowed while Nooz was open", plus opportunistic refresh, an honest denominator for the loom, and who words the copy? Blocks U4, I4 and the desktop scheduler copy.
9. **OQ-22 Secret custody.** Approve per-platform tiers (Keychain, DPAPI, libsecret, labelled file tier; none on Ubuntu Touch), each shown in Settings. Blocks F6 actuals for BYOK on every platform.
10. **NQ-6 Android Keystore follow-up.** It is already logged and changes Android behaviour, which the program does not do. Should it be its own Android PR before the secret-store actuals, or part of the port? Blocks the Android side of F6.
11. **NQ-7 Flavors and store history.** Desktop and iOS have no ML Kit: one flavor there? Does the twice-rejected Play history (D31) constrain Mac App Store or Microsoft Store submissions, versus direct download (OQ-4)? Blocks any store lane; direct-download lanes are unaffected.
12. **OQ-25 Identifiers.** Bundle id, click name, desktop app id, MSI upgrade GUID, winget id: none is written until a NAMES.md row exists (R11). `xyz.mdhv.riverwip` stays. Blocks every manifest in §6.
13. **NQ-8 ai-catalogue on new platforms.** Confirm nothing moves or is renamed, and whether desktop and iOS consume the same file or a platform-annotated one (an additive optional field would need the owner's sign-off because Aarso reads it). Blocks nothing in this plan, which assumes no change.
14. **NQ-9 Build brief.** `STATE.md` cites a build brief that is not in the repo. Where does it live, so this plan can be checked against it?
15. **OQ-3, OQ-4, OQ-5, OQ-20 Signing, channels, hardware, CI storage.** Developer ID and notarisation, a Windows signing route, store versus direct download, whether a Mac or the Dell exists, and Actions artifact storage. Block the signing templates (M0, W1, I5) and every macOS and Windows device gate (NOV until answered).
16. **OQ-7d asom off Android.** D25(b) and D14 part B decide whether desktop Nooz can discover asom. Blocks L7; without it the provider chain is local plus BYOK.
17. **NQ-10 Default branch and CI triggers.** `ci.yml` runs on pushes to `main`, but the default branch is `claude/app-build-d1f9s6`: which is trunk? This plan triggers package lanes on tags and dispatch; a default-branch change also affects the raw URLs Aarso uses.
18. **NQ-11 Script coverage off Android.** If the per-script rendering check (L3) finds missing glyphs on a target, bundle a Noto subset (megabytes; D46 avoided this on Android) or ship with the gap declared? Decided only if the check finds gaps.

## 9. Sources read

Inputs from the planning run (outside this repo): `Personal-Tracker/PORTING_PROGRAM.md` §0 to §8, the profile of
this repo (produced by a reader on 2026-10-06) and the full-plan template.

Read or measured directly by this plan's author on 2026-10-06 (paths from the repo root): `README.md`,
`LICENSE.RESERVED`, `STATE.md` (header, phase table, D33, D46, D59, §RESERVED, insertion points),
`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`,
`gradle/verification/unit-tests.gradle.kts`, `gradle/i18n/verify-i18n.gradle.kts`, `app/build.gradle.kts`,
`core/model/build.gradle.kts`, `core/inference/src/main/cpp/CMakeLists.txt`,
`.github/workflows/{ci,release,cleanup-artifacts}.yml`, `.gitignore`, `ai-catalogue/README.md`,
`tools/i18n/generate.py` (head), `web/vercel.json`, `web/api/feed.js` (head), `web/js/feeds.js` (head),
`core/inference/.../byok/ByokConfig.kt`, `core/data/.../net/HttpClient.kt`, `core/data/.../db/Entities.kt`,
`core/data/src/test/.../db/RiverDatabaseMigrationTest.kt` (the FTS case), `feature/reader/.../ReaderScreenTwoPane.kt`,
the file lists of `core/{model,data,inference}/src/test`, the `core/design` resource tree, import and call-site
greps across every module, the measurement commands in §1 and §6.5, and `git ls-remote --heads origin`.

Relied on from the profile reader's list and not re-read here: `STATE.md` decisions D1 to D7, D13, D15, D16, D18,
D20, D21, D29 to D31, D34, D37, D47, D48, D52 and the headers of D1 to D59; `app/src/main/AndroidManifest.xml`;
`app/.../{AppContainer,MainActivity,RiverApplication,AppLocale,ModelChoicePanel}.kt` and `crash/CrashRecovery.kt`;
the build files of `core/{data,inference,design}` and `feature/{sources,reader,river,lens}`;
`core/data/.../{RiverData,repo/DataExporter,repo/ModelCatalogueRepository}.kt`;
`core/inference/src/main/cpp/nooz_llama_jni.cpp`; `core/inference/.../{urbana/UrbanaProvider,local/LocalKokoroTtsProvider,local/kokoro/KokoroAudio,local/ModelManager}.kt`;
`core/design/.../{Tokens,PaperGrain,Type}.kt`; `feature/reader/.../FlashCard.kt`;
`.github/workflows/ai-catalogue-sentry.yml`; `ai-catalogue/{models,free-tiers}.json`; `catalogue/README.md`;
`fastlane/README.md`; `third_party/{fonts,data}/README.md`; `i18n/lexicon/README.md`;
`scripts/capture_screenshots.sh`; `web/{package.json,manifest.webmanifest,index.html}`; `web/api/article.js`;
`web/js/{app,db,router,images,newspaperShare,lens}.js`.

## Owner rulings and the proposed line (added 2026-10-07)

Status: PLAN. Nothing here is built, run on a device, signed or submitted. The program-level plan is Personal-Tracker `PORTING_PROGRAM.md` ([PR #10](https://github.com/mbaliga/Personal-Tracker/pull/10)), which holds the owner's rulings and section 5A, the proposed port / no-port line. The cells, estimates and open questions above are this repo's original plan and are unedited. Where the owner has since answered a question, the answer is below. Section 5A is a proposal; the owner has not yet confirmed it.

### Where nooz sits in the proposed line (program section 5A.3, a proposal)

| Target       | Verdict | Weeks and flags |
| ------------ | ------- | --------------- |
| Ubuntu Touch | port    | 5w g r          |
| Linux        | port    | 8w              |
| iOS/iPadOS   | port    | 12w g           |
| macOS        | port    | 3w g            |
| Windows      | port    | 3w g            |

Key: `follows` means it ports only as far as the products that depend on it; `exists` means the program reads it as already running there, unverified (finish, verify and sign); flags: `g` gated on a prerequisite, `r` re-estimate or floor, `o` its own program, `s` scope note. The program's P4, P8, P12 and P13 gate whole columns or repos and are not flagged per cell. A port verdict counts the deliverable in the line; where this repo's plan calls a deliverable a reframe (program rule R12) it keeps that label. Tests cited in the reason: (a) the owner said it is needed there; (b) its job is really done on that OS by real users; (c) that OS is where it is sold or its audience is; it has no reason to exist if (x) its surface is absent or untouchable, (y) the capability is forbidden or impossible, or (z) the only form is a thin wrapper or a different product nobody asked for. P-numbers and OQ-numbers refer to the program plan (Personal-Tracker `PORTING_PROGRAM.md`, sections 5A.5 and 8).

Reason: Reading news is a phone and a desktop job, and its pure-JVM core is the cleanest CMP candidate. UT is a port gated on S-UT1: background fetch is absent there as it is on iOS and desktops, and the app says so. Its iOS, macOS and Windows builds need the llama.cpp per-OS pin (program P7). nooz is licence-RESERVED, so direct downloads only until OQ-12 is ruled (program P13).

### Owner rulings that apply here

- **OQ-17 toolchain (2026-10-06):** "B: staged pin (Recommended)": Kotlin 2.1.20 and Compose Multiplatform 1.8.2 for the first wave, 2.4.x deferred.
- **OQ-38 iOS pin (2026-10-07):** "Move iOS to Kotlin 2.2.21 + CMP 1.9.3 (Recommended)", with the first iOS proof on an explicitly selected Xcode 26.x. The program plan reads this as moving a repo that ships an iOS target as a whole (a Gradle build has one Kotlin version); that reading is not researched, and the bump cost for repos pinned lower is in no figure. nooz is one of the lower pins the program names (Kotlin 2.0.21).
- **Ubuntu Touch scope (2026-10-06):** "Native only, no substitutes" for Android-only products. The program marks only the web-reader click alternative in this plan's Ubuntu Touch cell (step U-A) as a substitute the ruling excludes. The primary shape, QML pages over a headless JVM core (steps U1 to U4, 5 weeks), is native and is the shape the proposed line counts. **OQ-21:** "No, native ports only" (Waydroid is not accepted, which answers the second half of this plan's open question 4). **Ubuntu Touch keys:** "App-private file allowed" (an app-private file with the weaker guarantee shown in the UI); this plan's thin client holds no BYOK key on Ubuntu Touch, so the ruling matters only if that changes.
- **Ubuntu Touch device:** the owner owns one and says it is a OnePlus 6; research reads it as 20.04-only while the program plan targets 24.04. On 2026-10-07 the owner chose "OnePlus 6 pre-spike now, decide later" (OQ-37): a labelled "S-UT1 (focal)" headless-JVM pre-spike, no 24.04 flashing, a 24.04 device decision afterwards. Every Ubuntu Touch device gate stays NDV until then.
- **OQ-31 Mac (2026-10-06 and 2026-10-07):** "Buy a Mac", and on 2026-10-07 an Apple-silicon Mac mini, not yet bought; no Apple device gate is called checkable before then.
- **Apple (OQ-2, 2026-10-06):** "Whatever let's me sell apps on the app store": the paid Developer Program and the App Store are the target channel. TestFlight is not used until the exception to I-1 (OQ-32, drafted as PROPOSED-1, not approved) is approved.
- **OQ-20 CI (2026-10-06):** "Linux-only CI when private (Recommended)": this repo is public, so the ruling does not limit its macOS and Windows lanes; going private would stop them. Actions artifact storage is still exhausted (program rule R6).
- **OQ-5 hardware (2026-10-06):** the owner's answer changes which of their other machines can serve as device gates, so a gate this plan names on specific hardware may be moved or dropped. Which machine carries which device gate is not decided (OQ-33).
- **OQ-22 key custody (2026-10-06):** "OS keystore, weaker fallback shown (Recommended)": Keychain, Credential Manager (DPAPI), Secret Service, a passphrase-protected file or an app-private file on Ubuntu Touch, each with the weaker guarantee stated in the UI.
- **Repo-specific:** the Ubuntu Touch deliverable keeps this plan's own label, a thin client over the JVM core (program rule R12), and is never called a port of the Android app.
- **Directives (2026-10-06):** "Draft amendments for approval": program directives I-1 to I-12 and rules R1 to R12 are unchanged; PROPOSED-1 to PROPOSED-4 in Personal-Tracker `DECISIONS.md` are drafts awaiting the owner.

### Prerequisites and open questions that touch this repo (program sections 5A.5 and 8)

Prerequisites (program-level; not costed here):

- program P3: S-UT1: one headless jlinked-JVM-in-a-click spike driven from QML (not run)
- program P4: A device that can run the 24.04 Ubuntu Touch the program plan targets (the owner's OnePlus 6 is read as 20.04-only)
- program P7: The native-engines pin (F8): one llama.cpp and stable-diffusion.cpp commit with per-OS builds
- program P8: An Apple-silicon Mac (OQ-31: a Mac mini chosen on 2026-10-07, not yet bought)
- program P12: The iOS toolchain pin (OQ-38, ruled 2026-10-07): Kotlin 2.2.21 with Compose Multiplatform 1.9.3 for the iOS targets, proven on an explicitly selected Xcode 26.x; nooz is one of the lower pins the program names (Kotlin 2.0.21) and, on the program plan's reading, its Android and desktop builds would move with the iOS pin; the bump cost is in no figure
- program P13: OQ-12: nooz is licence-RESERVED, direct downloads only until the owner rules

Owner questions in the program register that concern this repo (status as of 2026-10-07):

- OQ-2 (ruled): Apple Developer Program and the delivery route
- OQ-5 (ruled): Hardware stance
- OQ-7 (open): Repo-local gates, clause (d): whether other apps may use asom off Android (D25(b)); this plan's step L7 and open question 16
- OQ-12 (open): Licences for repos without a LICENSE
- OQ-17 (ruled): Toolchain pins: the pin is ruled; the "Also" approvals (converting shared modules to kotlin("multiplatform"), asom's no-KMP rule staying asom-local) are unanswered
- OQ-20 (ruled): CI minutes, storage and repo visibility
- OQ-21 (ruled): Waydroid as the Ubuntu Touch answer
- OQ-22 (ruled): Secret custody per platform
- OQ-31 (ruled): CI for App Store builds; which Mac
- OQ-32 (open): Exception to I-1 for TestFlight and App Store crash reports
- OQ-33 (open): Hardware details still open
- OQ-37 (answered in part): A second Ubuntu Touch device
- OQ-38 (ruled): iOS toolchain pin

When the owner confirms or changes the line, this repo's original cells above stay as the engineering detail; only the verdicts and re-costs in program section 5A change.
