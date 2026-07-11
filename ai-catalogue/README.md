# ai-catalogue/

The **canonical, periodically-updated data source for open-source/downloadable AI models and
cloud free-tier facts**, published from this repo so sibling apps in the constellation (aarso /
Workbench today; others as they exist) can consume one shared, honestly-maintained catalog
instead of each hand-maintaining and drifting independently.

This is **data, not code** — a build-time Gradle/Maven dependency on this repo is deliberately
not the contract. Consuming apps fetch the raw JSON over HTTPS (e.g.
`https://raw.githubusercontent.com/mbaliga/nooz/<branch>/ai-catalogue/models.json`), exactly the
way aarso already fetches its (now-superseded) `free_tiers.json` from a sibling repo. That keeps
the coupling to "one JSON schema", not "one build toolchain."

**Unrelated to `catalogue/`** — this repo's other `catalogue/` directory is the CI-side sentry
mirror for *news-source* feeds (RSS/Atom endpoints; brief §P6). `ai-catalogue/` is a completely
separate concern: AI models and cloud inference pricing/limits. Don't conflate the two; nothing
here talks to news feeds, and nothing there talks to model weights.

## Files

- **`models.json`** — open-source LLM (GGUF) and single-file Stable Diffusion checkpoints,
  each with `kind`, size, a resolved `downloadUrl` (or `null` when no verified mirror is known —
  see "Honesty rules" below), `sha256` when independently verified, `policySafe` (official/
  licensed release vs. a community "abliterated"/uncensored remix — the signal a policy-
  restricted storefront build needs to filter on), and `verifiedAt` (last confirmed HTTP 200).
- **`free-tiers.json`** — cloud LLM providers' free tiers (ongoing free vs. trial credit), each
  with a `sourceUrl` a human can re-verify the number against.

Fields prefixed with `_` (e.g. `_lastProbeStatus`) are CI-internal sentry bookkeeping — consumers
should treat them as opaque and ignore them; they aren't part of the documented consumer schema
above and may be added/removed/renamed without a `schemaVersion` bump.

Both carry a top-level `schemaVersion` (currently `1`) and `lastUpdated`, so a consumer can tell
at a glance how stale its local copy is before deciding whether to refresh.

## Honesty rules (binding on every update to this directory)

1. **Never fabricate a `downloadUrl` or `sha256`.** A model with no independently-verified
   mirror gets `downloadUrl: null`, not a guess. Two entries in `models.json`
   (`qwen3-4b-instruct-q4`, `gemma3-4b-q4`) are intentionally left this way — named per an
   internal on-device-rewrite-model brief, no verified GGUF mirror chosen yet.
2. **Never auto-write a free-tier figure.** Provider limits are business policy, not a
   fetchable fact — the CI sentry (below) only flags staleness and diffs against an upstream
   cross-reference; a human confirms each number against its own `sourceUrl` before committing.
3. **`sha256` is a convenience, not a substitute for the consumer's own checksum verification**
   after download — most entries here don't carry one (re-hashing multi-gigabyte files on every
   CI run isn't practical), so a consuming app must still verify integrity locally post-download.
4. **A model's `downloadUrl` resolving today doesn't mean it resolves next month.** HF mirror
   filenames drift; that's exactly why `verifiedAt` exists and why the sentry re-probes weekly.

## Consumer contract

- Treat every fetch of this directory as an **explicit, user-consented action** (a manual
  "Update catalog" button, or an auto-update the user opted into) — **never automatic/background
  network access**. This mirrors this repo's own no-telemetry stance and aarso's binding rule 1/2
  (on-device is the default; any network reach is a visible, opt-in "watched object").
- Carry the provenance through: show `hfRepo`/`hfFile`/`sourceUrl` in your own UI rather than
  presenting a number or a filename as something your app determined itself.
- Cache what you fetch, and ship a bundled fallback snapshot for the offline/first-run case —
  don't make catalog availability a hard dependency on network access at any point.
- A `null` field means "unknown," never "zero" or "none" — don't coerce it into a UI default that
  reads as a real claim (e.g. don't render a null `downloadUrl` model as "download available").

## Update cadence — `ai-catalogue-sentry`

`.github/workflows/ai-catalogue-sentry.yml` (weekly, Wednesday ~06:23 UTC) +
`.github/scripts/ai_catalogue_sentry.py`:

- **`models.json`**: HEAD-probes every entry's `downloadUrl` (entries with a `null` URL are
  skipped — nothing to probe). Records `verifiedAt`/an HTTP-status observation per entry and
  opens a real PR when something changed — mirroring `catalogue/`'s own sentry pattern
  (safe to automate: this is a factual liveness probe, not a business-policy number).
- **`free-tiers.json`**: age-checks `lastUpdated` against a 35-day staleness window and, if
  stale, opens/reuses a reminder **issue** (never a PR that silently rewrites a number) — porting
  aarso's own `update_free_tiers.py` mechanism, since that catalog's canonical home is now here.
