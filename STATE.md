# STATE

Living build state. Updated every session (brief §0).

- **Working register:** omission. Name: **Nooz** (owner-decided 2026-07-10, see
  D5). License, taxonomy labels, metaphor language remain **RESERVED** — see
  §RESERVED. Package: `dev.asystemofcells.nooz` (owner-decided 2026-07-20, via
  Play Console registration; rename applied).
- **Current phase:** P0–P7 substantially built, plus the owner's 2026-07-11/12
  feature round (see D9–D13): the reader "one more cut" (real Hyle fonts,
  interactive sheet-slide, newspaper share, back-gesture fix), **Clippings**,
  the **dictionary lens** (blue obscure-word definitions + one-click
  dictionaries, non-generative), an **all-region feed expansion** (63 verified
  feeds) with **bottom-docked search/filter** and surfaced **News APIs**, and a
  sibling session's shared **ai-catalogue/** (open models + free-tier data).
  P7's a11y pass, F-Droid/Play metadata skeleton, and screenshot script are in;
  baseline profiles remain an honestly-logged gap (see P7 section below).

---

## Phase status

| Phase | Title | Status |
| ----- | ----- | ------ |
| P0 | Scaffold | ✅ complete (CI green) |
| P1 | Sources | ✅ complete (CI green) |
| P2 | Ingest & classify | ✅ complete: parse/dedup/classify (pure) + Room persistence + WorkManager scheduled fetch |
| P3 | Reader | ✅ complete: article list, typography-first reader, full-text extraction + LRU cache, end-of-feed marker |
| P4 | The River (centerpiece) | ✅ complete: pure layout/analysis math + Canvas visualization + cross-section panel |
| P5 | The Lens (tap-to-defuse) | ✅ complete: UI + guard + router + on-device inference all real (see D31) |
| §8 | CVD palette | ✅ done + verified (build-failing pairwise test) |
| P6 | Catalogue sensing layer | ◑ device-side layer complete (remote refresh + local health monitor); CI sentry built but **targets this repo's own mirror, not the real provider-catalogue project** (see below) |
| P7 | Hardening & release prep | ◑ a11y pass done (+ adversarially reviewed/fixed); F-Droid/Play metadata skeleton + screenshot script done; **baseline profiles honestly not attempted** (see below) |

### Strategy: analytical cores first, then UI
The brief's load-bearing, correctness-critical logic is pure and lives in
`:core:model`/`:core:inference`, unit-verified locally (a JVM scratch harness —
no Android SDK in this sandbox) *before* the Android/Compose surfaces that
present it. Every push is compiled by CI; several genuine bugs were caught and
fixed this way (see "CI-caught issues" below) — that log is worth reading
before touching WorkManager, Compose smart-casts, or the JSON-builder DSL again.

### P5 — inference providers, current state
Detection, evidence, the fidelity guard, session-ephemeral defuse state, the
reader overlay, and the defuse bottom sheet are all real and wired end-to-end.
- **LocalLlamaProvider**: real end-to-end as of D31 — a genuine, vendored
  llama.cpp build (`core/inference/src/main/cpp`), not a stub. `isAvailable()`
  checks for any `.gguf` on disk (model *download* — real catalogue, streaming
  + progress, storage budget, delete — was already real, see D18); `rewrite()`
  and `digest()` now actually load that model and run it via JNI. What's
  **still honestly unverified**: this sandbox can compile Kotlin/C++ and let
  CI cross-compile the native library, but it can't run an Android
  device/emulator, so real-device output quality (does a 1.5B-3B model
  actually produce a *good* rewrite/digest, as opposed to "the pipeline runs
  and returns text") is unverified here — same honesty caveat
  `LocalKokoroTtsProvider`'s own doc comment already carries for Cast's audio
  output. `FidelityGuard` still vets every result downstream regardless.
- **UrbanaProvider**: real ContentProvider discovery attempt against
  `com.urbana.daemon.discovery` — correctly reports "not discoverable" since no
  real daemon exists to test against here (brief: absent support hides the
  provider, never errors — this is the correct behavior, not a bug).
- **MlKitProvider**: lives in `:core:inference`'s `full` source set only
  (so `foss` never references it). No real ML Kit GenAI dependency added yet
  (evolving API, no Pixel-class device to verify against) — conservatively
  reports unavailable.

### P6 — Catalogue sensing layer
- **`SourceHealth`** (`:core:model`): a pure `classify(lastFetchAt, lastError,
  consecutiveFailures, now)` over data P2's fetch loop already recorded on
  `SourceEntity` (`etag`/`lastModified`/`lastFetchAt`/`lastError`/
  `consecutiveFailures` — anticipated back in P2, now finally read). Four
  states: OK, STALE (no success in 48h), RATE_LIMITED (429/"Too Many
  Requests"/`Retry-After` in the error), FAILING (any other error). Unit
  tested. `SourceRepository.observeHealth()` exposes it; the Sources screen
  shows a per-row status line — descriptive, never alarmist, per the copy
  register.
- **`CatalogueRepository`** (`:core:data`): lets `catalogue.json` refresh
  Tier A/B definitions without an app release, per spec. **Deliberately ships
  with no default URL** — brief §0 requires every remote URL be verified live
  at build time, and "the provider-catalogue repo" is an external project
  this build has no access to and cannot verify; shipping a guessed URL would
  violate that same standard. The user supplies the URL (Sources screen,
  "Catalogue" card); refresh is a manual pull, never automatic/background.
  Falls back to the verified `Starters.seed` until/unless the user loads one.
  Backed by `DataStore<Preferences>` (`catalogue` store) — this is the first
  real use of the already-declared DataStore dependency.
- **CI-side sentry** (`.github/workflows/catalogue-sentry.yml` +
  `.github/scripts/catalogue_sentry.py`): scheduled (weekly) + manually
  dispatchable Action that GETs every probeable Tier A/B endpoint listed in
  `catalogue/catalogue.snapshot.json` (a hand-maintained mirror of
  `Starters.kt`), diffs against the last recorded status, and — on drift —
  rewrites the snapshot, writes `catalogue/DRIFT_REPORT.md`, and opens a PR
  via `peter-evans/create-pull-request`. Locally dry-run three times against
  a scratch copy: first run establishes baseline (no drift, correct), a
  `SIMULATE_DRIFT=1` run correctly detects and reports one seeded failure,
  and a third run correctly detects the simulated recovery back to OK —
  satisfies the brief's own gate ("simulated tier change → Action opens a
  correct PR") end-to-end. **What's honestly incomplete**: "opens PRs
  against the catalogue repo" in the brief means an external
  `provider-catalogue` project this build has no fork of and no PAT for.
  Rather than fabricate that, the sentry is built to do the real thing
  (real probes, real diffing, real PR) against a *configurable* target —
  `CATALOGUE_FORK_REPO` (repo variable) + `CATALOGUE_FORK_TOKEN` (secret) —
  and defaults to opening the PR against this repo's own tracked mirror when
  those aren't set. See `catalogue/README.md`.

### P7 — Hardening & release prep
- **A11y pass**: done, then adversarially reviewed and fixed (see the new
  "Adversarial-review-caught issues" section below) — TalkBack labels/actions
  on lens spans (`LensAnnotatedParagraph`'s per-span `CustomAccessibilityAction`s)
  and river regions (`RiverCanvas`'s per-week `selectable` + `contentDescription`
  overlay, RTL-pinned, in a `selectableGroup()`), plus loading-state labels,
  live regions, expand/collapse `stateDescription`, and a merged
  switch+label announcement in Sources.
- **F-Droid metadata / Play listing skeleton**: `fastlane/metadata/android/en-US/`
  — the one directory shape both F-Droid (reads it directly from an app's
  source repo) and Play (via fastlane's `supply`/Triple-T) understand, so one
  skeleton serves both. `title.txt` stays an explicit RESERVED placeholder
  (the name decision *is* the file); `short_description.txt`/
  `full_description.txt` hold draft, functional copy — no name, no
  metaphor/mythological language, no taxonomy-label decisions, per §RESERVED.
  `images/` is empty (icon is RESERVED; screenshots need a device this
  session doesn't have). See `fastlane/README.md`.
- **Screenshot script**: `scripts/capture_screenshots.sh` — real, ready-to-run
  `adb`-based tooling for whoever has a connected device/emulator. Never
  executed in this session (no device here); honestly documented as such
  rather than faked.
- **Baseline profiles — honestly not attempted.** Generating one requires
  running a macrobenchmark instrumented test on a real/emulated device to
  record an actual startup/rendering trace; this sandbox has no Android SDK
  emulator, and `ci.yml` doesn't run `connectedAndroidTest` either (no AVD is
  provisioned there). Scaffolding an `androidx.baselineprofile` Gradle module
  I have no way to compile-check, exercise, or verify even at the "does this
  new module break the existing build" level (Gradle configures the whole
  project graph even for targeted tasks, so a broken new module could take
  down `assembleFossDebug`/`assembleFullDebug` for everything else) is a real
  risk for zero verified benefit in this environment — the same bar D3/D4
  apply elsewhere. Logged here as a follow-up requiring a device-capable
  environment, not attempted rather than faked.

### Remaining
- P6 follow-up: point `CATALOGUE_FORK_REPO`/`CATALOGUE_FORK_TOKEN` at the
  real provider-catalogue project once one exists and is authorized.
- P7 follow-up: baseline profiles (see above); fill in `fastlane/` once the
  RESERVED name/license/icon decisions land; run `scripts/capture_screenshots.sh`
  once a device/emulator is available.
- Visual polish (logged, not correctness-blocking): the lens's pre-underline
  is solid, not dotted (a true dotted underline needs a custom draw pass keyed
  to `TextLayoutResult`); the river's topic bands are flat stacked rects, not
  smoothed flow ribbons.
- Dedup only collapses near-duplicates *within a single fetch's items*, not
  across the full item history — a real-time whole-DB similarity scan was out
  of scope for v1; logged as a possible future enhancement.

### CI-caught issues (read before touching these areas again)
- **Nested KDoc comments**: Kotlin block comments nest. A KDoc mentioning a
  glob path like `` `tokens/*.json` `` or `` `mapping/*.kt` `` opens a *nested*
  comment at the `/*`, which the KDoc's own closing `*/` then closes — leaving
  the outer `/**` unterminated and swallowing the rest of the file ("Unclosed
  comment" at EOF, with a cascade of unrelated "unresolved reference" errors
  downstream). Hit twice (`Tokens.kt`, `Entities.kt`); fixed by rewording to
  avoid a literal `/*` in prose. `*/*` inside a **string literal** (MIME
  wildcards) is safe — only true comment context matters.
- **Cross-module smart-cast**: Kotlin will not smart-cast a nullable `val`
  property when it's declared in a *different Gradle module* than the code
  reading it (e.g. `Item.author: String?` from `:core:model`, read in
  `:feature:reader`) — "Smart cast... is impossible, because 'x' is a public
  API property declared in different module." Fix: bind to a local `val`
  first (always smart-castable) before the null check. Same-module properties
  (e.g. a sealed-class field from a ViewModel in the same feature module) are
  unaffected.
- **WorkManager + Configuration.Provider**: implementing `Configuration.Provider`
  alone is *not* sufficient — Android Lint's `RemoveWorkManagerInitializer`
  check requires the default `androidx.work.WorkManagerInitializer` be removed
  from the manifest via the documented `tools:node="remove"` override once a
  custom `Configuration` is needed. Switched to full manual/on-demand init
  instead (`WorkManager.initialize(...)` called explicitly in
  `Application.onCreate()`), which also removes an ordering hazard the
  Configuration.Provider path had.
- **`WorkRequest.MIN_BACKOFF_MILLIS`** lives on the `WorkRequest` base class,
  not `PeriodicWorkRequest`.
- **kotlinx.serialization's JSON builder DSL**: `JsonArrayBuilder`/
  `JsonObjectBuilder`'s own collection-like members can shadow the `add(String)`
  / `put(String, String)` convenience extensions under some overload
  resolutions, silently demanding a `JsonElement` instead of a raw primitive.
  Fixed by constructing `JsonObject`/`JsonArray`/`JsonPrimitive` explicitly
  rather than using the `buildJsonObject { put(...) }` DSL.
- **Flavor dimension propagation**: any module that transitively depends on a
  flavored module (here, `:core:inference`, flavored `foss`/`full` so `foss`
  never references ML Kit) must declare the *same* flavor dimension itself,
  even with no flavor-specific source of its own, or Gradle's variant-aware
  resolution has no matching variant to pick. `:feature:lens` needed it from
  the start (P0 scaffold correctly anticipated this); `:feature:reader` needed
  it added once it started depending on `:feature:lens` for the lens overlay.
- **`room-runtime`/`room-common` in a local JVM-only harness**: `room-common`
  (pure annotations) compiles fine on plain JVM and is enough to verify
  Entities/DAOs/mappers/repositories; `room-runtime` is an AAR and cannot be
  resolved by a plain `kotlin("jvm")` project at all (`RiverDatabase`/
  `RiverData`/anything touching `Context` or `WorkManager` stays CI-only).
- **Platform declaration clash: delegated `var` + same-named function**: a
  Compose-state-delegated property (`var x: Boolean by mutableStateOf(...)
  private set`) still compiles a synthetic `setX(Boolean)` method even though
  Kotlin's own visibility on it is private — the JVM only sees the name +
  descriptor, not Kotlin visibility. A same-signature public `fun
  setX(enabled: Boolean)` on the same class collides with it ("Platform
  declaration clash"). Hit in `LensViewModel` (`setUnderlinesEnabled`,
  `setSterileLensEnabled`). Fixed by naming the public setter functions
  differently from the property (`updateUnderlinesEnabled`,
  `updateSterileLensEnabled`) rather than mirroring the property name.

### Adversarial-review-caught issues (CI didn't catch these — read before touching P7's a11y layer)
CI is unit tests + lint + assemble; none of those exercise locale, TalkBack, or
touch-target geometry, so this class of bug compiles clean and stays invisible
to the pipeline. Caught instead by a dedicated review+adversarial-verify pass
over the P7 accessibility diffs (see STATE.md's P7 section) — worth the same
respect as the CI-caught log above.
- **Custom `Canvas` drawing + a Compose layout overlay silently disagree under
  RTL**: `RiverCanvas`'s `Canvas` computes column position with raw,
  direction-unaware pixel math (`x = index * (colWidth + gapPx)`) — `DrawScope`
  never auto-mirrors for RTL, mirroring is the drawer's own job. The
  accessibility/tap overlay added on top of it, a plain `Row`, *does*
  auto-mirror under `LayoutDirection.Rtl` (this app declares
  `android:supportsRtl="true"`, and nothing pinned direction anywhere). Result:
  in an RTL locale the two layers disagree about which physical position is
  "week 0," so both taps and TalkBack focus for a visually-correct bar resolve
  to the wrong week. Fixed by wrapping the overlay in
  `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`
  so it matches the Canvas's own always-LTR coordinate space. **Lesson**:
  whenever a custom `Canvas`/`DrawScope` and a real Compose layout share one
  coordinate system (an overlay, a hit-test layer), pin them to the same
  layout direction explicitly — they do not agree by default.
- **`Modifier.toggleable` must span the whole interactive region, including
  the control it labels**: in `SourceRow`, `toggleable` was attached to the
  label `Column` only, with the sibling `Switch`'s `onCheckedChange` set to
  `null` (correct, to avoid a duplicate handler) — but that left the `Switch`
  itself outside the only remaining tap target, so tapping the switch control
  directly did nothing (only tapping the text still worked). Fixed by moving
  `toggleable` onto a `Row` that wraps both the label `Column` and the
  `Switch` together. **Lesson**: when hollowing out a control's own handler in
  favor of a parent `toggleable`/`selectable`, the parent's bounds must
  actually contain that control, not just the text next to it.
- **Repeated `CustomAccessibilityAction` labels are indistinguishable in
  TalkBack's action menu**: `LensAnnotatedParagraph` built one action label
  per detected span from `span.evidence` alone (term + category), so two
  spans hitting the same common lexicon word (e.g. two "very"s) produced
  byte-identical, unpickable menu entries. Fixed by indexing labels (`"(2 of
  3)"`) and varying the trailing verb by the span's actual state (Accepted →
  "Revert suggestion", Rejected → "Rewrite unavailable", else → "View
  suggestion") instead of a static "View suggestion" regardless of state.
- **A rounded percentage can contradict the exact count next to it**:
  `RiverCanvas`'s per-column description rounded read/stream to a percentage
  naively, so e.g. 199 of 200 read rounds to "100% read, 199 of 200" — two
  adjacent numbers a screen reader states as fact that don't agree. Fixed by
  only ever showing 0%/100% when it's exactly true, clamping everything else
  to 1–99.
- **`Modifier.selectable` wants a `selectableGroup()` parent**: a row of
  individually-`selectable` items (the river's week columns) needs
  `Modifier.selectableGroup()` on their shared container for TalkBack/Switch
  Access to expose proper single-selection/position semantics ("item 3 of
  12"); added to `RiverCanvas`'s overlay `Row`.

### P0 — done
- Multi-module Gradle build: `:app`, `:core:model|data|inference|design`,
  `:feature:sources|reader|river|lens`. Version catalog (`gradle/libs.versions.toml`).
- Gradle wrapper 8.14.3 (jar vendored — `services.gradle.org` is unreachable from
  this dev sandbox, so `gradle wrapper` URL validation can't run here; CI resolves
  the distribution normally).
- Two flavors `foss` / `full` on the inference chain (`:core:inference`,
  `:feature:lens`, `:feature:reader`, `:app`); `:feature:sources`/`:feature:river`
  stay flavor-agnostic (no dependency on the flavored chain). `foss` carries zero
  proprietary/Play deps.
- Manual DI: composition root `:app/AppContainer.kt` (no Hilt/Koin).
- Token contract in `:core:design` (`Tokens`, `Theme`, `Type`, `Copy`,
  `EmptyState`, `TopicColors`). Dark-first, violet `#8E7BFF`. **Values sourced
  from the Hyle Design System** (see Decisions D1). Behaviour never depends on
  token values.
- CI (`.github/workflows/ci.yml`): pure-core tests + both-flavor unit tests,
  lint, assemble.
- `LICENSE.RESERVED`, `.gitignore`, `gradle.properties` (package base centralized).

### P1 — done
- Pure (`:core:model`): domain types, taxonomy, hashing/ids, canonical-URL
  normalization, feed URL builders (Google News/GDELT/Mastodon), HTML feed
  autodiscovery, OPML import/export, registry schema, verified starters,
  source-kind detector.
- Data (`:core:data`): Room `SourceEntity`/DAO/DB, dependency-free `HttpClient`
  (gzip, conditional GET, redirects — foss-clean, no OkHttp), `FeedProbe`/
  `FeedProber`, `SourceRepository` (add-by-URL w/ discovery, starters, OPML,
  enable/disable/remove, per-source health fields).
- Feature (`:feature:sources`): add-by-URL, candidate picker, one-click
  starters by region, builder/keyed rows (honestly labeled), OPML import/export
  via SAF, source list w/ enable + remove.
- Registry mirrors provider-catalogue `services[]` (`consumedAt: runtime`) →
  zero-migration P6.
- **Gate:** starters seed rss + googlenews + gdelt + mastodon kinds (≥4);
  denominator copy present on the sources surface + empty states. ✅

### P2 — done
- Pure (`:core:model`): `FeedParser` (RSS/Atom/RDF/JSON, XXE-hardened),
  `Simhash` (char-shingle, calibrated threshold), `Dedup`, `Classifier`
  (evidence-carrying: every assignment names its rule + matched terms),
  `Ingest` transform, `WeekBucketing`, `WeeklyAggregator`, `SyntheticCorpus`.
- Data (`:core:data`): `ItemEntity`/`ReadEventEntity`/`WeeklyAggregateEntity`
  (structured fields as JSON columns via `JsonColumns.kt`, not Room
  TypeConverters), `ItemRepository` (fetch+ingest, ETag/Last-Modified
  conditional GET, per-source failure isolation), `ReadEventRepository`,
  `WeeklyAggregateRepository`. `FetchWorker`/`RiverWorkerFactory`/
  `FetchScheduler` (WorkManager, manual DI, on-demand init).
- **Gate:** per-source failures isolated (one dead feed never blocks others);
  tapping any topic chip's evidence is traceable to the rule + terms. ✅

### P3 — done
- Data (`:core:data`): `ArticleExtractor` (lightweight Readability-style
  heuristic over jsoup — link-density-scored paragraphs, best-container
  selection), `FullTextCache` (file-based LRU, size budget, 200MB default),
  `ArticleRepository` (cache-first, `ArticleFetcher` abstraction for testing).
- Feature (`:feature:reader`): `ArticleListScreen` (topic dot + label, never
  color alone; denominator-honest header; explicit end-of-feed marker),
  `ReaderDetailScreen` (typography-first, share/open-in-browser, loading/
  loaded/fallback states), coarse read-event lifecycle (GLANCE on open, READ
  on close-after-successful-load — buckets only, never a duration).
- **Gate:** extraction verified against real-shaped HTML incl. nav/sidebar/
  link-heavy noise. ✅ (device-measured "≥80% of starter-list articles" not
  verified — no device in this sandbox.)

### P4 — done
- Pure (`:core:model`): `RiverAnalysis` (coverage, over/under ratio, breadth =
  exp(Shannon entropy), the supply-vs-drift Laspeyres decomposition — verified
  to sum to Δ exactly across hand cases, new/vanishing topics, and 500
  deterministic random trials), `RiverLayout` (chronological → normalized
  per-topic band fractions, scaled against the heaviest week so shape stays
  comparable across time).
- Feature (`:feature:river`): `RiverCanvas` (custom Canvas, no chart library —
  read sub-segment in full topic color, unread muted toward near-black as a
  single shared dark direction, "negative space is the protagonist"),
  `CrossSectionPanel` (every metric tap-to-reveal its formula; the
  decomposition in plain language), denominator-honest header, honest empty
  state below 2 periods of history (nothing to decompose against yet).
- **Gate:** decomposition sums exactly (proven in `:core:model` tests, 500-case
  property test + an 8/12-week synthetic-corpus geometry sweep as a
  computational proxy for "renders 8+ weeks smoothly", since Canvas rendering
  itself can't be visually verified in this sandbox). Palette CVD test green. ✅

### P5 — done except honestly-stubbed inference execution (see above)
- Pure (`:core:model`): `BiasLexicon` (Recasens-lineage loaded verbs/
  intensifiers/emotive adjectives/editorializing hedges), `AffectSpanDetector`
  (non-overlapping, phrase-preferring span detection with per-span evidence),
  `FidelityGuard` (rejects any rewrite that adds/drops a number, adds/drops a
  capitalized entity, or changes negation parity — adversarial corpus caught
  100%, faithful neutralizations pass).
- Pure (`:core:inference`): `InferenceProvider`/`Provenance`/`RewriteRequest`/
  `RewriteResult`, `InferenceRouter` (user-ordered fallback, stops at the first
  *available* provider's real answer rather than silently cascading past a
  failure), `ModelManager` (checksum + storage budget, real and tested).
- Feature (`:feature:lens`): `LensViewModel` (session-ephemeral — no Room/
  DataStore dependency anywhere in the class; underline + sterile-lens
  toggles), `LensAnnotatedParagraph` (tap-mapping stays correct even after a
  rewrite changes the span's rendered length), `DefuseBottomSheet` (evidence,
  detection-as-opinion copy, provenance-colored accept state, revert).
- **Gate:** adversarial corpus (seeded number swaps, entity substitutions,
  dropped negations) caught 100%; accept/revert wiring complete. Real-device
  round-trip (actually generating and displaying a model's rewrite) blocked on
  wiring one real provider — see above.

### P6 — done except the sentry's PR target (see above)
- Pure (`:core:model`): `SourceHealth`/`HealthStatus`/`SourceHealthClassifier`
  (OK/STALE/RATE_LIMITED/FAILING/UNKNOWN from data P2 already recorded — no
  new collection, just a read model over it).
- Feature (`:core:data`): `CatalogueRepository` (DataStore-backed, no default
  URL — decision D4), `SourceRepository.observeHealth()`.
- Feature (`:feature:sources`): a "Catalogue" card (URL entry, refresh, revert
  to built-in starters) and a per-source health line, both wired through
  `SourcesViewModel`.
- CI: `.github/workflows/catalogue-sentry.yml` + `.github/scripts/catalogue_sentry.py`
  + `catalogue/catalogue.snapshot.json`. Locally dry-run three times (baseline,
  simulated drift, simulated recovery) — all three behaved correctly.
- **Gate:** "simulated tier change → Action opens a correct PR" — satisfied
  against this repo's own mirror (the `workflow_dispatch` `simulate_drift`
  input exercises the full probe→diff→PR path); satisfied against the *real*
  external provider-catalogue project only once `CATALOGUE_FORK_REPO`/
  `CATALOGUE_FORK_TOKEN` are configured — see decision D4.

### P7 — a11y pass done (+ adversarially fixed); metadata skeleton + screenshot script done; baseline profiles not attempted
- A11y, F-Droid/Play skeleton, and the screenshot script are described in the
  P7 section above (not repeated here). The a11y pass is worth flagging twice
  for one reason: CI (unit tests + lint + assemble) caught none of the real
  bugs in it — a dedicated adversarial review pass did, catching an RTL
  tap/TalkBack mismatch and a dead switch tap-target that would otherwise have
  shipped silently. See "Adversarial-review-caught issues" below.
- **Gate**: no gate stated in the brief for P7 beyond the a11y pass itself;
  treating "TalkBack labels including lens spans and river regions" as met
  (both surfaces now have real semantics, reviewed and fixed), with baseline
  profiles as the one honestly-open item.

---

## Decisions (locked unless brief says otherwise)

- **D1 — Design tokens come from the Hyle Design System.** The owner cloned
  `mbaliga/hyle-design-system` into the build session. Hyle is the ecosystem's
  cross-platform token source of truth and already matches the brief's locked
  defaults (dark-first, violet `#8E7BFF`, provenance radium `#C7EF9E` on-device /
  cyan `#35E0FF` cloud, `#121212`-class surfaces). `:core:design/Tokens.kt`
  mirrors Hyle's shared tokens' values. The brief anticipated a re-skin "when
  final tokens land" — they landed. Font family (brief placeholder was Plus
  Jakarta Sans) is fully resolved as of D7: Hyle's own three internal families
  are bundled and verified — the two sans (**Hyle Grotesk Classic**, **Hyle
  Grotesk Plus**) and the serif (**Hyle Print**); no platform fallback remains.
- **D2 — Pure logic lives in `:core:model`/`:core:inference` (Kotlin/JVM, no
  Android).** All unit-critical algorithms (river decomposition, dedup
  simhash, classifier rules, lens fidelity guard, CVD palette math, model
  checksum/budget logic) sit here so they run without an Android SDK, locally
  and in CI. Android modules consume them and add only IO/UI. This refines,
  not reshapes, the brief's module sketch.
- **D3 — Inference execution is honestly stubbed, not faked.** See the P5
  section above. This is a considered decision, not a shortcut: the brief's
  own FidelityGuard exists because small models fabricate; an agent silently
  faking a "working" model would be a worse failure than admitting the gap.
- **D4 — No default catalogue URL, no default sentry PR target.** Same
  standard as D3, applied to P6: brief §0's "verify live at build time" rules
  out shipping a guessed `catalogue.json` URL, and the CI sentry has no
  fork/PAT for the external provider-catalogue project the brief describes.
  Both are built to do the real thing (real fetch/parse/merge, real probe/
  diff/PR) against a *user- or repo-configurable* target rather than a
  fabricated default — see the P6 section above.
- **D5 — The owner's design mocks (2026-07-10) supersede the dark-first
  working skin; the name is decided.** The owner supplied five mocks (Splash,
  Paper = article view, Viz = the river as an hourglass supply→consumption
  flow, Stand = the list, Settings) carrying a "Nooz" wordmark and a light,
  paper-and-serif newspaper language. These are *owner artifacts*, so the
  previously-RESERVED name decision is now **made — "Nooz"** — and applied
  (app label, wordmark on splash and viz, fastlane title); this repo did not
  choose it, it implemented it. The paper theme is now the default
  (`ThemeMode.LIGHT`), with the Hyle dark scheme kept as the dark option and
  a Settings selector (light/system/dark). Explicitly per the owner: the app
  shell/landing and the Sources screen keep their existing layouts (they only
  inherit the theme). Two deliberate deviations from the mock pixels, both on
  standing constraints: (a) stream colours stay the CVD-verified topic
  palette — the mock's exact hues include red/green pairs the build-failing
  pairwise ΔE test (brief §8) exists to prevent; (b) the mock's exact
  typeface files weren't supplied, so display roles bind to the platform
  serif ([FontFamily.Serif]) — same live-verification bar as feeds/models;
  swapping in real font files later touches `Type.kt` alone. The mock's
  struck-through "Show Progress" row is honoured as a decision *against*
  that feature. Still open: final package name (rename is
  install-breaking, deliberately not done), icon, license, taxonomy labels.

- **D6 — The owner's second mock drop (2026-07-11/12) restructures navigation
  and finalises the three tints.** Flow map (Figma): splash → **Nooz Stand**
  (no bottom navigation anywhere); the empty Stand's centred plus → **Edit**
  (tabs: Sources / Region & Topics); reading is **immersive** (swipe right =
  stand, swipe left = settings); the Stand's day bar — the loom folded flat,
  candy-cane while empty/fetching — opens the **day loom** by pull-down or
  tap, and the loom's date opens the scrolling **date picker**. Theme is
  three literal surface tints (White / Paper / Dark charcoal — the owner's
  three-theme reference), Paper default; Settings adds a three-step **Text
  Size**; "Show Article Progress" is struck through again and stays unbuilt.
  Reader gestures: two-finger vertical = in-app window brightness (no
  permission — window-level, so text size stays a Settings control),
  two-finger flick = next tint. The interactive references' **ad-libbed
  content** (topic lists like "religion"/"entertainment", fake feed URLs,
  font names like "Hyle Deco Sans", size names "Rice/Peanut/Almond",
  per-sector mix percentages) was deliberately NOT copied: real taxonomy,
  real URLs, plain size labels, and the globe ring / loom weave **real
  counts**. Stream colours remain the CVD-verified palette (same D5
  precedence). The launcher icon + Play `icon.png` come from the owner's
  512×512 logo. Region filtering maps sources' declared starter regions to
  the globe's sectors ("india" → South Asia); URL/OPML additions carry no
  declared region and count as global rather than guessing. The metrics
  block (coverage/breadth/over-under, CrossSectionPanel) now lives under the
  globe on the Region & Topics tab; the weekly columns/hourglass RiverScreen
  and the bottom-nav shell are superseded and removed.

- **D7 — Three real reading fonts, Hyle's own (2026-07-11/12).** The owner's
  direction: "Three font styles (two sans serif, one serif — all from Hyle —
  don't use Hyle Deco)," then, once the design system shipped the real font
  source-of-truth, the five family names: HyleGroteskClassic, HyleGroteskPlus,
  HylePrint, HyleClassic, HyleDecoPro. Ground truth was pulled from the cloned
  `hyle-design-system` repo's new `fonts/` directory (a fetch past the earlier
  clone brought commit `efcef1b`, which added 23 TTFs + per-family
  LICENSE-NOTEs). The three the app uses map exactly to "two sans, one serif,
  not Deco": **Hyle Grotesk Classic** (Space Grotesk + Archivo letterforms) and
  **Hyle Grotesk Plus** (Classic + Deco N/R sweep) are the sans; **Hyle Print**
  (Literata + Deco letterforms) is the serif. HyleClassic and HyleDecoPro (the
  Bitstream pair) are excluded — Deco by name, per the owner. Each family is an
  OFL derivative; every TTF was verified with `fonttools` (real family name +
  weight class, not trusted by filename) before bundling with its LICENSE-NOTE
  and the OFL body (`third_party/fonts/`). `ReaderFont` is now `GROTESK_CLASSIC`
  / `GROTESK_PLUS` / `PRINT`; `Type.kt` binds all three plus makes **Hyle Print
  the display/masthead voice** (wordmark, headlines, list titles, the loom) —
  the single biggest fidelity gain, replacing the platform-serif stand-in.
  Text sizes are the owner's real names — **Rice / Peanut / Almond** (ascending
  real-world size); the earlier plain labels were wrong, and the owner
  confirmed these were never ad-libbed. (This corrects the prior same-session
  note, written before the design system shipped `fonts/`, which had wrongly
  concluded Hyle had no serif and used Archivo/JetBrains Mono.)

- **D8 — Reader "one more cut": fidelity + interaction fixes (2026-07-12).**
  From the owner's screen-recording reference (the launcher's own back-swipe:
  a rigid sheet sliding off a stationary underlayer with a crisp, still,
  shadowed edge) and a bug list. Changes, all in `:feature:reader`/`:app`:
  (a) **Slide** — the immersive Paper now tracks a one-finger horizontal drag
  as a rigid sheet (`graphicsLayer` translation + a hard `shadowElevation`
  edge) and settles past ~a third of the width (right → stand, left → settings),
  else snaps back — replacing the old instant screen-swap. (b) **Eye icon
  removed** — the always-on lens toggle wasn't in the mock and confused the
  owner; the loaded-language highlight moved to a persisted Settings switch
  (`highlightLoadedLanguage`, default off; detection stays real, rewrite stays
  stubbed per D3). (c) **Bottom strip → gradient fade** — the divider/strip is
  gone; controls float over a `verticalGradient` fade of the text, matching the
  Paper mock: a reading-progress dial + open-in-browser + share + reading time
  + the day-mix bar (opens the loom). (d) **Share → newspaper clipping** —
  `NewspaperShare` renders the headline as a paper masthead PNG (Nooz wordmark,
  double rule, real title in Hyle Print, real source · author · date) via a
  `FileProvider`, falling back to text if the image can't be written. (e)
  **Back gesture** — the immersive reader is a sub-state of the Stand, so the
  shell's `BackHandler` was disabled there and the native back gesture exited
  the app; it now also fires when an article is open and closes it. Nothing
  fabricated: the clipping and progress all read from real article/scroll data.

- **D9 — Corrections after the owner tried the build (2026-07-12).** (a) The
  **eye/lens icon is back** in the reader (unmistakable filled eye, dimmed when
  off — not the crossed-out incognito look), wired to the same persisted
  highlight setting Settings shows. (b) The **font picker** matches the mock: a
  vertical list of family names each set in its own face, checkmark on the
  selected — using the real Hyle names, not the ad-libbed "Hyle Deco Sans"
  placeholder. (c) The **splash** matches the mock (legible grey greeked serif
  newsprint, top rule, big left-bleeding lines, fine-print block, wordmark over
  a soft highlight) — it was near-invisible at 7% alpha before. (d) The
  **slide-out is completed**: the real stand list renders stationary behind the
  sliding paper (state hoisted to `ReaderScreen`). (e) The **loom** gained a
  symmetric reverse — an overscroll pull-down dismisses it back to the Stand,
  mirroring pull-to-open, plus a grabber handle.

- **D10 — Clippings (2026-07-12).** A new saved-articles section. A clipping is
  a denormalized snapshot (survives item retention), keyed by item id; Room
  bumped to **v2** (destructive fallback until first release). A bookmark toggle
  in the reader saves/removes; the Clippings shelf (reached from the Stand's
  action row) shows each as a newspaper clipping card, tap to reopen, share
  re-issues the newspaper PNG.

- **D11 — Dictionary lens, non-generative (2026-07-12, per owner's answer).**
  Kindle-style: obscure words underlined **blue**, tap for a meaning; loaded
  language stays **red** (red-vs-blue is CVD-safe). `ObscureWords` (pure,
  tested) gates on a bundled top-30k common-word list (Norvig MIT, trimmed).
  Definitions are **downloaded on request, never bundled or fabricated** —
  `DictionaryCatalog` lists Webster's 1913 (flat JSON, live-verified,
  public-domain); `DictionaryRepository` streams the download (past
  `HttpClient`'s body cap) and lazily looks it up. Blue marks appear once a
  dictionary is downloaded (Settings → Dictionary). No model involved — the
  generative rewrite stays stubbed per D3. **Device-unverified**: the 22 MB
  download + in-memory map is implemented and compiles but wasn't run on a
  device here.

- **D12 — All-region feed expansion + finding tools (2026-07-11).** 43 more
  feeds, each fetched and confirmed live (an all-region verification fan-out),
  covering Americas, Europe & Africa, Middle East & C. Asia, South Asia (beyond
  India), East & SE Asia, Australia & Pacific — 63 verified feeds total, stamped
  `2026-07-11` (`StartersTest` now accepts per-run dates). The larger Sources
  list gained a **bottom-docked search bar**, region **filter chips**, and a
  surfaced **News APIs & builders** section (keyless providers add in one tap
  from their verified example; keyed ones link to key signup). The
  `catalogue.snapshot.json` mirror is left for the P6 sentry to reconcile.

- **D13 — Shared open-resource catalogue (sibling session, 2026-07-11).** A
  parallel Claude session (Sonnet 5) added top-level **`ai-catalogue/`**
  (`models.json` — downloadable GGUF + SD models with live-probed HF URLs;
  `free-tiers.json`; a README consumer-contract and a weekly drift sentry) as
  the constellation's shared source of downloadable-AI-model + free-tier facts
  — the owner's "in-repo JSON catalogue other apps consume." This session did
  not author it and defers to it rather than duplicating; a research fan-out
  here independently verified an overlapping model set, kept as cross-check.

- **D14 — Owner feedback round: fidelity, lens, BYOK, onboarding (2026-07-12).**
  A 22-item pass against the owner's mocks and asks:
  - **Fidelity.** Splash rewritten to bleed greeked newsprint off both edges at
    the measured grey (≈0.32), no highlight box; one shared `NoozWordmark`
    (Hyle Print) for every masthead (Stand, loom, Edit, date-picker, splash);
    Edit header resized (large wordmark + small baseline EDIT). `DayMixBar`
    segments are rounded pills. The loom is a single fixed screen (no scroll)
    whose streams fade to transparent at both ends (DstIn mask), and it recedes
    **upward** into the bar on swipe-up (reverse of the pull-down open). Reader
    body now begins at screen centre with the title just above it; opening an
    article slides it in from the right over a stationary stand.
  - **Lens.** Definitions went Kindle-style: with a dictionary present,
    long-press *any* word (removed the fragile blue-link tap). Loaded language
    is a subtle drawn red underline over normal ink, not link-styled text.
    `define()` gained morphological fallbacks; the loaded-language lexicon was
    expanded across all four categories.
  - **Reader chrome.** An obvious back control on by default; an **immersive**
    setting hides it. Configurable two-finger gestures (on/off). 
  - **Data & about.** `DataExporter` writes the whole local profile (settings,
    filter, sources, clippings, coarse read log) to a JSON file; an About
    section links mdhv.xyz + siblings.
  - **Intelligence (#18).** A real **BYOK** provider (OpenAI-compatible chat
    completions) wired first in both flavor orders; config in private prefs
    (never exported), CLOUD-marked, still vetted by `FidelityGuard`. On-device
    model *execution* stays honestly unwired (no llama.cpp binding), so
    `ModelCatalog` URLs remain unpopulated here even though the sibling's
    `ai-catalogue/models.json` now has probed ones — a download with no runtime
    would mislead. **Follow-up:** move the BYOK key into the Keystore.
  - **Onboarding (#19).** First-run flow (candy-cane "loading", Quick vs
    Advanced, always-Skip), persisted via an `onboarded` flag; Advanced walks
    BYOK + immersive.
  - **Build & reliability.** Release adds resource shrinking + App-Bundle splits
    atop the existing R8. Ported Hyle's device-only crash-recovery (uncaught
    handler → private file; surfaced in Settings; never transmitted).
  - **Edit.** Catalogue is now a plain line/text section (no boxed card);
    clearer active-tab indicator.

- **D15 — Shared model-choice panel (cross-repo feedback, 2026-07-12).** A
  consumer of Nooz's model catalogue independently found the same gap D14
  already logged — no verified download URL/checksum for either
  `ModelCatalog` entry — and, rather than a fake one-tap download button,
  built an honest 3-path chooser in their own onboarding. Applied the same fix
  here: a single `ModelChoicePanel` (on-device / download a model — listed
  with size, explicitly "not available in this build" / bring your own key)
  now backs **both** onboarding's Advanced step and Settings' Reader
  intelligence section — previously only Settings had a (BYOK-only) panel and
  onboarding didn't surface the on-device/download paths at all. Implemented
  as inline stanzas under a chip selector rather than separate screens, to
  stay consistent with the rest of Settings' disclosure pattern.
  **Superseded by D18** — the "Download a model" stanza described here as
  purely informational is now a real download flow.

- **D16 — Aarso wired up as a real `ai-catalogue/` consumer (cross-repo,
  2026-07-12; ported into this lineage 2026-07-24).** Closes the loop D13
  opened: Aarso (`mbaliga/Android-IDE-core`) replaced its two hand-maintained
  `full`/`play` model lists and its own `free_tiers.json` refresh pipeline
  with a fetch of this repo's `ai-catalogue/models.json` +
  `ai-catalogue/free-tiers.json` (bundled fallback + a consented "Update"
  action; `policySafe` drives its Play-vs-sideload split at runtime). Two
  implications: (1) the `ai-catalogue-sentry` weekly probe and the free-tier
  staleness reminder now have a real downstream reader, not just a
  designed-for one; (2) the raw-URL branch Aarso points at
  (`SessionStore.DEFAULT_FT_URL`/`DEFAULT_MC_URL`) is
  `claude/app-build-d1f9s6`, which was this repo's default branch when D16
  was written. **That condition has since fired**: `main` became the real
  canonical branch on 2026-07-23 (PR #1's squash-merge). The old branch name
  still resolves, so nothing is 404ing today, but Aarso's session should be
  told to repoint those URLs at `main` — a later deletion of the stale
  branch would otherwise silently strand that consumer on its bundled
  snapshot.

- **D17 — Free-tier verification pass (2026-07-12; ported into this lineage
  2026-07-24).** A hand-verification pass over every `free-tiers.json` entry
  against its own `sourceUrl` (or best corroborating evidence where the page
  withheld numbers or errored), per the sentry docstring's "a human confirms
  each number" rule: groq/openrouter reconfirmed; cerebras/github_models
  refined (Cerebras's free-tier model lineup changed entirely); cohere
  corrected from a flat "~100 req/day" to its real 20 req/min +
  ~1,000 calls/month cap; deepseek corrected from ~10M to ~5M tokens with
  30-day validity; anthropic/openai `sourceUrl`s updated to where their old
  links now redirect; one new entry (Cloudflare Workers AI, 10K Neurons/day)
  added with a primary-source-verified number. Figures the pass could NOT
  re-verify against a primary source were deliberately left unchanged and
  annotated as such in each entry's own summary, never overwritten with
  secondary-source guesses. Note on lineage: D16/D17 were authored on the
  since-superseded `claude/ide-one-click-downloads-nooz-7lv280` branch (its
  PR #2 diverged from the pre-app init commit and was closed unmerged, not
  mergeable against the real `main`); only these two catalogue artifacts
  were ported out of it — the rest of that branch was a parallel app build
  superseded by what `main` already contains.

- **D18 — Real one-click model downloads, reading this repo's own
  `ai-catalogue/` (owner feedback, 2026-07-12).** D14/D15 left "Download a
  model" informational because the two ids `LocalLlamaProvider` hardcoded
  (`qwen3-4b-instruct-q4`, `gemma3-4b-q4`) never got a verified mirror. What
  was missed: `ai-catalogue/models.json` — this repo's *own* shared,
  live-probed catalogue (D13) — already carries 18 *other*, real,
  HTTP-200-verified entries; the two unverified ids are just two of its many
  rows, not the whole catalogue. New `:core:data` `ModelCatalogueRepository`
  bundles a snapshot asset (`ai_catalogue_models.json`) as the offline/
  first-run default and refreshes from the repo's own raw-GitHub URL only on
  explicit tap (never automatic — the catalogue's own consumer contract).
  `ModelChoicePanel`'s download stanza now lists every `policySafe` `LLM_GGUF`
  entry with a real `downloadUrl` (7 of them, 1.1–9.1 GB; the 3 abliterated/
  uncensored variants and the SD image checkpoints are filtered out —
  irrelevant and inappropriate for a neutral-language rewrite feature) with a
  real streaming download, live progress, a storage-budget check before
  starting, and delete. `LocalLlamaProvider.isAvailable()` no longer checks a
  fixed 2-id catalogue — it just asks "is any `.gguf` on disk," so it
  correctly reflects whatever the repository downloads. The old
  `core/inference` `ModelCatalog`/`ModelSpec`/`ModelState` types are deleted;
  `ChecksumVerifier`/`StorageBudget` remain as generic utilities (the latter's
  `canDownload` now takes a raw size, not a `ModelSpec`). Execution is still
  the honest gap (§ above) — a downloaded model sits on disk, not yet run.

- **D19 — Owner feedback round: Hyle Print glyph fix + 9 numbered bugs
  (2026-07-12).** (a) **Font glyphs, not a missing image.** The owner's
  splash/Edit-header complaint ("still does not use the right image") and the
  "alphabets not in caps as they need to be" complaint are the same root
  cause: Hyle Print's bundled TTFs had a foreign glyph spliced into "N" and a
  mirrored "R" (D7's font drop carried the defect in silently). Every
  wordmark/headline on screen renders through Hyle Print, so the one glyph
  bug looked like several different bugs. Re-bundled the corrected TTFs from
  `hyle-design-system@4c63219`; there was never a raster logo asset to find,
  and none was added. (b) **Reader flow** breaks: the Settings-drag exit
  never called `closeItem()`, and the slide-offset remember was declared
  behind an early return with no reset path on a system-back close; both
  fixed with one unconditional `LaunchedEffect(selected)`. (c) **Loom empty
  day**: draws a dotted silhouette fan when `totalRead == 0` instead of
  nothing. (d) **NewspaperShare**: bylines stack and ellipsize instead of
  colliding; the bitmap height formula now shares the exact constants the
  draw pass uses. (e) **DayMixBar**: clips to one capsule silhouette so
  internal segments are contiguous and hard-edged (only the two end-caps
  rounded); the reader's top bar now plots the day's *read* mix, not the
  supply mix. (f) **Edit globe "missing"**: root-caused, not cosmetic —
  `TabLabel`'s underline `fillMaxWidth()` sat in an unconstrained `Column`,
  which passes the parent Row's whole remaining width straight through, so
  "Sources" claimed the entire row and "Region & Topics" (and the globe
  under it) had zero width left to lay out in; fixed with one
  `Modifier.width(IntrinsicSize.Min)`. This is the same defect the owner had
  already flagged once before — the earlier pass addressed tab *visibility*
  without catching this constraint-propagation cause, hence "after repeated
  specific requests." (g) **Date nav**: explicit prev/next-day chevrons in
  the loom header, bounded to never step past today. (h) **Dictionary
  formatting**: new `DictionaryFormatting` parser recovers the bundled
  Webster's-1913 text's real structure — numbered senses restart at 1 per
  part-of-speech; "Syn. --" closes a group — from what ships as one flat run
  with no line breaks at all; `DefinitionSheet` renders it with a hanging
  indent, bold sense numbers, italic domain tags and synonym blocks. (i) Two
  prose fixes from a full-app spellcheck pass (aspell + manual read): "site
  url" → "site URL"; a broken sentence in the model download stanza's
  description.

- **D20 — Owner feedback round: density slider, clippings board, Nooz Flash,
  date range, region-band picker (2026-07-12).** (a) **View density**: new
  `ListDensity` (Detail/List/Small tiles/Big tiles), a shared `DensitySlider`
  on the Stand and Clippings, hidden in immersive mode where a pinch gesture
  steps it instead (`pinchDensityDelta`, `densityPinchModifier`). (b)
  **Clippings board**: Detail is now a torn-paper board — each `Clipping`
  gets a jagged top/bottom tear (`GenericShape`) and a small rotation, both
  seeded from its own id so they're stable, not re-rolled every recomposition
  — on the app's own paper tone regardless of the active theme, since a
  clipping is paper first; List collapses to one compact line. (c) **Reader
  peek**: dragging an article back towards the Stand now rests at a partial
  position instead of going fully off-screen; a further drag or the explicit
  back control still closes it. (d) **Nooz Flash**: a new `digest()`
  capability alongside `rewrite()` on `InferenceProvider` — Local/Urbana/
  MlKit stay honestly stubbed (no real runtime exists for any of them yet),
  Byok gets a real implementation sharing its HTTP call with `rewrite()`.
  `AppContainer.flashRouter` is on-device-first with Byok as the *only*
  fallback, deliberately excluding Urbana — narrower than the main lens's
  chain. Compresses today's flowed headlines (matching the standing filter)
  to 10 words or fewer; "go deeper" reveals the real headline list rather
  than a second, ungrounded generation. (e) **Date picker**: the Stand's date
  now opens the loom's full picker (previously inert text); today is the
  filled circle, the standing selection is outlined — the owner's own
  correction, the two were swapped; a Range chip adds a two-tap range flow,
  and the loom weaves the range's *summed* totals (plain addition over each
  real day's counts). (f) **Region-band picker**: `Region.forBand` names and
  blends every sector a widened pinch band actually spans, by sampling the
  already-correct `Region.forLongitude` rather than re-deriving wraparound
  math — before, only the exact-centre sector or "Global" ever showed,
  nothing in between. (g) **Splash collision**: the wordmark now sits on a
  page-coloured masking background so it doesn't visibly collide with
  whatever greeked backdrop text lands behind it. (h) **Section headings**:
  a shared `SectionHeading` (caps + letter-spaced, the app's own SETTINGS/
  EDIT chrome voice) replaces plain muted-body-text headings across Settings,
  Edit, Onboarding, and the loom's month grid. (i) Dictionary: a lone
  unnumbered sense no longer hangs-indents its wrapped lines past the first
  line — that indent only makes sense relative to a number the sense doesn't
  have.

- **D21 — Lift-and-part reader navigation + owner course-correction
  (2026-07-13).** (a) **Lift-and-part**: replaced the reader's slide-and-peek
  rig with the owner's spec'd physics — an edge-origin-only one-finger drag
  (~24dp zone; interior swipes no longer hijack reading) drives one
  `progress` float directly off the finger (no `animate()` mid-drag): Paper
  scales toward 90%, rounds its corners, gains a shadow and translates, while
  the room being entered — Stand left, Settings right — slides in under it
  at the same rate. Only release triggers an eased `tween(300,
  FastOutSlowInEasing)` settle, past a 45%-drag or fast-flick threshold, to
  either a parked floating card (≥64dp always showing, tap/drag-interactive)
  or back to full Paper. Settings is now an embedded room (a `settingsRoom`
  slot lambda supplied by the app shell, since `feature:reader` can't depend
  on wherever `SettingsScreen` lives) rather than a nav-switch destination,
  so parking towards *either* side never destroys `ReaderDetailScreen`'s
  `LazyListState` — the reader's exact scroll position survives every trip.
  Hardware back now unwinds one step at a time (parked → full Paper → closed)
  via a `BackHandler` owned by `ReaderScreen` itself; the app shell's own
  `BackHandler` only ever leaves a non-Stand screen. No Aarso/FoneBru
  precedent existed in this repo to reuse (checked; the only matches were an
  unrelated sister-app name and an unrelated code comment). (b) **Course
  correction, owner's direct feedback**: the density slider/pinch feature
  from D20(a) is fully removed — `ListDensity` (model, DataStore key,
  `DensitySlider`/`DensityViews`), and the Stand/Clippings back to their
  single detail-list rendering, per "I like the previous view better with
  the detailed list. That's default and it's fine." Clippings keeps its
  D20(b) torn-paper board (that's the detail rendering itself, not the
  removed control). (c) **Region & Topics tab wrap**: `TabLabel`'s
  `IntrinsicSize.Min` sized the tab to its *longest word's* width, not its
  one-line width — invisible for "Sources" (one word) but wrapped "Region &
  Topics" across lines; fixed to `IntrinsicSize.Max` + `maxLines=1`. (d)
  **Splash overflow**: the display block's greeked text is longer than its
  `weight(1f)` box is ever tall on a real phone, and Compose doesn't clip a
  Text's paint to its own layout bounds by default — the unfit lines were
  drawing straight through into the fine-print block below. Fixed with an
  explicit `.clipToBounds()`. (e) **Extraction stubs / rendering polish**:
  `ArticleExtractor` now trusts an explicit semantic content container
  (`itemprop=articleBody`, common CMS classes, bare `<article>`/`<main>`)
  before falling back to density scoring, credits a matched element's
  grandparent at half weight alongside its direct parent (many templates
  wrap *every* paragraph in its own one-off `<div>`, which previously
  isolated each paragraph into its own single-item "container" and
  collapsed extraction to just one paragraph), falls back further to bare
  leaf `<div>`s when nothing is semantically marked up at all, and keeps
  `<li>` content (bulleted) instead of silently dropping list-based
  how-tos/explainers. `ReaderDetailScreen` also adds a touch of extra
  bottom padding between paragraphs so breaks read as distinct blocks
  rather than barely-more-than-a-line-gap.

- **D22 — Paper grain + Nooz Flash discoverability (2026-07-13).** (a)
  **Paper grain**: a new `PaperGrain` setting (None/Fine/Coarse — "simplistic
  and minimal," a fixed three-step choice rather than a slider), a
  `Modifier.paperGrain()` in `core:design` that seeds a deterministic
  speckle field once per size via `drawWithCache`/`onDrawBehind` (never
  re-rolled per recomposition, never drawn over the content itself — behind
  it, like the background it sits on), applied to Paper's own background in
  `ReaderDetailScreen` and to Clippings' torn-paper cards (clipped to the
  same jagged shape as the card itself, so no speckle spills past a torn
  edge). None is the default; nothing changes until the reader picks a
  grain in Settings. (b) **Nooz Flash discoverability**: the owner's follow-up
  — "unfindable" — traced to the feature reading as ordinary muted chrome
  once enabled, not to the Settings toggle itself (already visible without
  expanding "Reader intelligence"). `FlashCard` now gets a bordered card
  container and a bolt mark across every state so it has a distinct
  silhouette on the Stand. Also added: a Play button (device text-to-speech,
  `android.speech.tts.TextToSpeech` — on-device, no network, matching the
  feature's own stance) that reads the compressed flash line aloud, tapping
  again stops it early.

- **D23 — Reader/UI polish round from owner screenshots (2026-07-13).** (a)
  **Room overlap**: the parked lift-and-part card was drawn over a *full-width*
  revealed room, so the Stand's right edge / Settings' left edge sat hidden
  behind it (Settings labels visibly clipped). Rooms are now stationary
  ("sits still behind," per the original mock) and inset by the 64dp peek on
  the parked side, so the card lifts and parts to reveal a room laid out
  *beside* it, never behind it. (b) **Byline collision**: source (left) and a
  long author list (right) overlapped — `SpaceBetween` can't prevent a collision
  when both are long. Each now takes a weighted half and wraps/ellipsises within
  it. (c) **Region & Topics tab**: still wrapped to three lines on-device despite
  the `IntrinsicSize.Max` + `softWrap=false` attempt. Replaced the whole
  Column+fillMaxWidth-underline+intrinsic approach with a single Text that draws
  its own underline via `drawBehind` at exactly `size.width` — no intrinsic
  measurement in the path at all. (d) **Read-distribution skew**: re-opening the
  same article kept incrementing its topic in the Stand's read bar; `todayReadMix`
  now counts each article once (the raw events still drive the dwell buckets). (e)
  **Unseemly errors**: the router no longer enumerates internal provider ids in
  user-facing failures (`"no reader-intelligence provider is available"`);
  `FlashUiState.Unavailable` carries a `needsSetup` flag so the card shows an
  actionable "add one in Settings" hint instead of "tried: local-llama, byok".
  The article Fallback ("Couldn't extract…") is reworked to present the summary
  as the body plus a plain "Read the full story at the source ↗" link, rather
  than an apologetic error line. **Still open** (logged, not built this round):
  Google-News-style aggregator links are redirect pages we can't read through,
  so they still fall back to summary-only; and the owner's framing/omission
  contrast-graph idea (highlight how sources frame the same event differently;
  configurable viewed-vs-available views) is a larger feature awaiting a scope
  decision.

- **D24 — Omission contrast dashboard, phase 1 (2026-07-13).** The owner's
  contrast idea, scoped to three phases (their call: "all three, phased"):
  (1) an omission dashboard — supply vs consumption, starkly — built now;
  (2) a filter-vs-reality view; (3) a cross-source framing lens (blocked on
  real inference). Phase 1: `ContrastPanel` is the loom's stark counterpart —
  a `Loom | Contrast` toggle in `LoomScreen` swaps the woven canvas for a
  blunt per-topic ledger reusing the *same* selected-day/range aggregate
  (`streamCountsByTopic` set against `readCountsByTopic`). Each topic shows a
  faint "flowed" bar over a solid "read" bar on one shared scale, so the gap
  is the omission, read at a glance; a headline states the plain ratio ("read
  N of M — X%"), a callout names the widest blind spot, and a Flowed/Read/Gap
  sort control is the configurable lever for "what this view is about." Supply
  is never filtered here, same as the loom — omission is the subject.

- **D25 — Contrast phases 2–3 + reader bar (2026-07-13).** The owner asked for
  all three contrast phases; 2 and 3 land here, giving the loom a three-way
  toggle: **Loom | Contrast | Framings**. (a) **Phase 2 (filter vs reality)**:
  the Contrast view gains a "Reach" funnel at the top — everything that
  **flowed** → what your **filter** let through (exact by topic; region shown
  in the filter label) → what you **read** — as three nested bars on one scale,
  with the two omissions named in plain counts ("your filter set aside N", "you
  read R of the M it let through") plus an enabled-vs-delivered-vs-quiet sources
  line. Reuses the loom's own `filter`/aggregate/`enabledSourceCount`. (b)
  **Phase 3 (framings)**: `StoryClustering` (`core:model`, pure + unit-tested)
  groups the selected day's headlines that ≥2 different sources covered, by
  shared significant words (union-find over keyword overlap, stopwords/short
  words dropped). `FramingsPanel` shows each such story as a card with every
  outlet's headline verbatim, side by side — the "invades" vs "cross into"
  comparison, made by the reader. Clustering is fully on-device; the
  *automatic* marking of which words are loaded stays the lens's job and waits
  on a real inference provider (honest stub). `LoomViewModel` gained
  `itemRepository` (only recent, un-pruned items survive, so framings are a
  recent-days feature); tapping a framing opens that article in the reader. (c)
  **Reader bottom bar**: now shows the reader's own today **read** mix
  (`todayReadMix`), consistent with the Stand's top bar, instead of the ambient
  supply mix (owner).

- **D26 — Minimal contrast redesign + owner polish batch (2026-07-14).**
  (a) **Contrast, redrawn minimal** (owner's assessment-report references —
  one muted ink, thin marks, wide whitespace): the Reach section is now a
  single *nested funnel bar* (flowed = faintest full width, filter = mid over
  it, read = solid over that, so the bar just shrinks through the two
  omissions) with a spare number legend; By-topic is now a *dumbbell* per topic
  (a hollow dot for its share of the stream, a solid dot for its share of your
  reading, a hairline between = the gap). Framings dropped its filled cards for
  hairline-separated groups. (b) **#1 model "wiring error"**: a downloaded model
  made `LocalLlamaProvider.isAvailable()` true, so the router picked it and its
  stubbed call failed *every time* — surfacing the raw wiring message. Availability
  is now gated on a `RUNTIME_WIRED` flag (false until a real llama.cpp binding
  lands), so the router falls through cleanly to a configured key or the honest
  "needs setup" message; a model on disk is still detected (`hasModelOnDisk`) for
  when the runtime lands. The lens's rejected-rewrite copy is muted, not alarming
  red. (c) **#5** builder/API adds are a bare plus (or key) icon, not a text
  button. (d) **#6a** the region globe spins horizontally only (vertical pan
  dropped). (e) **#7** the reader's edge-to-open zone widened 24dp→56dp, past the
  OS back-gesture strip, so the Stand/Settings rooms open easily. (f) **#10** the
  loom's grabber handle is gone (swipe-up + system back still dismiss) —
  immersive. **Logged, not built this batch** (architectural / sequenced next):
  #8 parked home (list + peeking reader, first-run manual in the pane), #9 one
  fixed date position, #2 splitting reader-quick vs full settings, #3+#6b moving
  region/topics into the contrast space with the globe opened as a read-by-region
  heatmap. **Blocked**: #4 fetching arbitrary past dates — live RSS feeds carry
  only recent items, so historical days can't be back-filled from them (a GDELT
  date-query path could cover only GDELT-builder sources).

- **D27 — Architectural cluster from the owner batch (2026-07-14).** (a) **#8
  parked home**: the Stand's home state is now the parked lift-and-part layout —
  the list with the most-recent article resting as a peeking card. The rest is a
  real reader (`openItem(rest=true)` loads text but records no read; `markEngaged`
  records the glance only when the peek is brought in full; `closeItem` skips a
  never-engaged peek). No content → the full Stand, whose empty state is the
  first-run guidance. (b) **#2 settings split**: `SettingsScreen(compact=true)` is
  the reader's right room — theme/font/size/grain/reading-time/lens/immersive plus
  a "More settings" door; the full page (dictionary, models, gestures, data,
  about) stays behind that door and the Edit gear. (c) **#9 date + B5/B6 header**:
  the loom header is one row matching the Stand — wordmark + "N source(s)"
  baseline-aligned left, the date always top-right with day-step chevrons; the
  region label moved out of the header. (d) **#3 + #6b region/topics + globe
  heatmap into Contrast**: `Region.forSourceTag` now folds the catalogue's
  europe/africa tags into the EUROPE_AFRICA sector; the Contrast view opens with a
  "Regions" section — the earth unrolled to a longitude **read-heatmap** (each
  sector shaded by how much you read from it, aimed sector outlined) plus region
  and topic chips that write the standing filter (`LoomViewModel.setRegion`/
  `toggleTopic`, fed by a new `readEvents` flow + `readEventRepository`). The
  Edit Region & Topics tab still exists as the precise picker; a full relocation
  can follow. **Still open**: #4 (past-date fetch — RSS-blocked, explained) and a
  polish batch (coarse grain, header gradient falloff, richer empty states,
  squished Edit header).
- **D28 — Polish batch B1–B4 (2026-07-14).** (a) **B1 coarse grain**:
  `PaperGrain` speckles now carry a per-dot *varied* radius (a min/max span) and
  the coarse step is denser (7dp weave) with small dots, so it reads as a coarser
  paper *grain* rather than the sparse uniform "big ugly dots" a naive bigger-dot
  step gave. (b) **B2 header falloff**: new `Modifier.topFadingEdge(active)` in
  `:core:design` — an offscreen `BlendMode.DstIn` alpha mask that dissolves the
  top of a scrolling region into its header instead of a hard clip line. It's
  gated on the scroll state's `canScrollBackward` so the top rows stay crisp at
  rest and only fade once something scrolls up under the header. Applied to the
  Stand list, Settings, Clippings, and both Edit tabs. (c) **B3 empty states**:
  the shared `EmptyState` gained a faint **omission emblem** (a blank sheet whose
  middle rule is a stub — the story that isn't there) and now actually backs the
  previously-plain-text empties (Clippings, Framings, Contrast — `fill=false`
  inline, Edit no-match); `EmptyStand`'s big plus already qualified. (d) **B4
  squished Edit header**: the "Nooz EDIT" masthead is now its own inner
  baseline-aligned Row (matching the Stand/loom), so the 48dp Settings/DONE
  controls no longer share a baseline line with it and drag it off-centre.
- **D29 — Edit/Settings merge, globe restored to Contrast, Framings paused,
  literal no-results state (2026-07-15).** (a) **Edit absorbs Settings**:
  `EditTab` gains a third `SETTINGS` case; `EditScreen` takes a caller-supplied
  `settingsTab: @Composable () -> Unit` slot (the same cross-module pattern as
  `ReaderScreen`'s `settingsRoom`) instead of an `onOpenSettings` callback, so
  the full settings render inline as a tab rather than behind a gear (owner:
  "it needn't be in the settings cog"). `SettingsScreen.kt` splits into a thin
  `SettingsScreen` (Scaffold + back arrow) and a new `SettingsBody` (the actual
  content, reusable without that chrome) — MainActivity wires
  `settingsTab = { SettingsBody(vm = settingsVm, compact = false) }`. (b) **Cog
  relocates to the Stand**: the region/topics summary text ("Global | All") is
  now clickable and opens Edit's Sources tab directly ("the current sources
  thing"); the settings-cog IconButton sits where the old "EDIT" text button
  was and opens Edit's Settings tab. `MainActivity` tracks `editStartTab`
  (`EditTab`, `rememberSaveable`) to steer which tab Edit lands on.
  `ArticleListScreen`/`ReaderScreen` gained an `onOpenEditSettings` callback
  threaded alongside the existing `onOpenEdit`. (c) **The globe is back in
  Contrast**: `GlobeCanvas` moved from `feature/sources` into `:core:design`
  (both `feature/sources` and `feature/river` already depend on it — moving
  avoided a module cycle) and gained a `heatByRegion: Map<Region, Int>`
  parameter — when set, the ring draws one arc per region (sized by its real
  longitude span, shaded by relative read volume) and the dot cloud shades by
  the same per-region heat instead of by topic-ring band membership. Contrast's
  `RegionsSection` now spins the *same* globe (local yaw/pitch/bandHalf state
  mirroring Edit's `RegionTopicsTab` exactly) instead of the flat
  `RegionHeatStrip` bar, which is removed. (d) **Framings paused**: the
  clustering matched unrelated articles (a Brazil-sovereignty editorial next
  to an EU/ICC story; a China car-export report next to an unrelated movie
  review) — `LoomMode.FRAMINGS` is removed from the mode toggle so the tab is
  unreachable; `FramingsPanel.kt`/`StoryClustering.kt` are untouched and ready
  to re-wire once the clustering itself is fixed. (e) **Tab underline
  breathing room**: `EditScreen`'s `TabLabel` padding grew (top=xs,
  bottom=md) and a small `Spacer` now separates the tab row from the
  `HorizontalDivider` beneath it — the rule no longer reads as fused to the
  divider. The tab row also picked up `horizontalScroll` now that it carries
  three tabs. (f) **Literal no-results state**: new `NoResultsState` in
  `:core:design` — the owner's own exclamation-mark illustration (alpha-masked
  from their upload into `res/drawable-nodpi/img_no_results.png`, tinted via
  `ColorFilter.tint` so it reads on every theme), a fixed "No results found"
  headline, and one line drawn from a small set of original, unattributed
  quotes (never fabricated and pinned to a real person). Distinct from
  `EmptyState`: that one explains *why* a list is empty with actionable
  copy (no sources, nothing clipped); `NoResultsState` is for a search/filter
  that came up genuinely empty — wired into Edit's Sources search-empty case.
- **D30 — Stand-list cluster: coverage mix, scroll position, article TTS,
  read marking, first-run illustration, quick setup (2026-07-15).**
  (a) **Globe correction**: D29(c)'s spinnable read-heatmap globe was a
  misread — the owner meant the *flat* unwrapped strip (`RegionHeatStrip`,
  restored) with no spin; it "just wasn't showing" because a day with zero
  reads rendered as near-invisible uniform fill. Fixed: the strip now always
  draws a visible outline regardless of data, and a day with nothing read
  yet shows an explicit "No reads yet today" line instead of a blank-looking
  bar. `GlobeCanvas` reverted to its pre-heatmap form (the `heatByRegion`
  path had no remaining caller). (b) **Mix for coverage**: new
  `core/model/Diversifier` — a deterministic round-robin-by-source reorder
  (not a random shuffle) — toggled from a new shuffle icon on the Stand,
  next to the settings cog. (c) **Scroll position**: a thin track+thumb
  (`ScrollPositionBar`) floats over the bottom of the Stand's list, sized to
  the visible fraction and positioned by scroll offset; hidden once
  everything already fits one screen. (d) **Per-article listen**: the
  Flash-only `PlayFlashButton` (FlashCard.kt) generalized into
  `PlayTextButton(text, playLabel)`, reused by a new play control in
  `ReaderUtilityBar` that reads the *loaded article body* aloud (chunked past
  TTS engines' per-call length ceiling), not just Nooz Flash's compressed
  line — on-device TTS, no network, available regardless of the Flash
  setting. (e) **Read marking + immersive pinch-filter**: new
  `AppSettings.readMarkStyle` (`GREYED` default / `STRIKETHROUGH`) and
  `unreadPinchFilter` (on by default) — `SourcesRepository`-style DataStore
  keys, a `SettingsBody` selector row, and a `ReaderViewModel.readIds` flow
  (every item with ≥1 read event). `ItemRow` dims or strikes its own title;
  a hand-rolled pointer-event pinch detector (`PointerEventPass.Initial`,
  non-consuming — `detectTransformGestures` would have eaten the list's own
  scroll drags) toggles an unread-only filter, with its own honest "nothing
  left unread" state when the filter empties the list. (f) **First-run
  illustration**: the owner's needle-and-thread image (many strands into one
  weave — the app's own loom idea) replaces the plus-in-a-circle for the
  zero-sources `EmptyStand` case; background removed via near-white alpha
  thresholding (unlike the exclamation mark, colour is kept, not tinted).
  Tapping the illustration opens Sources. (g) **Quick setup fixed**:
  onboarding's "Quick setup" used to alias straight to "Skip" (`onQuick =
  onFinish`, identical to `onSkip`) — it added nothing, matching the owner's
  "isn't doing any setup at all." `SourcesViewModel.quickSetup()` now adds
  five verified, editorially varied global outlets before finishing.

- **D31 — Real llama.cpp binding: Nooz Flash's on-device runtime wired
  (2026-08-05).** The owner's explicit ask, after Play rejected the app twice
  and separately asked "does Flash work" — no half-measure accepted. Closes
  the gap D14/D18/P5 all logged as the highest-value remaining P5 work: there
  was genuinely zero llama.cpp integration anywhere in the repo (`RUNTIME_WIRED
  = false` hardcoded, no `.so`, no NDK/JNI config — confirmed by a repo-wide
  grep before starting). Unlike Nooz Cast's onnxruntime-android (a prebuilt
  Maven AAR), llama.cpp has no such artifact, so `core/inference/src/main/cpp`
  now vendors the real upstream source via CMake `FetchContent`, pinned to
  commit `360e1349f0009c5ad99d21e3c4546b707addc68a` — built from scratch as
  part of `:core:inference`'s native build (first NDK/CMake build in this
  project; a clean build now takes several minutes longer). The JNI bridge
  (`nooz_llama_jni.cpp`) and the Kotlin wrapper (`LlamaCppEngine`) are adapted
  from llama.cpp's own maintained Android reference
  (`examples/llama.android`), cloned locally and read at the exact pinned
  commit rather than trusted from memory — that C API moves fast enough that
  guessing signatures would have been a real risk. Trimmed for Flash's actual
  shape (short single-turn rewrite/digest, never a chat): no streaming Flow,
  no benchmark harness, a smaller 4096-token context than the reference's
  8192. `LocalLlamaProvider` now loads whichever `.gguf` is on disk and runs
  real inference through it; `PromptTemplates` (new, shared with
  `ByokProvider`) keeps the on-device and cloud paths sending a model the
  identical wording for the same capability. `ReaderViewModel`'s
  `FLASH_COMING_SOON` flag — added specifically so Flash could be shown
  honestly instead of inviting a tap that could only fail — flips back to
  `false` in this same change, exactly as its own doc comment said it would.
  Adversarially reviewed (a second pass re-reading the pinned-commit reference
  source line-by-line against every JNI signature, CMake target name, and C
  API call in the new code) before pushing, since this sandbox has no local
  JDK/NDK to compile-verify — CI's native build is the only real compile
  signal. See the P5 section above for what's still honestly unverified
  (real-device output quality).
- **D32 — Today in History + a real adaptive launcher icon (2026-08-12).**
  The owner proposed a batch of newspaper page furniture (crossword, comics,
  spot-the-difference, today-in-history) and asked for a read on it before any
  implementation. Only **Today in History** was built, and the reasoning is
  worth keeping: comics are a rights problem, not a code one (every strip
  worth having is copyrighted, and this app was mid-Play-rejection when it was
  proposed); spot-the-difference would mean doctoring news photographs inside
  a product whose whole premise is fidelity, with `FidelityGuard` existing
  specifically to stop invented content; a crossword is genuinely interesting
  in its Nooz-native form (cloze-deleted from *today's own headlines*, so it
  quietly asks whether the reader actually read them) but the interactive grid
  is a week-plus of work and belongs after the app has an audience. Today in
  History, by contrast, is thesis-aligned rather than bolted on: the app
  already shows what a reader missed across *sources*; this asks the same
  question across *time*.
  - `TodayInHistory` (`:core:model`, unit-tested like [Diversifier]): tidies
    Wikipedia's blurbs (their "(aircraft involved pictured)" asides point at
    an illustration this column doesn't show, so they come out) and picks the
    column by sampling evenly across the whole returned span — the feed is
    newest-first and front-loaded with recent decades, so taking the first
    five would have made "today in history" mean "today in the last thirty
    years". Deterministic, oldest-first, like a printed almanac.
  - `TodayInHistoryRepository` (`:core:data`): Wikimedia's own curated
    `onthisday/selected` endpoint, cached per calendar day. **It is the first
    fetch this app makes to a destination the reader didn't choose** (the
    manifest's own note: "The user's own fetches to their own chosen sources.
    No other egress"), which is why it ships **off by default**, names
    Wikipedia plainly in its settings row, and carries a UA identifying the
    app with a contact URL per Wikimedia's policy. CC BY-SA is credited in
    view under the column, not buried in About.
  - Deliberately **never links an event to today's headlines**. Asserting that
    a 1971 decision "echoes" today's would be exactly the invented connection
    `FidelityGuard` exists to prevent; it sits beside the news and lets the
    reader draw the line, the way Contrast already behaves.
  - **Launcher icon.** The owner supplied new pixel-art artwork (black field,
    after a first light-grey version that would have dissolved into a light
    home screen). The deeper find: this app had **no adaptive icon at all** —
    only a flat `mipmap/ic_launcher.png`, despite minSdk 31, so every device
    it runs on was wrapping the artwork in a system-drawn plate and shrinking
    it. That, not the artwork, was the "visibility problem". Now a real
    `adaptive-icon` (foreground + black background + a filled-silhouette
    `monochrome` layer for Android 13+ themed icons), sized so the furthest
    *content* pixel lands just inside the strict circle mask (the tightest
    common shape) while still filling ~87% of the visible aperture. Verified
    by rendering circle/squircle/themed masks and 48-192px previews before
    committing.
  - Also corrected stale copy that outlived D31: the Flash settings row and
    `ModelChoicePanel`'s on-device stanza both still said the on-device
    runtime wasn't wired.

- **D33 — Web reader: iPadOS lead-image flicker, and the real shape of
  /api/article's 502s (2026-08-20).** Two web-reader glitches from the same
  session. **(a) The flicker** (commit `b6975d6`): reported from a 25s
  iPadOS DuckDuckGo recording, the open article's lead image was popping out
  and back in about once a second with no user interaction. Root cause was
  `ensureArticle()` ending in a bare `rerender()` on every completion,
  including the ones from the background prefetch pump (concurrency 3, up to
  60 items queued) — so a full stage rebuild fired once per background item
  for however long the queue took to drain, and each rebuild recreated the
  on-screen `<img>` from a bare `src`. Chrome keeps the decoded bitmap hot
  across that; WebKit re-decodes the fresh node regardless, which is the
  flicker. Fixed with a completion rerender policy (`js/app.js`:
  immediate rerender only if the resolved item is on screen, skip entirely
  in views with no article bodies laid out, otherwise coalesce into one
  trailing rerender per burst), `<img>` node reuse keyed by `src` in
  `js/images.js` so a same-content rebuild moves the existing node instead
  of recreating it, a resize listener that only re-lays-out on a real width
  change (iPadOS fires height-only resizes as its toolbar collapses on
  scroll), and `style.css` dropping the stage's permanent `will-change:
  transform` plus switching its height rules from `dvh` to `svh` so the
  collapsing toolbar stops perturbing layout. **(b) /api/article's 502s**:
  the prior session's write-up (see D32-adjacent commit `b6975d6`'s message)
  had traced production 502s to a bare, headerless `error code: 502` body
  with no `x-vercel-id` — not this function's own JSON — and guessed the
  timeout race was the cause. That guess was wrong. Probing
  `?url=https%3A%2F%2Fdefinitely-not-a-real-host-zzz.invalid%2Fx` — a target
  that can only fail inside the function's own `catch` block, never near a
  gateway timeout — still came back as that same bare Cloudflare body with a
  `cf-ray` header. Production sits behind Cloudflare in front of Vercel, and
  Cloudflare replaces *any* origin 502 with its own edge error page,
  unconditionally — so every honest per-item error this function ever
  produced was being masked at the edge, regardless of timing.
  `api/article.js`'s three upstream-failure paths (non-OK status, non-HTML
  content-type, fetch/timeout catch) now return `200 + {error, html: null}`
  instead of `502`/`415` — the same shape the existing extraction-failure
  path already used, which the client (`ensureArticle()` in `js/app.js`)
  already treats as the per-item `'error'` state for any response lacking
  usable `html`, `dbPutArticle` included. `TIMEOUT_MS` stays at 8000 (it
  still keeps the function under the platform's own gateway limit) but its
  comment now states the real finding instead of the superseded race
  theory. Left open, and out of this repo's reach: *why* some sites'
  upstream fetches fail from Vercel's IPs at all — reuters.com and wsj.com
  failed in under a second in the earlier probing, which reads like
  bot-blocking rather than a timeout, but that can't be confirmed from
  here.

- **D34 — Dark mode: a follow-the-phone tint, and system bars that agree with
  it (2026-08-31).** Reported from a Galaxy S26+: in dark mode the app's text
  stayed black and unreadable. Three separate causes, all of them the same
  underlying mistake — *two different components each answering "is it dark?"
  their own way*.
  **(a) No follow-the-phone mode at all.** `ThemeMode` was three literal tints
  (White/Paper/Dark), all manual, defaulting to Paper — a deliberate call at
  the time (see the enum's own doc), but it means a phone in dark mode got the
  light app and no obvious way to discover otherwise. Added
  `ThemeMode.SYSTEM`, resolving to Paper by day and the charcoal Dark tint by
  night, and made it the **default for new installs**. The three tints are
  untouched and an existing reader's persisted choice is read straight back out
  of DataStore, so nobody's setting changes under them. `"system"` — a key from
  the first theme iteration that had been aliased to Paper — now means what it
  always said.
  **(b) System-bar icons followed the OS while the app followed the setting.**
  `enableEdgeToEdge()` with no arguments uses `SystemBarStyle.auto`, which reads
  `Configuration.UI_MODE_NIGHT`. With the app on light Paper and the phone in
  night mode, that put *light* status/nav icons on a *light* surface (and the
  converse for a hand-picked Dark tint on a day-mode phone). Bar appearance is
  now driven from the app's own resolved tint via `WindowCompat` in a
  `SideEffect` (`MainActivity`), so there is exactly one answer to "which
  surface is this".
  **(c) OEM force-dark was left enabled.** The activity theme is now
  `android:forceDarkAllowed=false`. One UI offers to force-darken apps it
  believes have no dark theme; force-dark re-colours what it can reach and
  leaves Compose's own drawing alone, which lands as near-black text on a
  darkened field — exactly the symptom reported. Nooz ships its own tints, so
  it has nothing to gain from the OS doing this and everything to lose.
  Also: the pre-Compose window background was hardcoded `#121212`, a value
  matching no surface the app actually paints (the Dark tint is `#262624`). It
  is now night-qualified — paper by day, the real charcoal by night — so the
  first frame matches the tint about to be painted instead of flashing.
  `Theme.kt` resolves `SYSTEM` in exactly one place and every colour derives
  from that. Verified by `:core:model` unit tests (`resolve`/`isDarkSurface`/
  `next` invariants, including "SYSTEM never survives resolution" and "a flick
  never lands back on SYSTEM"); 159 model tests green locally. **Honestly
  unverified here:** the on-device result on an actual S26+, since this sandbox
  has no Android SDK or emulator — the reporter's device is the real check.

- **D35 — India regional-language starter pack, re-verified from a contributed
  patch (2026-08-31).** A contributor (via the owner) sent a patch adding 28
  India regional feeds. Its own STATE entry conceded the Gradle suite could not
  be run in its author's workspace; what it could also not do was fetch the
  endpoints, and **4 of the 28 did not survive contact with the network**:
  - `abp-sanjha-punjabi` — hard 404 (a Laravel "Not Found" page). The working
    path is `/home/feed`, not `/feed`.
  - `thanthi-tv-tamil` — 200 with a well-formed `<channel>` containing **zero
    items**. Replaced with the publisher's collections endpoint (40 items).
  - `oneindia-gujarati`, `oneindia-odia` — 200, correct script, and a
    `lastBuildDate` refreshed *daily*, but every actual item was ~6 weeks old.
    A feed can look alive at the envelope and be abandoned at the contents.
  Also, all five `oneindia.com` feeds 403 under the User-Agent the app really
  sends; they only answer a UA containing "Mozilla". Rather than change the
  app's UA to get past someone's bot rule, native publishers were found for
  every language those feeds covered — so the pack needs no UA workaround at
  all and the shipped `HttpClient` default is untouched.
  **What landed: 33 feeds**, each fetched with `HttpClient`'s actual UA
  (`river/0.1 (+news-omission-reader)` — deliberately not a spoofed one) and
  gated on four axes, not just a 200: parseable feed root, ≥5 items, item
  titles in the *expected script*, and a newest item ≤7 days old. Coverage:
  Telugu ×3, Tamil ×3, Kannada, Malayalam ×2, Marathi, Gujarati ×2, Bengali,
  Punjabi, Odia ×2, Urdu, Hindi national ×2, 13 ABP Hindi state desks, and
  Northeast Now (English, the region's own wire). Tests pin one representative
  id per language (so a publisher can be swapped without editing a lockstep
  list, but losing a whole language fails), the ≥13 state desks, URL
  uniqueness, and — because the sources search is a plain substring match on
  `title` — that each language's *name* stays in some title, which is the only
  reason typing "Odia" finds anything.
  One rejected candidate worth recording: `sambadodisha.com/feed` returns 200
  and looks like an Odia outlet, but serves casino spam. Fetching is what
  caught it.
  **Known, pre-existing gap left honest:** `catalogue/catalogue.snapshot.json`
  mirrored only the original 2026-07-07 seed (25 services) and never took the
  Jul-11/Jul-13 all-region expansions. This lands the 33 new entries there
  (now 58) so the CI sentry actually watches them — the rot above is exactly
  what the sentry is for — but the snapshot still lags `Starters.kt`'s ~141
  feeds. Not silently fixed here; logged.

- **D36 — Onboarding ends on a tour, and Settings keeps it (2026-08-31).**
  Owner: no idea whether anyone has found Nooz Cast or Nooz Flash, and the same
  worry about the Loom. Reading the flow back, that is exactly what the app was
  set up to produce — every individual decision defensible, the sum
  undiscoverable. Onboarding was two steps (Welcome → Advanced) and named
  **none** of Cast, Flash, the Loom, Clippings or Today in History; the Loom
  opens by *pulling down on the stand*, Clippings by a bookmark inside the
  reader, and Flash/Cast/Today in History all ship deliberately off. Nothing
  ever said so.
  Added a third step, `FeatureTourContent` — five lines, each naming a thing
  and saying where it lives — reached from **both** doors (Quick setup and
  Advanced's Done), and placed *last* rather than first: the names mean
  something once there are stories behind them, and a tour up front taxes every
  reader before they have a reason to care. **Skip still skips everything**,
  including the tour.
  Deliberately **not** toggles. Flash and Cast each pull down a model, and "off
  until the reader decides" is the whole point of how they ship — flipping that
  during setup, before the words mean anything, trades one bad outcome for a
  worse one. The tour points at the switch instead of being the switch.
  The same composable is mounted in Settings as a collapsed "What's inside"
  disclosure, because onboarding runs **once and has no replay** — without it,
  every reader who installed before today (i.e. everyone who prompted this)
  would never see the tour at all. That, not the first-run copy, is what
  actually reaches the people already asking.
  Also fixed while here: onboarding's step, model path and immersive choice
  were `remember`, not `rememberSaveable`, so rotating the phone mid-setup
  dropped the reader back at Welcome having lost whichever door they picked.

- **D37 — Search that reaches into the articles, and the app's first real
  migration (2026-09-01).** Owner: search should be "rich wrt what's in the
  articles so a person can search based on recall alone that they saw something
  or read something that they remember fuzzily." The Stand's search was
  `title.contains(q)` — it could only find a story if the reader remembered
  words from its *headline*, which fuzzy recall almost never preserves.
  Article prose existed **only** as loose `.txt` files in `FullTextCache`, a
  class with no way to enumerate or query them, so there was nothing to search.
  Added `article_text`, an FTS4 index written from `ArticleRepository`
  alongside the cache, with `ArticleSearch` in `:core:model` as the pure half
  (query building + snippet extraction, unit-tested). Results now match title,
  summary **and** body, and a body match shows the excerpt it matched on —
  without that, a result whose headline contains none of the search terms just
  looks like a bug.
  **Four real defects were caught by testing this rather than reasoning about
  it, three of which would have shipped silently:**
  1. **`AND` is not an operator.** SQLite builds FTS4 with standard query
     syntax unless compiled with `SQLITE_ENABLE_FTS3_PARENTHESIS`; there,
     whitespace already means AND and the word `AND` is *another search term*.
     Joining terms with `" AND "` demanded the article also contain the literal
     word "and" — which most prose does, so this would have hidden in plain
     sight and eaten only the occasional short article.
  2. **The index would have held exactly one row.** FTS4's only key is its
     implicit rowid and `0` is a *valid* rowid, so every insert supplied 0 and
     `REPLACE`-on-conflict made each newly indexed article overwrite the last.
     `autoGenerate = true` fixes it; the table looked entirely healthy either
     way.
  3. **Indic scripts were being shredded.** Tokenising on `!isLetterOrDigit()`
     splits on combining marks — which is how Telugu, Devanagari, Gujarati and
     Odia write their vowels — so words broke mid-cluster and search was
     unusable for exactly the languages D35 had just added feeds for. The FTS
     side needed `unicode61` for the same reason; the default `simple`
     tokenizer treats every non-ASCII byte as a separator.
  4. **Function words excluded the answer.** Terms are ANDed, so "floods in
     nepal" demanded a word starting "in", which the headline "Flash floods on
     the Nepal-Tibet border" hasn't got.
  **The migration is the bigger finding.** `RiverDatabase` was still built with
  `fallbackToDestructiveMigration()` alone under a comment reading "v1 schema is
  unshipped; destructive fallback is fine until the first release" — untrue
  since versionCode 2. Bumping the version for this feature would have answered
  the upgrade by **deleting every shipped reader's clippings, read events and
  weekly aggregates**: the entire history the Loom draws. v4 therefore ships a
  real `MIGRATION_3_4`, schemas are now exported to `core/data/schemas/` and
  checked in, and `RiverDatabaseMigrationTest` opens a real v3 database, runs
  the migration and asserts the old rows are still there. Destructive fallback
  is kept only as the last resort it was always meant to be, for pre-release
  databases with no path forward. The migration DDL is copied verbatim from
  Room's own exported `4.json`, since Room compares that statement on open and a
  hand-written near-miss passes review and then throws on a real device.
  Bounds: index rows are dropped when their items age out of the ~60-day
  retention (`FetchWorker`), and opening an article cached before the index
  existed backfills it — there is no batch migration over the existing 200MB
  cache. 180 model + 46 data tests green.

- **D38 — Long-press a word, see it in your own language (2026-09-01).** Owner:
  "if a user is reading this in a second or third language... they might just
  want to be able to long press a word and see it in their native language even
  if we don't translate the entire thing... how Kindle does it."
  Built as an extension of the dictionary lens rather than a new gesture: the
  reader already long-presses a word and gets a bottom sheet, so the sheet
  gained a labelled translation block under the definition. The two are
  independent downloads and load independently — a reader who installed only
  one still gets everything that one can answer.
  **Word-level, never document-level, on purpose.** Translating a whole article
  would put a machine's paraphrase where a publisher's sentences were, which is
  the one thing this reader does not do to a story. A glossed word leaves the
  article intact and answers the question actually being asked.
  Data is WikDict's Wiktionary-derived SQLite exports (CC BY-SA 3.0): **50 pairs
  — 25 languages, both directions — every one fetched on 2026-09-01 with the
  User-Agent `HttpClient` really sends** and confirmed HTTP 200. Both directions
  ship for each language because a Spanish reader wanting English needs `es-en`
  while an English reader wanting Spanish needs `en-es`; shipping one would
  silently serve half the readers it appears to.
  Stored as **SQLite rather than the flat JSON map** `DictionaryRepository`
  uses. That is a deliberate departure: the Webster's map is held wholly in
  memory (its own comment calls that "a considered v1 trade-off") and doing the
  same to a 26 MB bilingual file would be a much worse one. Two things about the
  real files, both found by reading one rather than assuming: WikDict ships
  `simple_translation` **with no index on `written_rep`**, so an index is built
  once at install rather than scanning per lookup; and headword matching is
  **case-sensitive**, so "Water" — which is what a reader long-presses at the
  start of a sentence — needs a lowercase fallback or finds nothing.
  Downloads stage to a `.part` file and rename on success: a half-written
  database wearing the real name would fail as a corrupt file on every lookup
  forever instead of as one failed download. Tests cover install-and-translate,
  the case fallback, index creation, a failed download leaving nothing behind, a
  corrupted file yielding "no translation" rather than a crash mid-article,
  replacement, and removal.
  **Honest gap, surfaced in the UI and not just here:** WikDict publishes 650
  pairs and **not one is an Indian language** — no Hindi, Telugu, Tamil,
  Bengali, Malayalam, Kannada, Marathi, Gujarati, Punjabi, Odia or Urdu. That is
  awkward directly after D35 added feeds in eleven of them. FreeDict has exactly
  one (`eng-hin`) in a different format, unshipped for now. The Settings section
  says so in as many words rather than presenting a language list that quietly
  omits them.

- **D39 — A sources search that matches the size of the list (2026-09-01).**
  Owner asked for search "that matches the size of the list of resources we
  would now have." The picker's filter was
  `def.title.lowercase().contains(q)` — one substring over one field, which was
  defensible against twenty starters and is not against a catalogue past its
  second hundred. Three failures a reader meets immediately: **word order**
  ("world bbc" found nothing though "BBC World" is right there), **one field**
  (a domain the reader knows, a region, or a language name searched none of
  them — which made D35's keep-the-language-in-the-title convention
  unreachable), and **no ranking** (an incidental URL match could sit above an
  exact masthead). `SourceSearch` in `:core:model` ANDs independent terms across
  title, URL, homepage, region and notes, then orders by match strength — exact
  title, title prefix, title word-prefix, title substring, other field — with
  ties keeping their incoming order, since the catalogue's own arrangement is
  deliberate and a search should not reshuffle what it did not rank. Tested
  against the real shipped catalogue, not only fixtures.

- **D40 — Accessibility: a screen reader can reach the dictionary; and an
  honest scoping of localization (2026-09-01).**
  **Shipped (a11y).** Sighted readers reach a definition — and now a
  translation, since D38 put both in the same sheet — by long-pressing *any*
  word. TalkBack cannot land a press on a particular word inside a paragraph, so
  that entire feature was unreachable with a screen reader on;
  `LensAnnotatedParagraph`'s own doc comment had already flagged it. Paragraphs
  now expose "Define <word>" custom actions alongside the existing per-mark
  ones, built from `ObscureWords` — the detector for "which words here would
  someone want defined", which had been left unused when pre-marking was
  dropped. Actions are **named, not positional** ("Define quotidian", not
  "Define word three"): the menu is read aloud in sequence, where a list of
  positions is unusable. Deduplicated and capped at six, because that menu is a
  listening budget rather than a rendering one. Still gated on a downloaded
  dictionary, so a translation-only reader does not get it — logged, not fixed.
  **Audited — and one claim here was wrong, corrected in D41.**
  `android:supportsRtl` is already on; there are no Java
  `toLowerCase`/`toUpperCase` calls; `String.format` is never called without a
  locale; the one `SimpleDateFormat` is correctly pinned to `Locale.US` for a
  crash log. **But the conclusion drawn from that — that the Turkish dotless-i
  class of bug "cannot occur" — was false**, and this entry asserted it. Kotlin's
  no-arg `lowercase()`/`uppercase()` are indeed `Locale.ROOT`-based, but
  `NewspaperShare.kt:110` passes `Locale.getDefault()` explicitly. The grep that
  produced this paragraph searched for the Java method names and never looked
  for the Kotlin ones with an argument. See D41. A suspected bug — `DateTimeFormatter.ofPattern`
  emitting non-Latin digits into GDELT feed URLs under locales like `ar-EG` —
  **was tested and is not real**: `ofPattern` uses `DecimalStyle.STANDARD`
  regardless of locale, unlike `SimpleDateFormat`. Recorded because it is the
  kind of thing that looks like a bug on inspection and would have produced a
  confident, wrong commit.
  **Not done, and it is a decision rather than an oversight (i18n).** The app
  cannot presently be translated at all: `strings.xml` holds exactly one entry
  (`app_name`) and every other user-facing string is a Kotlin literal —
  **~146 of them across 25 files**, concentrated in `SettingsScreen` (43),
  `EditScreen` (15) and `ArticleListScreen` (14). Externalizing them is
  mechanical and compiler-checked, so it is tractable; what it is not is free.
  It changes no behaviour, produces a large diff across every UI file, and
  starts charging a tax on every future copy change — in exchange for a benefit
  that only exists once somebody actually translates the result. Which
  languages, and whether now, is the owner's call to make, not one to slip in
  underneath a feature branch. **Recommended order when it happens:** externalize
  in one pass per module (compiler catches every miss), keep the reader's own
  prose last since it is the most likely to still be edited, and add
  `android:localeConfig` at the end so the Android 13+ per-app language picker
  appears only once there is more than one language to pick.

- **D41 — Dedup was silently deleting Indic and Urdu news; the topic classifier
  could not see them at all (2026-09-01).** A 23-agent audit of accessibility
  and localization across both platforms turned up two defects that are not
  accessibility problems at all — they are **silent data loss in exactly the
  languages D35 had just added feeds for**, and both were reproduced before
  being fixed.
  **(a) `Simhash.normalize` stripped every combining mark.**
  `NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")` — `\p{M}` is in neither class, and
  that is how Devanagari, Bengali, Telugu, Tamil, Gujarati, Kannada, Malayalam,
  Odia and Gurmukhi write their vowels and virama. Measured, not inferred:
  "मुंबई में भारी बारिश" normalised to the consonant skeleton **"म बई म भ र ब र श"**,
  and against "मुंबई में भारी बेरोजगारी" (unemployment, not rain) landed at
  Hamming distance **6** — inside `NEAR_DUP_THRESHOLD` of 8, so `Dedup` kept one
  and discarded the other. An Urdu pair landed at exactly 8. There was no
  symptom: `Dedup.deduplicate` keeps a cluster's representative and drops the
  rest with no log, error or counter, so a Hindi build merely showed fewer
  stories than its sources sent — indistinguishable from a quiet feed, in an app
  whose entire claim is measuring what flowed. Adding `\p{M}` takes that pair to
  16. An NFC pass was added alongside, because feeds are inconsistent about
  composed vs decomposed nukta forms and the same headline in two encodings
  would otherwise fail to dedup.
  **(b) The fixed threshold was itself biased against dense scripts.** With
  marks preserved, the short Urdu pair still sat at exactly 8 — while the
  *identical one-word change in Latin* ("Heavy rain in Karachi" / "Heavy heat in
  Karachi") sits at 19. A four-word headline yields ~16 shingles, and simhash
  over that little evidence is noisy; scripts that pack more meaning into fewer
  characters are systematically more exposed. `Simhash.thresholdFor` now scales
  the budget with the evidence available (`min(8, max(2, shingles/6))`), so a
  56-character syndication pair keeps the full 8 bits and still collapses, while
  a four-word headline gets 2 — still collapsing identical and punctuation-only
  variants, no longer collapsing different stories.
  **(c) The web topic classifier could never fire on non-Latin text.**
  `web/js/topics.js` built every matcher as `new RegExp('\\b' + term + '\\b')`.
  JavaScript defines `\b` against `[A-Za-z0-9_]`, so it cannot fire adjacent to
  a Devanagari, Arabic, Thai or CJK character: verified,
  `new RegExp('\\bराजनीति\\b','i').test('आज की राजनीति खबर')` is `false`. Every
  item from a non-English feed classified as `general` — the Loom collapsing to
  one band and the Contrast dumbbells emptying, silently. Replaced with a
  Unicode-aware boundary built from a leading character class rather than a
  lookbehind (Safari only gained lookbehind in 16.4, and an unsupported
  construct throws at regex *construction*, taking the module and the app with
  it), plus containment matching for scripts written without spaces. Note this
  was a **prerequisite**, not the whole fix: translating the keyword lexicon
  first would have shipped terms that could never match.
  **Also corrected: D40's own claim that the Turkish dotless-i bug "cannot
  occur" was false.** `NewspaperShare.kt:110` passed `Locale.getDefault()` to
  `uppercase()`, so a Turkish reader sharing a Livemint clipping published an
  image reading "LİVEMİNT" — someone else's masthead, misspelled. Now
  `Locale.ROOT`. The original grep searched for the Java method names and never
  the Kotlin ones with an argument.
  **And the web reader now has CI at all.** Its 6,300 lines of JS shipped
  entirely unexercised, which is how (c) survived. `web/tests/` runs on node's
  built-in test runner with no install step — deliberately, since
  `web/package.json` has no lockfile for `npm ci` to use. `"type": "module"` was
  **not** added: `web/api/*.js` are CommonJS Vercel functions and would break.

- **D42 — The Loom can be operated by a screen reader, and the claim is now
  machine-checked (2026-09-01).** First tranche of the accessibility audit's P0
  list. What landed:
  **Read versus unread is finally in the semantics tree.** `ArticleListScreen`
  spent that state entirely on `TextDecoration.LineThrough` and a colour;
  neither reaches the accessibility API, so a read row and an unread row were
  **byte-identical in speech** — in an app whose stated purpose is showing
  consumption. One `stateDescription` fixes it.
  **The unread filter has a control.** It was reachable *only* by a two-finger
  pinch — no button, no menu, no action (WCAG 2.5.1, Level A). The pinch still
  works; it is no longer the only door.
  **The Loom is operable.** Its semantics were one merged node whose entire
  action list was `[SetTextSubstitution, ShowTextSubstitution,
  ClearTextSubstitution, GetTextLayoutResult]` — no click, nothing custom —
  while its own description ended *"Tap a stream for its counts."* The app was
  instructing a gesture it would not accept, and the per-stream numbers existed
  nowhere else in speech. Now one `CustomAccessibilityAction` per band, each
  carrying its own counts in the label (the menu is read aloud in sequence, so
  "stream three" would be useless), plus a `stateDescription` for the selection.
  **And `describeLoom` names the supply side.** It enumerated only
  `bands.filter { it.consumed }` — topics with at least one read — so a topic
  that flooded the feed and was never opened was **never named**, which is
  precisely the omission the screen exists to show.
  **The evidence half matters as much as the fix.** No UI module had a single
  test dependency, so every accessibility claim about this app was reasoning
  over source rather than observation. `:feature:river` now carries
  `compose.ui.test` + Robolectric, and `DayLoomAccessibilityTest` asserts
  against the real semantics tree on the JVM: that every stream is reachable as
  a named action, that the summary names an unread flood, that the tap
  instruction is gone, and that an empty day still says something true. Note the
  honest limit — this proves the tree carries the data and the actions; it does
  **not** prove what TalkBack utters, and no device here can.
  Remaining from the audit's P0 after D43: screen-change announcements, and
  focus restoration on the web's full-stage rebuild.

- **D43 — The web reader's stories are headings and links again, not buttons
  wrapping whole articles (2026-09-01).** The audit's remaining P0 blockers.
  Every story on the Paper, and every row in Clippings, was `role="button"` on
  the container itself with the entire article nested inside it. Two
  consequences, neither of them visible to a sighted tester:
  `button` computes its accessible name from its contents *and flattens their
  structure*, so each card's name was **an entire article read as one unbroken
  string**, and the `<h2>` inside it stopped being a heading. Heading navigation
  on the front page returned the masthead and nothing else; the links list was
  empty. Those are the two ways a screen-reader user skims a page, and the Paper
  offered neither. Second, the bookmark and the image-style chips became
  interactive elements nested inside a button — invalid, and handled differently
  by every engine.
  **The headline is now the control.** A real `<a href="#/reader/…">` inside the
  existing heading: the heading comes back, the name is the title alone, the
  story lands in the links list, and keyboard operation is free rather than
  hand-rolled. The card keeps a plain click listener so the large pointer target
  survives — it has simply stopped pretending to be a control. `setAttribute`,
  not the `.href` setter, because the setter resolves against the document URL
  and re-serialises, which can decode escapes an item id needs to keep.
  **Read state is spoken.** `.is-read` only dimmed the headline, so "have I read
  this?" was carried by colour alone. A `nooz-visually-hidden` "Read." now sits
  inside the heading.
  **The web Loom had the same defect as the Android one, plus a worse one.**
  Each tube was an SVG `<path>` carrying `role="button"`, `tabindex="0"` and its
  counts in an `aria-label`. None of it was ever reachable: the `<svg>` declares
  `role="img"`, which makes its whole subtree **one leaf** in the accessibility
  tree, and Safari does not honour `tabindex` on SVG shapes regardless. It
  looked like access and was not. The per-stream numbers now live in ordinary
  `<button>` stream keys — off-screen until focus lands inside, then they slide
  in as a legend, because a sighted keyboard user has to see where the focus
  ring went. `describeLoom` gained the same supply-side fix as its Android twin,
  and both stopped saying "1 sources".
  **Evidence, again, is half of it.** `web/tests/a11y.test.mjs` runs the real
  view modules against a real DOM (linkedom, already a dependency) and inspects
  what comes out: no card claims to be a button, no control is nested inside a
  fake one, every headline is a heading containing a link, an awkward item id
  still produces a usable href, the read marker lands on the story that was
  read, the card click still fires exactly once, and each Loom stream is a named
  button whose selection is announced. Both reading modes are covered —
  Newspaper mode is a separate code path a manual pass would probably not have
  opened. Verified by mutation: reintroducing `role="button"` on a column story
  fails the suite.

- **D44 — Navigating the web reader now puts the cursor somewhere, and says so
  (2026-09-01).** The last of the audit's P0 list. `renderNow` rebuilds the
  stage and the drawer on every route change, so whatever had focus — the
  headline link you just activated, the nav button you just pressed — ceased to
  exist and focus fell to `<body>`. The only thing restored was a live `INPUT`
  or `TEXTAREA`. For a keyboard or screen-reader user that **is** the
  navigation: activate a story, land silently at the top of the document, walk
  the entire page again to get anywhere. Nothing announced the new view either,
  because from the accessibility tree's point of view nothing happened — the
  document simply changed underneath.
  Focus now lands on the new view's own `<h1>` (`tabindex="-1"`, ring
  suppressed — nobody arrived by pressing Tab), which is also what makes the
  change *audible*: a screen reader announces the newly focused heading. A
  drawer takes focus on open and hands it back to the control that opened it on
  close, even across drawer-to-drawer moves, and falls back to the stage heading
  if that control is gone. `closeSearch` returns focus to the search toggle,
  which it never did — the bar is hidden by a class, so a focused input inside
  it stayed focused while invisible, taking keystrokes nobody could see.
  **The two negative rules matter as much as the positive one**, and are the
  easy ones to lose: focus must not move on an ordinary re-render (a fetch
  landing, a story marked read, a setting toggling), and must not move on the
  first paint. Getting either wrong is worse than the original bug, because it
  takes the cursor from someone mid-sentence.
  The logic lives in `web/js/focus.js` rather than inside `app.js` specifically
  so it can be tested — `app.js` opens IndexedDB and starts fetching on import,
  and a rule this consequential should not be verified by reading it.
  `web/tests/focus.test.mjs` covers all four rules plus the fallbacks. Verified
  by mutation: dropping the route-change guard fails two tests.
  One test-craft note worth keeping: assertions here compare printable names,
  never DOM nodes. `assert.deepEqual` on two linkedom elements walks a circular
  object graph, and the run stops producing output instead of a failure
  message — a suite that hangs on the bug it catches is not a suite.

- **D45 — Arriving at a screen is announced (2026-09-01).** Every top-level
  screen swap happens inside one activity and one composable, so nothing told
  TalkBack the screen had changed: opening the Loom produced no announcement at
  all, just a silently different set of nodes under the same window. The screen
  switch is now wrapped in a pane whose `paneTitle` changes with it, which is
  what Compose turns into the platform's window-state-changed event — the event
  TalkBack reads aloud. Titles are the names the reader already knows from the
  control they pressed ("Loom", not "LoomScreen").
  **Honest limit:** this one is reasoned and compiled, not observed.
  `MainActivity` needs the whole DI graph to compose, so there is no cheap
  JVM test for it, and unlike D42 there is no assertion behind this claim.

- **D46 — Android's font fallback already covers every script the catalogue
  ships, so no fonts need bundling. This corrects an earlier claim of mine
  (2026-09-01).** I had recorded non-Latin font coverage as the gate on the
  whole locale rollout. It is not, and the reasoning behind that was wrong.
  The premise was right: `fontTools` on the ten bundled faces shows Hyle
  Grotesk at 735 codepoints (Latin + Greek) and Hyle Print / PT Serif at
  1163/717 (Latin + Cyrillic + Greek). **Not one of them carries a single
  Indic, Arabic or CJK glyph** — and the app already ships 33 India regional
  feeds in eleven scripts.
  What I got wrong was assuming a bundled font means tofu. AOSP
  `Typeface.java` (android14-release) settles it: `Typeface.Builder.build()`
  wraps its single family in `CustomFallbackBuilder`, and so does
  `createFromResources` for XML families. `CustomFallbackBuilder.build()` passes
  `getSystemDefaultTypeface(mFallbackName)` into `nativeCreateFromArray`, and
  `mFallbackName` defaults to null, which that method resolves to
  `Typeface.DEFAULT` — the full system font collection, Noto chain included.
  A missing glyph therefore falls through to the platform's own fonts. Compose's
  `Font(resId)`, `ResourcesCompat.getFont` and `NewspaperShare`'s `Canvas`
  drawing all converge on that same path.
  **Consequence:** no Noto bundling (which would have added megabytes), and the
  locale rollout is not gated on fonts. Worth recording how nearly this went the
  other way: the first attempt to check it was a Robolectric probe calling
  `Paint.hasGlyph`, which reported Latin "A" missing from the *default* paint —
  it was measuring Robolectric's stub, not Android, and would have "confirmed"
  a catastrophic bug that does not exist. A test that cannot fail correctly is
  worse than no test, and the tell was that its answer was too dramatic.

- **D47 — A language is now a data file, and thirty of them exist
  (2026-09-01).** The owner's correction was blunt and correct: *"tractable but
  taxes future copy change is not really tenable nor acceptable — please advise
  and course correct so it becomes easier to add more languages as we go."*
  This is the course correction, and the shape of it is the point.
  **Adding a locale is one JSON file.** `i18n/strings/<bcp47>.json` next to
  `en.json`, then `python3 tools/i18n/generate.py`. No Kotlin, no build change,
  no call-site edits. The generator writes Android's `values-b+<tag>/
  strings.xml`, the web reader's `web/i18n/<tag>.json`, `locales_config.xml`
  and a `LocaleCoverage.kt` of measured completeness. Removing a locale is
  deleting the same one file; the generator reaps the orphans.
  **Partial is safe, and that is what makes thirty possible.** Android resolves
  each string separately and falls back to `values/` per key; the web layer does
  the same. A locale that is 41% done shows 41% in the reader's language and
  English for the rest — never a blank, never a key name. Without that property
  the only honest options are "finish a language or don't start it", which is
  how apps end up with two.
  **One source for both front ends.** Nooz has two clients and one set of words.
  Two hand-maintained catalogues means a translator does every language twice
  and the two drift the first time anyone is in a hurry — which in practice
  means the web reader stays English. *"The web reader must also do the same"*
  is now structural rather than a promise.
  **The languages.** `Locales.kt` lists India's fifteen most-spoken in 2011
  Census order (that is the order the picker offers, and for a reader without
  English the first screenful is most of the decision), then fifteen more of the
  world's, skipping Hindi/Bengali/Urdu already covered. Endonyms are the primary
  label throughout: a picker written entirely in English is a picker for people
  who already have English. Shipped: 29 locales plus English, 28 of them
  complete for this first tranche of 37 strings.
  **Two honest gaps, recorded rather than hidden.** Kashmiri ships at 41% — the
  entries I could not write responsibly are absent, and fall back to English,
  which is exactly what the per-key fallback is for. **Santali is in
  `Locales.kt` and has no catalogue at all.** It is listed because omitting the
  language of ~7.6 million people to avoid an imperfect answer is not a trade
  this app gets to make quietly; it is *not* in `locales_config.xml`, because
  offering someone their language in Android's system picker and then handing
  them an English app is worse than not offering it. `locales_config.xml` is
  generated from what exists, never from what is intended, precisely so that
  distinction cannot rot.
  **Verified in the artefact, not the source.** `assembleFullDebug` produces an
  APK whose `resources.arsc` contains the Hindi, Tamil, Urdu, Punjabi and
  Chinese strings — checked by reading the built APK, since a generator that
  writes plausible XML nobody packages is the failure mode worth guarding
  against.
  **Switching.** API 33+ uses the platform `LocaleManager`, so the choice lives
  where every other app keeps it (Settings › Apps › Nooz › Language) and
  survives reinstalls; API 31–32 mirrors it in SharedPreferences and applies it
  in `attachBaseContext`, which needs the answer synchronously and so cannot use
  the DataStore that holds everything else. Deliberately not AppCompat:
  `setApplicationLocales` would cover both, but needs an AppCompat activity and
  theme, and this app is Compose over platform themes on purpose (D34).
  **Still to do:** 219 strings remain in Kotlin, ratcheted per file by
  `verifyI18n`. Each future tranche is appended to `en.json` and every locale
  file, so the cost of language number thirty-one is unchanged by any of it.

- **D48 — The web reader speaks the same thirty languages, from the same files
  (2026-09-01).** *"The web reader must also do the same, okay?"* — it does, and
  structurally rather than by promise. `web/js/i18n.js` reads the catalogues
  `tools/i18n/generate.py` writes from the very same `i18n/strings/*.json` the
  Android app is built from. There is no second list to keep in step.
  Per-key fallback matches Android's resource resolution exactly, so Kashmiri
  renders its 41% in Kashmiri and the rest in English on both clients, and `t()`
  takes a mandatory English fallback argument so a failed fetch shows words
  rather than `tour_loom_body`.
  **`lang` and `dir` are the accessibility half of this**, and they are easy to
  forget because nothing looks wrong without them. `<html lang>` is what picks a
  screen reader's voice and pronunciation rules — an Urdu interface still
  labelled `lang="en"` gets read aloud with English phonetics, which is
  unusable. Each row of the language picker also carries its **own** `lang`,
  since every entry is written in a different language; without that the list is
  read in one accent, and a list of native names is precisely the thing a
  reader without English navigates by ear. `dir` flips the whole document for
  Arabic, Persian, Urdu and Kashmiri, and the picker's CSS uses `text-align:
  start` and `margin-inline-start` so rows align to their own reading edge.
  **Resolution is the part with judgement in it, so it is the part under test.**
  Browsers send what the OS gives them — `pt-BR`, `zh-Hans-CN`, `zh-hans` — and
  almost none of it is the name of a catalogue. `resolveTag` walks the browser's
  preference order, matches case-insensitively, then shortens progressively
  (`zh-Hans-CN` → `zh-Hans` → `zh`). `web/tests/i18n.test.mjs` pins all of it.
  **Three of those tests are really about the generator**, and they are the ones
  worth keeping: no translation may lose or invent a `%1$s` its English carries,
  none may hold a key the base does not (which is how an unpropagated rename
  shows up), and the index the picker reads must agree with the catalogues that
  exist, coverage figures included. A dropped placeholder leaves a silent gap in
  a sentence on a screen nobody testing in English will ever look at.
  Verified end to end: booting with `ur` stored yields `lang="ur"`, `dir="rtl"`,
  a heading reading ترتیبات, thirty-one picker rows each in its own script, and
  Kashmiri labelled کٲشُر · 41% ترجمہ شدہ.

- **D49 — The last two silent visualizations now speak, and are tested
  (2026-09-01).** Closes the audit's Canvas list, which D42 opened with the
  Loom.
  **The region globe told a screen-reader user to "Drag to spin, pinch to widen
  the band"** — two gestures neither of which a screen reader can make — and
  offered no action in their place. The sector chips beneath it cover picking a
  region, so that half had a door; **the band width had none at all**, and the
  topic-mix ring, the only place the aimed region's real counts are drawn,
  existed nowhere in speech. Now four custom actions (spin east/west, widen/
  narrow) sized so a few invocations visibly move the selection — an action menu
  is read one item at a time, so a step needing twenty repeats is no step — and
  a description that names the region, the band, the total and the top topics.
  **`RegionHeatStrip` was a bare `Canvas`,** which publishes nothing at all. The
  entire answer to "where in the world have I been reading?" — the question the
  Contrast ledger exists to ask — was carried by the relative darkness of eight
  rectangles: silent to a screen reader, and unreadable to anyone who cannot
  separate two close greys. It now names every sector that has a read, with its
  number, densest first, and says the current selection out loud instead of
  leaving it to a highlight. Sectors with nothing read are omitted: reading
  eight zeroes aloud before the one number that matters buries the answer.
  **Evidence.** `:core:design` had no test dependency at all, so `GlobeCanvas` —
  an interactive control living outside any feature module — had nowhere for its
  proof to go; it now carries the same Robolectric + `compose.ui.test` setup as
  `:feature:river`. `GlobeAccessibilityTest` (5) **invokes** the custom actions
  rather than trusting their labels, since an action that fires nothing is
  precisely the defect being fixed. `ContrastAccessibilityTest` (4) is
  mutation-verified: deleting the `semantics` block fails all four.
  **The remaining honest gap**, which `verifyI18n` cannot see: these spoken
  descriptions are still assembled from English literals inside functions, not
  from `strings.xml`. The scanner matches text call sites, and a string built in
  a `buildString` is invisible to it. So the Loom, the globe and the heat strip
  currently speak English in every locale.

- **D50 — The classifier can finally read the languages the catalogue publishes
  in (2026-09-01).** Two bugs met here, and either alone was enough to hide the
  other.
  **The lexicon was English-only** while the catalogue ships 33 India regional
  feeds across eleven scripts (D35). Every one of those stories classified as
  `general`. The Loom — this app's whole argument — collapsed to a single
  undifferentiated band, and the Contrast dumbbells emptied, *for exactly the
  readers the India expansion was for*. Nothing errored. It looked like a quiet
  news day, every day.
  **And the matcher could not have fired even with the terms present.** It was
  `Regex("\\b" + escape(term) + "\\b")`, and Java defines `\b` against `\w`
  = `[a-zA-Z_0-9]` unless `UNICODE_CHARACTER_CLASS` is set — so there is no
  word/non-word transition at the edge of a Devanagari or Arabic character the
  engine does not treat as a word character at all. **Every non-English term
  anyone added to this lexicon would have been silently inert**, and the failure
  would have looked exactly like "that keyword just isn't in this headline".
  This is the third appearance of the same family of bug in this codebase, after
  the JS `\b` in `topics.js` (D41) and the missing `\p{M}` in `ArticleSearch`
  and `Simhash` (D39) — the boundary class is now `[\p{L}\p{N}\p{M}]` in all
  four places, with containment matching for the unspaced scripts where a word
  boundary is not a meaningful idea at all.
  **987 terms across eleven languages**, in `i18n/lexicon/`, in the same shape
  as the string catalogues: one file per language, generating both
  `TopicLexiconL10n.kt` and `web/js/topics-l10n.js`. Emitted as a JS *module*
  rather than JSON on purpose — `classifyItem` is synchronous and on every
  render path, so a fetch would either make classification async or make it
  return `general` for everything until the fetch landed, which is precisely the
  bug being fixed. Terms are merged across languages rather than kept
  per-language: a story is classified by the words it is written in, not by a
  language label its feed may never have carried.
  **Tested on both clients, including that they agree.** Six Kotlin tests and
  two more JS ones: headlines in eleven scripts, English unchanged (`warden` is
  still not `war`), the boundary construction tested independently of any
  particular word, combining marks not splitting an inflected form, unspaced
  scripts matching by containment, every generated term compiling and matching
  itself, and finally a test asserting every web term is present in the Kotlin
  file — because a hand-edit to one generated file works locally and is silently
  reverted by the next generator run.
  **Honest limit, recorded in `i18n/lexicon/README.md`:** this is a first pass
  and has not been reviewed by native speakers. It ships because an incomplete
  lexicon that fires beats a complete one that cannot, and because
  `TopicEvidence` keeps every match inspectable — a reader can tap a
  classification and see the exact term behind it, which is how a bad term gets
  found.

- **D51 — Accessibility is now checked by a browser, and it immediately found
  what review had not (2026-09-01).**
  **The finding first, because it is the argument for the tooling.** The palette's
  secondary ink — `--paper-ink-dim` / `Tokens.Palette.paperInkDim`, `#8A8A86` —
  measures **3.21:1** on the paper field, against the 4.5:1 WCAG AA requires for
  body text. That token carries the masthead edition line, every byline and
  timestamp, secondary copy throughout both clients, and the dek of a read
  story. The dark theme's `inkDim` was the same failure by a different route:
  42% alpha compositing to **3.57:1**. Both are now 4.6:1 or better, still far
  lighter than the 17:1 primary ink, so dimmed text still reads as dimmed — that
  distinction never needed to be this faint. **Nobody had noticed in months of
  work on this app, including a full accessibility audit, because contrast is
  not something you can eyeball.**
  Worse, and more pointed: the loom strip — *the Loom's only entry point on the
  Paper*, the feature the owner specifically flagged as undiscovered — combined
  `--paper-ink-faint` (1.55:1) with `opacity: 0.82`. It was quiet to the point
  of invisible. The opacity is gone; restraint survives in size, weight and
  placement. Opacity is the wrong tool for quiet: it washes out text and bar
  together, and no colour token can compensate for it downstream.
  **The suite.** `web/tests/browser/axe.test.mjs` serves the reader, seeds real
  stories through the app's own IndexedDB layer, and runs axe-core over the
  Paper, an open article, the Loom, Settings, and the front page in Urdu with
  `dir="rtl"`. An empty page passes any accessibility check, so the seeding is
  the point; one test also asserts axe found enough to check. Mutation-verified:
  restoring `#8A8A86` fails four of the five scans.
  **What this does and does not claim.** axe catches roughly a third of real
  barriers and is no substitute for a person with a screen reader. But that
  third is the third that regresses invisibly — contrast drift, an icon button
  that loses its name, a skipped heading level, a lost landmark. The parts axe
  cannot see are covered by the Robolectric semantics tests (D42, D49) and, in
  the end, by testing with actual users.
  **A second finding, from writing the harness rather than running it.** The
  first version dismissed onboarding with the wrong `localStorage` key, so every
  scan was measuring the onboarding card and passing. A green suite that never
  reached the app is the same failure as Lint's inert `HardcodedText`, and it is
  why each test now waits on a selector that only exists once the Paper has
  rendered.

- **D52 — CI was running 94 of 309 unit tests (2026-09-01).** `ci.yml` asked for
  `testDebugUnitTest testFossDebugUnitTest testFullDebugUnitTest`, which reads
  like "all of them" and is not: those are Android variant tasks. **`:core:model`
  is a plain `kotlin.jvm` module whose task is `test`** — and it is the
  most-tested module in the repo. Its **215 tests** (Simhash, Dedup,
  ArticleSearch, the feed parser, OPML, the classifier, and everything added in
  D50) had never been executed by CI. The build was green the entire time,
  because every task that was asked for passed.
  A hand-maintained task list has the same failure mode the moment someone adds
  a module, so `gradle/verification/unit-tests.gradle.kts` registers a
  `unitTests` task that **discovers** each module's own test task, and **fails
  the build** if a module has test sources it cannot find a task for. Exclusions
  have to be written down in `skippedModules` with a reason, which is precisely
  what never happened to `:core:model`.
  Discovery runs in `gradle.projectsEvaluated`, not each project's
  `afterEvaluate`: AGP registers its variant tasks from inside its own
  `afterEvaluate`, so a callback added here runs first and sees an Android
  module as having no test task at all.

- **D53 — The screens a reader actually lives in now speak their language
  (2026-09-01).** The second migration tranche: `ArticleListScreen` and
  `ReaderDetailScreen`, both to zero, taking the catalogue from 37 strings to 68
  and the ratchet from 218 to 193.
  Chosen over the larger `SettingsScreen` deliberately. **Most of these strings
  are `contentDescription`s** — the words a screen reader utters on every screen
  the reader touches: "Fetching your sources", "Showing unread only, tap to show
  every story", "About 40 percent through the article", and the `stateDescription`
  read/unread pair added in D42. Left in English they would have undone most of
  what the thirty locales are for: a blind reader in Tamil would have had a Tamil
  onboarding and then an English app read aloud to them forever after.
  28 locales are complete at 68 strings; Kashmiri sits at 35%, and the picker
  says so.
  Two mechanical notes worth keeping. `semantics { }` and `clickable(onClickLabel
  = …)` are **not composable scopes**, so every one of these needed the
  `stringResource` hoisted to the nearest composable — the compiler catches it,
  but only after the migration looks finished. And aapt **rejects `--` inside an
  XML comment**, failing the whole resource file over a double dash in a
  hand-written section note; the generator now converts it, because prose written
  for humans reaches for a double dash constantly.
  Verified in the artefact again, with a negative control this time: the Telugu,
  Punjabi and Urdu strings are in `resources.arsc`, and a nonsense probe is not.

- **D54 — The Loom speaks the reader's language, closing the gap D49 recorded
  (2026-09-01).** D49 ended with an admission: the Loom's, the globe's and the
  heat strip's spoken descriptions were still assembled from English literals
  inside plain functions, invisible to `verifyI18n` (which matches text call
  sites, not string construction). So a blind reader who set the app to Tamil
  got a Tamil interface and then heard **the app's centrepiece described in
  English**, permanently, with nothing anywhere reporting it.
  `describeLoom`, `describeGlobe` and `describeHeatStrip` are now `@Composable`
  so they can reach `stringResource`, along with the Loom's per-stream action
  labels, its selection state, the globe's four actions, and the Loom, Contrast
  and CrossSection chrome. The catalogue goes 68 → 117 strings; the ratchet
  193 → 170. **29 locales complete**, Kashmiri at 27%.
  A Kotlin note worth keeping: `map` is `inline` and therefore *is* a composable
  scope, but `joinToString`'s `transform` is not — so every list is resolved
  first and joined afterwards. The compiler catches it, but only after the work
  looks done.
  And the placeholder guard earned its place twice over. It failed on Arabic's
  `loom_source_one`: مصدر واحد carries "one" in the word rather than a digit.
  That is a translation decision, not a slip, and several languages make it — so
  the rule is now "a `_one` key may drop its number; nothing else may, and
  inventing a placeholder is always an error." Narrowing the rule to fit the
  language beats bending the language to fit the rule, and the guard still
  catches the thing it exists for: a lost `%1$s` leaves a silent gap in a
  sentence on a screen nobody testing in English will ever look at.

- **D55 — The web reader was 13% translated, and nothing said so
  (2026-09-01).** D48 built the web i18n layer and this entry is about what that
  did *not* accomplish. The layer being in place is not the same as the reader
  being translated, and the gap was invisible from the inside: `web/i18n/*.json`
  reported **100% for twenty-nine locales**, every one of those numbers being
  about the *catalogue* rather than about how much of the app reads from it.
  Exactly **one view called `t()`**. A reader who chose Tamil got a Tamil
  settings heading and an English everything-else, and the picker showed no
  shortfall at all.
  The measurement came first, deliberately — `web/tests/i18n-coverage.test.mjs`,
  the same ratchet shape as Gradle's `verifyI18n`, printing the honest number on
  every run. It started at **7 wired, 48 hardcoded**. It is now **60 and 3**,
  with the Paper, the reader, the Loom, Clippings, the Stand and Sources all
  going through `t()`. The catalogue grew 117 → 157; 29 locales complete;
  Kashmiri 24%.
  **A second guard, added because the first one could not have caught this:**
  every `t('key', …)` must name a key that exists in `en.json`. A typo'd key
  shows its English fallback silently — the catalogue still says 100%, the
  coverage ratchet counts the call as *translated*, and the string is English
  forever. It found `loom_strip_label` on its first run: a key the wiring
  referenced and nobody had added. Two guards that each look correct in
  isolation can still leave a hole exactly where they meet.
  One deliberate exemption, written down rather than assumed: `Nooz`, `Nooz
  Flash`, `Nooz Cast`, `GDELT` and `Wikipedia` are brand names. A translated
  masthead is a bug, and routing them through keys would mean thirty identical
  copies of one word and thirty chances for one to drift.

- **D56 — Android actually *picks* the translations, now checked rather than
  assumed (2026-09-01).** A real hole in how D47 was verified. That entry proved
  the Hindi, Tamil, Urdu and Punjabi strings were in `resources.arsc` — which
  establishes they were **packaged**, and nothing more. Resource *selection* is a
  separate mechanism: it depends on the `values-b+<tag>` qualifier being spelled
  exactly as `Locales.androidResourceQualifier` spells it, on the library
  module's resources merging into the app, and on the tag Android derives from
  the device locale matching. Get any of that wrong and **every locale falls back
  to English silently, with the strings still sitting in the APK exactly where
  the earlier check found them.** The check could not have told the difference.
  `LocaleResolutionTest` closes it with `@Config(qualifiers = …)`, which runs the
  real resolver: English as the base, Hindi, Tamil and Urdu resolving,
  `b+zh+Hans` (the one tag with a script subtag, and the one place the BCP 47
  conversion could break), `b+mai` (three-letter codes take a different path in
  some resolvers than two-letter ones), an unshipped locale landing on English
  rather than a blank, format arguments surviving translation, and — the property
  the whole "partial is safe" design rests on — Kashmiri resolving its one
  translated key while an untranslated one falls back per key.
  Mutation-verified by moving `values-b+ta` out of the tree: `tamilResolves`
  fails. The general lesson is the one worth keeping: **"the artefact contains
  it" and "the artefact uses it" are different claims,** and the first is much
  easier to check, which is exactly why it is the one that gets checked.

- **D57 — Choosing your sources now works in every shipped language
  (2026-09-01).** Third migration tranche: `EditScreen` and `SourcesUtilities`,
  both to zero. Catalogue 157 → 185 strings; ratchet 170 → 140.
  Picked over the larger `SettingsScreen` because this is the flow the app's own
  premise rests on — *"you decide your news sources"*. A reader who cannot read
  "Paste a feed or a site URL", "This page declares more than one feed, pick
  one" or "Couldn't add this URL" cannot use the app for the thing it is for,
  however much is translated around them.
  Several keys are shared with the web's `sources_*` set rather than duplicated,
  which is the payoff of one catalogue for two clients: the wording that already
  existed in twenty-nine languages did not need translating twice.
  29 locales complete at 185 strings; Kashmiri 22%.
- **D58 — Settings, the last big screen, and the brand exemption that made it
  finishable (2026-09-01).** Fourth migration tranche: `SettingsScreen`, 69
  hardcoded strings to zero, the single largest file in the app. Catalogue
  185 → 249 strings; ratchet 140 → 68 across 22 files.
  Settings is where the app makes its promises — *"the report stayed on your
  device; nothing was sent anywhere"*, *"what you look up is never sent
  anywhere"*, *"API keys are never included"*. A privacy claim a reader cannot
  read is not a claim they can rely on, which is why this screen was worth its
  size.
  Two files reached zero without a line of migration: `Wordmark` and the last
  two in Settings were the words **Nooz**, **Nooz Flash** and **Nooz Cast**.
  Routing a brand through `strings.xml` would mean twenty-nine identical copies
  of the same word and twenty-nine chances for one to drift into a translated
  masthead, so `verifyI18n` now carries a `BRAND` exemption mirroring the web
  guard's. The exemption is what let the ratchet close those files honestly
  instead of parking them at 1 and 2 forever.
  A near-miss worth recording: the exemption was declared and then not applied —
  the `return@match` never made it into the loop — and the build stayed green
  and said `73 still to move`, exactly as it had before. Nothing failed. The
  tell was the *other* half of the ratchet staying quiet: if the exemption had
  worked, SettingsScreen would have dropped under its budget and the build would
  have demanded the number be lowered. **A guard's silence is only evidence when
  you know which noise it would have made.**
  `LocaleResolutionTest` gains two cases for this tranche rather than trusting
  the catalogue count: `settings_your_data_body` resolving to Malayalam (a long
  body string, because a screen can be wired for its headings and still be
  English underneath) and `settings_size_license` keeping both positional
  arguments in Arabic. 12 tests, all passing.
  29 locales complete at 249 strings; Kashmiri 76/249 (31%, up from 22%).
- **D59 — The ratchet reaches zero, and the copy only blind users hear
  (2026-09-01).** Final migration tranche: the remaining 22 files, 81 new keys,
  catalogue 249 → 330. `i18n-allowlist.txt` is now empty and `verifyI18n`
  reports `0 string(s) still to move, across 0 file(s)`. Every interface string
  in the scanned modules lives in `strings.xml`.
  **The find that mattered.** Widening the guard to `onClickLabel =` surfaced 23
  strings it had never matched — `\b` finds no boundary inside `onClickLabel`,
  so `\blabel\s*=` had been silently skipping every one. These are labels
  TalkBack speaks and the display never shows: "Open the day loom", "Filter to
  Politics", "Define quotidian". A sighted pass over a Tamil build would have
  looked finished while every spoken affordance was still English. For an app
  whose accessibility claim has to be decisive, that was the worst possible
  place for the guard's blind spot, and it was invisible precisely because
  nothing renders it.
  **Two exemptions, on the same principle as BRAND.** `label =` on
  `rememberInfiniteTransition`/`animate*` is an animation-inspector name, and
  `@Preview` bodies are developer scaffolding; neither ships to a reader. Left
  matched, the only ways to satisfy the guard would have been to translate a
  debug label into twenty-nine languages or to park it on the allowlist — and
  the allowlist claims its entries are still to move. `enclosingCall()` walks
  back through balanced parens to tell the two kinds of `label` apart;
  `blankPreviews()` blanks preview bodies offset-for-offset so line numbers stay
  exact. Together they closed Bars, EmptyState and SectionHeading honestly
  rather than by amnesty.
  A third `LocaleResolutionTest` case asserts a spoken label resolves in Tamil,
  including an interpolated word — the same reasoning as D58's: the copy nobody
  can see is the copy that needs a test rather than a glance. 13 tests, 321
  across the build, all passing.
  29 locales complete at 330 strings; Kashmiri 119/330 (36%, up from 31%).

## Schema versions
- Data model: **v2**, materialized in Room (`SourceEntity`, `ItemEntity`,
  `ReadEventEntity`, `WeeklyAggregateEntity`, **`ClippingEntity`**).
- Source registry schema: **v1** (mirrors provider-catalogue `services[]`).
- Catalogue consumption (`catalogue.json`): **built** (P6) — user-supplied URL
  only, no baked-in default (see D4).

## Open questions (log now; surface at P4 gate per brief §10)
- Daily vs weekly default abstraction — built as a parameter; ship weekly.
- User-editable taxonomy — logged, not built in v1.
- On-device embedding classifier upgrade — logged, not built in v1.
- Direct-fetch IP exposure — document in v1; user-configured proxy is later.
- Which real inference provider to wire first (LocalLlamaProvider via a
  llama.cpp AAR is the most self-contained; Urbana and ML Kit both depend on
  ecosystem/device pieces this session couldn't access).

## Verification log (feeds/APIs — brief §0 "verify live at build time")
- **2026-07-07** — verified 31 candidate endpoints (fan-out fetch + curl re-check
  through the build proxy). **20 concrete feeds ship** (11 global, 9 India):
  BBC (top/world), NPR, Guardian (world/intl), Al Jazeera, DW, France24, NYT
  (world/top), ProPublica; The Hindu (home/national), NDTV, Times of India,
  Indian Express, Livemint, Hindustan Times, Business Standard, Scroll.in
  (via FeedBurner). Builder kinds verified: Google News (RSS), Mastodon (tag
  timeline, no auth). **Dropped:** CNN edition RSS (HTTP 503, retired), Wikinews
  Special:NewsFeed (404), The Wire & The Print (served HTML, no items), Deccan
  Herald (no resolving path). **Caveats:** GDELT DOC 2.0 exists but rate-limits
  (429/503 under load — retry w/ backoff); Mastodon public timeline needs auth on
  mastodon.social (422) so the hashtag timeline is the no-auth default.
- Guardian Open Platform (keyed, free) and GNews (Tier-B `api` reference) are
  registered as keyed providers; not live-verified (need a key).
- **Resolved by D18**: this app now downloads from `ai-catalogue/models.json`'s
  real, HTTP-200-verified entries rather than the old unverified 2-id
  placeholder catalogue. Do not hand-populate a `downloadUrl`/`sha256` outside
  that shared, sentry-probed file — it's the one place this constellation
  keeps that honest.
- **2026-07-11** — see D12: 43 more feeds fetched and confirmed live in an
  all-region fan-out (Americas, Europe & Africa, Middle East & C. Asia, South
  Asia beyond India, East & SE Asia, Australia & Pacific), stamped
  `2026-07-11`. 63 verified feeds total after this run.
- **2026-07-13** — depth pass targeting the owner's "list seems too small for a
  worldwide set of sources" note, focused on the thinnest buckets. Tested ~90
  candidate URLs (curl through the build proxy, checked for HTTP 200 + a
  parseable `<rss`/`<feed` body with real `<item>`/`<entry>` elements); **45
  pass and ship**, stamped `2026-07-13`. 108 verified feeds total after this
  run. By region:
  - **Europe (17, new `"europe"` tag)** — `europe-africa` was the thinnest
    bucket (7 feeds for two continents, no individual country outlets beyond
    pan-European Euronews/EUobserver). Rather than splitting `europe-africa`
    in place, new feeds use fresh `"europe"`/`"africa"` tags so the existing
    tag and its 7 feeds are untouched for anyone already filtering by it.
    Added: Kyiv Post (Ukraine), Notes from Poland, Hungary Today, The Baltic
    Times, The Portugal News, ANSA English (Italy), NL Times & DutchNews.nl
    (Netherlands), Balkan Insight, Emerging Europe, TheJournal.ie, RTE News &
    The Irish Times (Ireland), The Moscow Times (Russia), Romania Insider,
    The Olive Press (Spain), Cyprus Mail.
  - **Africa (9, new `"africa"` tag)** — The Standard (Kenya), MyJoyOnline
    (Ghana), Egypt Independent, Lusaka Times & Zambia Daily Mail (Zambia), The
    Namibian, Nyasa Times (Malawi), Club of Mozambique, Eye Radio (South
    Sudan) — 8 countries beyond the existing South Africa/Nigeria coverage.
  - **mideast-casia (+11)** — The Jerusalem Post (Israel), Iraqi News, The961
    (Lebanon), Syria Direct, Daily Sabah & Hurriyet Daily News (Turkey), Civil
    Georgia, APA & Trend News Agency (Azerbaijan), Asia-Plus (Tajikistan),
    24.kg (Kyrgyzstan).
  - **south-asia (+8)** — The News International & Daily Times (Pakistan),
    Dhaka Tribune & The Business Standard (Bangladesh), Ada Derana (Sri
    Lanka), Onlinekhabar English (Nepal), Ariana News & Khaama Press
    (Afghanistan, newly represented in this region).
  - **Notable exclusions** (plausible URL, failed live check): The Local's
    DE/FR/IT/ES/SE/DK/NO/CH editions (all HTTP 404 — RSS retired sitewide),
    Politico Europe & EURACTIV & Greek Reporter & Times of Israel & Al Arabiya
    English & Jordan Times & EurasiaNet & TOLOnews (HTTP 403, bot-blocked),
    SWI swissinfo.ch (410 Gone), Kyiv Independent & Kuwait Times & Kazinform &
    MyRepublica (Nepal) & Kuensel (Bhutan) (404, no resolving path found),
    Iran International, Rudaw, The Peninsula Qatar, Oman Observer, The
    Brussels Times, NewsDay & The Citizen (Tanzania) & GhanaWeb & Mmegi
    (Botswana) & FrontPageAfrica (Liberia) & Business in Cameroon (served
    HTML/challenge pages at the feed path, no items), The East African &
    Daily Nation (Kenya), Graphic Online (Ghana), Ahram Online & Egypt Today
    (Egypt), Morocco World News, Herald & Monitor (Zimbabwe/Uganda), Addis
    Standard, Sudan Tribune, Daily Mirror & Newsfirst & FT.lk (Sri Lanka),
    bdnews24 (Bangladesh) (403/HTML on repeated checks — dropped rather than
    guessed around).
- **2026-08-31** — India regional-language pack: 41 candidate endpoints fetched
  with `HttpClient`'s own UA (never a spoofed one), **33 ship**. Gated on a
  parseable root, ≥5 items, expected script, and a newest item ≤7 days old —
  not merely a 200. Dropped after fetching: `punjabi.abplive.com/feed` (404,
  the working path is `/home/feed`), `thanthitv.com/feed` (well-formed, zero
  items), the five `oneindia.com` feeds (403 to any UA without "Mozilla"; two
  of them additionally ~6 weeks stale behind a daily-refreshed
  `lastBuildDate`), `sambadodisha.com/feed` (200, but serving casino spam),
  plus Kerala Kaumudi, Deshabhimani, Manorama, Asianet, Madhyamam, MediaOne,
  Janmabhumi, Prajavani, Udayavani, Vijaya Karnataka, Eenadu, Loksatta, Sakal,
  Anandabazar, Navbharat Times, the Samayam family, Jagbani, and three
  Assamese candidates (404/403/500/empty between them). **No Assamese feed
  could be verified live — that language is an honest gap, not an oversight.**

---

## RESERVED — never decide, never suggest in-product
Still reserved: license; any visual metaphor language; final taxonomy labels;
any mythological/metaphorical naming; Tier B paid-key decisions. No
Marvel/Loki/TVA references anywhere (loom image guides form only, never copy).

**Decided by the owner** (not by this build):
- The app name — **"Nooz"** — delivered via the owner's own design mocks on
  2026-07-10 and applied per decision D5.
- The app icon — the owner's 512×512 logo (2026-07-11/12 mock drop), applied
  per decision D6 (launcher mipmaps + `fastlane/.../images/icon.png`). No
  longer reserved/open.
- The applicationId/package — **`dev.asystemofcells.nooz`** — forced by the
  owner's Play Console listing (2026-07-20); applied via a new
  `riverwip.applicationId` property, `AppInfo.PACKAGE_BASE`, and the `full`
  flavor's applicationIdSuffix (removed, so `bundleFullRelease`'s AAB matches
  Play's registered package exactly). `riverwip.packageBase` — every module's
  internal `namespace`, matching the actual `xyz.mdhv.riverwip` Kotlin package
  declarations throughout the source tree — deliberately stays untouched; a
  first attempt that repointed it at the new package broke every module's
  generated-R resolution, since renaming *that* for real would mean moving
  every module's Kotlin package + directory structure. The `foss`/F-Droid
  flavor keeps its own `.foss` suffix, and debug builds keep `.debug` — only
  the Play-bound release id changed. No longer reserved/open. Any device with
  an old `xyz.mdhv.riverwip.full`-signed test build installed will need to
  uninstall it first — Android treats the new applicationId as a
  different app, not an in-place update.
