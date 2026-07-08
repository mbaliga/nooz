# catalogue/

This directory is the **CI-side catalogue sentry's** working state (brief
§P6). It is unrelated to what the app fetches at runtime — the app's
optional remote `catalogue.json` refresh (`CatalogueRepository`, Sources
screen) points at a URL the user supplies, never at anything in this repo.

- `catalogue.snapshot.json` — a hand-maintained mirror of the app's built-in
  verified starters (`core/model/.../Starters.kt`), in a superset schema that
  also carries CI-only probe bookkeeping (`lastProbe`). The scheduled
  [`catalogue-sentry`](../.github/workflows/catalogue-sentry.yml) GitHub
  Action re-probes every entry's `probeUrl` and updates this file in place.
- `DRIFT_REPORT.md` — generated only when drift is detected; becomes the PR
  body. Not checked in otherwise.

**Why this repo's own mirror, and not "the provider-catalogue repo"**: the
brief's P6 spec describes an external `provider-catalogue` project this app
would eventually refresh from and this sentry would open PRs against. That
project is outside this build's scope — this repo has no fork of it and no
token authorized to push to one. Rather than fabricate a URL or fake a
result, the sentry is built to do the real thing (probe live endpoints, diff
against last-known state, open a real PR) against a configurable target:
set the `CATALOGUE_FORK_REPO` repo variable and a `CATALOGUE_FORK_TOKEN`
secret (a PAT with write access to that fork) to point it at the real
project once one exists; until then it opens the PR here, against this
tracked mirror, which is the closest honest default and still fully
exercises the brief's own gate ("simulated tier change → Action opens a
correct PR") via the `workflow_dispatch` `simulate_drift` input.
