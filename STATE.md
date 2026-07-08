# STATE

Living build state. Updated every session (brief §0).

- **Working register:** river / omission. Name, license, taxonomy labels, metaphor
  language are **RESERVED** — see §RESERVED. Package placeholder `xyz.mdhv.riverwip`.
- **Current phase:** P0–P5 substantially built (pure cores + Android/Compose UI
  for all five phases). Remaining: P6 (catalogue sensing) and P7 (hardening).

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
| P6 | Catalogue sensing layer | ☐ not started |
| P7 | Hardening & release prep | ☐ not started |

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

### Remaining
- P6: `catalogue.json` consumption (the schema already mirrors
  provider-catalogue's `services[]` — zero migration needed), local
  source-health monitor UI, CI-side sentry Action (probes Tier A/B, opens PRs
  against the catalogue repo — needs that repo's identity/credentials, not
  available in this session's scope).
- P7: a11y pass (TalkBack labels on lens spans + river regions), baseline
  profiles, F-Droid metadata (blocked on the RESERVED license), Play listing
  skeleton, screenshot script.
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

---

## Decisions (locked unless brief says otherwise)

- **D1 — Design tokens come from the Hyle Design System.** The owner cloned
  `mbaliga/hyle-design-system` into the build session. Hyle is the ecosystem's
  cross-platform token source of truth and already matches the brief's locked
  defaults (dark-first, violet `#8E7BFF`, provenance radium `#C7EF9E` on-device /
  cyan `#35E0FF` cloud, `#121212`-class surfaces). `:core:design/Tokens.kt`
  mirrors Hyle's shared tokens' values. The brief anticipated a re-skin "when
  final tokens land" — they landed. Font family is the one open swap-point (Hyle
  ships Archivo; brief placeholder was Plus Jakarta Sans) — bound to platform sans
  via `AppFontFamily` until the RESERVED font asset is dropped. This is reversible:
  revert to placeholder values in `Tokens.kt` alone.
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

## Schema versions
- Data model: **v1**, materialized in Room (`SourceEntity`, `ItemEntity`,
  `ReadEventEntity`, `WeeklyAggregateEntity`).
- Source registry schema: **v1** (mirrors provider-catalogue `services[]`).
- Catalogue consumption (`catalogue.json`): **not yet** (P6).

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
App name & final package; license; icon & any visual metaphor language; final
taxonomy labels; any mythological/metaphorical naming; Tier B paid-key decisions.
No Marvel/Loki/TVA references anywhere (loom image guides form only, never copy).
