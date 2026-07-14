# STATE

Living build state. Updated every session (brief §0).

- **Working register:** omission. Name: **Nooz** (owner-decided 2026-07-10, see
  D5). License, taxonomy labels, metaphor language remain **RESERVED** — see
  §RESERVED. Package placeholder `xyz.mdhv.riverwip` (rename still pending).
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
| P5 | The Lens (tap-to-defuse) | ◑ UI + guard + router complete; **inference execution honestly stubbed** (see below) |
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

### P5 — the one deliberately incomplete piece, and why
Detection, evidence, the fidelity guard, session-ephemeral defuse state, the
reader overlay, and the defuse bottom sheet are all real and wired end-to-end.
What is **honestly stubbed**, and must stay that way until someone can verify
against the real thing:
- **LocalLlamaProvider**: `isAvailable()` is real (any `.gguf` on disk); model
  *download* (real catalogue, streaming + progress, storage budget, delete) is
  real too — see D18. `rewrite()` still returns a plain failure — no llama.cpp
  JNI binding is integrated in this session, and faking a "successful" rewrite
  would defeat the entire point of `FidelityGuard` (small models fabricate;
  this app must never pretend one is running when it isn't). A downloaded
  model sits on disk, verified-reachable, waiting for that binding.
- **UrbanaProvider**: real ContentProvider discovery attempt against
  `com.urbana.daemon.discovery` — correctly reports "not discoverable" since no
  real daemon exists to test against here (brief: absent support hides the
  provider, never errors — this is the correct behavior, not a bug).
- **MlKitProvider**: lives in `:core:inference`'s `full` source set only
  (so `foss` never references it). No real ML Kit GenAI dependency added yet
  (evolving API, no Pixel-class device to verify against) — conservatively
  reports unavailable.
- **Net effect**: `InferenceRouter.rewrite(...)` will return `Failed(...)` on
  any real device today, and the Lens UI shows that failure honestly (never a
  silent no-op, never a fake success). Wiring one real provider (most likely
  LocalLlamaProvider via a llama.cpp AAR) is the highest-value remaining P5 work.

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

---

## RESERVED — never decide, never suggest in-product
Still reserved: final package; license; any visual metaphor language; final
taxonomy labels; any mythological/metaphorical naming; Tier B paid-key
decisions. No Marvel/Loki/TVA references anywhere (loom image guides form only,
never copy).

**Decided by the owner** (not by this build):
- The app name — **"Nooz"** — delivered via the owner's own design mocks on
  2026-07-10 and applied per decision D5.
- The app icon — the owner's 512×512 logo (2026-07-11/12 mock drop), applied
  per decision D6 (launcher mipmaps + `fastlane/.../images/icon.png`). No
  longer reserved/open.

The applicationId/package stays `xyz.mdhv.riverwip` until the owner schedules
the rename (changing it breaks in-place upgrades of installed test builds).
