# STATE

Living build state. Updated every session (brief §0).

- **Working register:** omission. Name: **Nooz** (owner-decided 2026-07-10, see
  D5). License, taxonomy labels, metaphor language remain **RESERVED** — see
  §RESERVED. Package placeholder `xyz.mdhv.riverwip` (rename still pending).
- **Current phase:** P0–P7 substantially built. P7's a11y pass, F-Droid/Play
  metadata skeleton, and screenshot script are in; baseline profiles are an
  honestly-logged gap (see P7 section below).

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
- **LocalLlamaProvider**: model manager (download state, SHA-256 checksum,
  storage budget) is real. `rewrite()` returns a plain failure — no llama.cpp
  JNI binding is integrated in this session, and faking a "successful" rewrite
  would defeat the entire point of `FidelityGuard` (small models fabricate;
  this app must never pretend one is running when it isn't).
- **ModelCatalog**: Qwen3-4B-Instruct / Gemma3-4B are named per the brief, but
  `downloadUrl`/`sha256` are empty. Brief §0 requires live-verifying feed/API
  URLs at build time; the same standard applies to a multi-GB model mirror,
  and this session did not verify one. Populate both fields (and re-derive the
  checksum) once a specific, verified mirror is chosen.
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

## Schema versions
- Data model: **v1**, materialized in Room (`SourceEntity`, `ItemEntity`,
  `ReadEventEntity`, `WeeklyAggregateEntity`).
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
- **Not yet verified**: any GGUF model mirror URL for `ModelCatalog` (P5) — see
  D3/open questions above. Do not populate `downloadUrl`/`sha256` without
  actually checking the mirror resolves and the checksum matches.

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
