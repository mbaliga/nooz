# STATE

Living build state. Updated every session (brief §0).

- **Working register:** river / omission. Name, license, taxonomy labels, metaphor
  language are **RESERVED** — see §RESERVED. Package placeholder `xyz.mdhv.riverwip`.
- **Current phase:** P2 ingest. The pure analytical cores of P2/P4/P5 and the §8
  CVD palette have landed and are unit-verified ahead of their Android/UI wiring
  (see "Strategy" below). Remaining: the Android IO/UI for P2–P5, plus P6/P7.

---

## Phase status

| Phase | Title | Status |
| ----- | ----- | ------ |
| P0 | Scaffold | ✅ complete (CI green) |
| P1 | Sources | ✅ complete (CI green) |
| P2 | Ingest & classify | ◑ pure core done + verified; WorkManager fetch/persist wiring remains |
| P3 | Reader | ☐ not started |
| P4 | The River (centerpiece) | ◑ decomposition + metrics math done + verified; Compose canvas viz remains |
| P5 | The Lens (tap-to-defuse) | ◑ fidelity guard + affect-span detection done + verified; rewrite UI + providers remain |
| §8 | CVD palette | ✅ done + verified (build-failing pairwise test) |
| P6 | Catalogue sensing layer | ☐ not started |
| P7 | Hardening & release prep | ☐ not started |

### Strategy: analytical cores first, then UI
The brief's load-bearing, correctness-critical logic is pure and lives in
`:core:model`, so it is unit-verified locally and in CI *ahead* of the Android
surfaces that present it. Done + green:
- **P2 ingest core** — `FeedParser` (RSS/Atom/RDF/JSON, XXE-hardened), `Simhash`
  (char-shingle), `Dedup`, `Classifier` (evidence-carrying), `Ingest` transform.
- **P4 river math** — `RiverAnalysis`: coverage, over/under, breadth, and the
  supply-vs-drift decomposition that sums to Δ exactly (500-case property test).
- **P5 lens safety** — `FidelityGuard` (adversarial corpus caught 100%),
  `BiasLexicon` + `AffectSpanDetector`.
- **§8** — `Cvd` + `TopicPalette` + build-failing pairwise CVD test.

Remaining (Android/Compose/IO, CI-compile-verified as they land):
- P2: WorkManager scheduled fetch (ETag/Last-Modified, per-source failure
  tracking), Item/ReadEvent/WeeklyAggregate Room tables, ingest→persist wiring.
- P3: reader list + typography reader, jsoup full-text extraction, end-of-feed marker.
- P4: Compose/Canvas river visualization (un-read as dark mass), cross-section panel.
- P5: reader underlines + defuse bottom sheet, `:core:inference` providers
  (Urbana/llama.cpp/ML Kit), session-ephemeral defuse choices, sterile-lens toggle.
- P6: `catalogue.json` consumption + local source-health monitor + CI sentry Action.
- P7: a11y, baseline profiles, F-Droid/Play metadata, screenshots.

### P0 — done
- Multi-module Gradle build: `:app`, `:core:model|data|inference|design`,
  `:feature:sources|reader|river|lens`. Version catalog (`gradle/libs.versions.toml`).
- Gradle wrapper 8.14.3 (jar vendored — `services.gradle.org` is unreachable from
  this dev sandbox, so `gradle wrapper` URL validation can't run here; CI resolves
  the distribution normally).
- Two flavors `foss` / `full` on the inference chain (`:core:inference`,
  `:feature:lens`, `:app`); other modules stay flavor-agnostic to avoid variant
  explosion. `foss` carries zero proprietary/Play deps.
- Manual DI: composition root `:app/AppContainer.kt` (no Hilt/Koin).
- Token contract in `:core:design` (`Tokens`, `Theme`, `Type`, `Copy`,
  `EmptyState`). Dark-first, violet `#8E7BFF`. **Values sourced from the Hyle
  Design System** (see Decisions D1). Behaviour never depends on token values.
- Themed shell (`MainActivity` → `RiverApp`) renders a register-correct empty state.
- CI (`.github/workflows/ci.yml`): pure-core tests + both-flavor unit tests, lint,
  assemble.
- `LICENSE.RESERVED`, `.gitignore`, `gradle.properties` (package base centralized).

### P1 — done (pure logic + Android layer)
- Pure (`:core:model`, verified locally): domain types, taxonomy, hashing/ids,
  canonical-URL normalization, feed URL builders (Google News/GDELT/Mastodon),
  HTML feed autodiscovery, OPML import/export, registry schema, verified starters,
  source-kind detector. 32 tests.
- Data (`:core:data`, verified via JVM harness): Room `SourceEntity`/DAO/DB,
  dependency-free `HttpClient` (gzip, conditional GET, redirects — foss-clean, no
  OkHttp), `FeedProbe`/`FeedProber`, `SourceRepository` (add-by-URL w/ discovery,
  starters, OPML, enable/disable/remove, health fields). Repo + probe tests pass.
- Feature (`:feature:sources`): `SourcesViewModel` + `SourcesScreen` — add-by-URL,
  candidate picker, one-click starters by region, builder/keyed rows (honestly
  labeled), OPML import/export via SAF, source list w/ enable + remove. Wired into
  `:app` via `AppContainer` (manual DI). *(Compose/Room compile validated in CI.)*
- Registry mirrors provider-catalogue `services[]` (`consumedAt: runtime`) →
  zero-migration P6.
- **Gate:** starters seed rss + googlenews + gdelt + mastodon kinds (≥4);
  denominator copy present on the sources surface + empty states. ✅

### CI note
- Unit tests run `testDebugUnitTest` (non-flavored modules) + `testFossDebugUnitTest`
  + `testFullDebugUnitTest` (flavored) so every module's tests execute.

---

## Decisions (locked unless brief says otherwise)

- **D1 — Design tokens come from the Hyle Design System.** The owner cloned
  `mbaliga/hyle-design-system` into the build session. Hyle is the ecosystem's
  cross-platform token source of truth and already matches the brief's locked
  defaults (dark-first, violet `#8E7BFF`, provenance radium `#C7EF9E` on-device /
  cyan `#35E0FF` cloud, `#121212`-class surfaces). `:core:design/Tokens.kt`
  mirrors Hyle's `tokens/*.json` values. The brief anticipated a re-skin "when
  final tokens land" — they landed. Font family is the one open swap-point (Hyle
  ships Archivo; brief placeholder was Plus Jakarta Sans) — bound to platform sans
  via `AppFontFamily` until the RESERVED font asset is dropped. This is reversible:
  revert to placeholder values in `Tokens.kt` alone.
- **D2 — Pure logic lives in `:core:model` (Kotlin/JVM, no Android).** All
  unit-critical algorithms (river decomposition, dedup simhash, classifier rules,
  lens fidelity guard, CVD palette math) sit here so they run without an Android
  SDK, locally and in CI. Android modules consume them and add only IO/UI. This
  refines, not reshapes, the brief's module sketch.

## Schema versions
- Data model: **v1** (not yet materialized in Room; see `:core:model` types P1/P2).
- Source registry schema: **v1** (mirrors provider-catalogue `services[]`).
- Catalogue consumption (`catalogue.json`): **not yet** (P6).

## Open questions (log now; surface at P4 gate per brief §10)
- Daily vs weekly default abstraction — built as a parameter; ship weekly.
- User-editable taxonomy — logged, not built in v1.
- On-device embedding classifier upgrade — logged, not built in v1.
- Direct-fetch IP exposure — document in v1; user-configured proxy is later.

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

---

## RESERVED — never decide, never suggest in-product
App name & final package; license; icon & any visual metaphor language; final
taxonomy labels; any mythological/metaphorical naming; Tier B paid-key decisions.
No Marvel/Loki/TVA references anywhere (loom image guides form only, never copy).
