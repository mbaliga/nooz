#!/usr/bin/env python3
"""CI-side ai-catalogue sentry: keeps ai-catalogue/models.json and
ai-catalogue/free-tiers.json honest without ever fabricating a fact.

Two independent jobs, mirroring the app's own two prior mechanisms (a model-liveness
probe like catalogue/'s news-feed sentry, and a staleness-only check like aarso's
now-retired scripts/update_free_tiers.py):

  1. Probe every models.json entry with a non-null downloadUrl (a plain HTTP liveness
     check — factual, safe to automate). Entries with downloadUrl=null are skipped
     entirely: there is nothing to probe, and this script never invents one. Rewrites
     each entry's `verifiedAt`/observed status in place and reports whether anything
     changed, so CI can open a real PR on drift.

  2. Age-check free-tiers.json's `lastUpdated` against a staleness window and
     best-effort fetch an upstream cross-reference for a human to diff against. This
     NEVER rewrites a provider's numbers — those are business-policy facts, not a
     fetchable/probable truth, so staleness only ever opens a reminder issue.

Run locally: python3 .github/scripts/ai_catalogue_sentry.py
"""
from __future__ import annotations

import datetime as dt
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
MODELS_PATH = ROOT / "ai-catalogue" / "models.json"
FREE_TIERS_PATH = ROOT / "ai-catalogue" / "free-tiers.json"
DRIFT_REPORT_PATH = ROOT / "ai-catalogue" / "DRIFT_REPORT.md"

UPSTREAM_FREE_TIERS = "https://raw.githubusercontent.com/cheahjs/free-llm-api-resources/main/README.md"
FREE_TIER_STALE_DAYS = 35
PROBE_TIMEOUT_SECONDS = 20
USER_AGENT = "nooz-ai-catalogue-sentry/1.0 (+CI probe, not a user device)"


# --------------------------------------------------------------------------- models


def probe_url(url: str) -> int | None:
    """HEAD-probe a download URL. Returns the HTTP status, or None on any failure.
    Never downloads the body — these are multi-gigabyte model files."""
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=PROBE_TIMEOUT_SECONDS) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception as e:  # noqa: BLE001 - best effort, network is unreliable
        print(f"  probe failed: {e}")
        return None


def check_models(simulate_drift: bool) -> tuple[bool, list[str]]:
    """Probe every model with a known downloadUrl. Returns (drifted, summary_lines)."""
    data = json.loads(MODELS_PATH.read_text(encoding="utf-8"))
    now = dt.date.today().isoformat()
    drifted: list[str] = []
    simulated_one = False

    for m in data["models"]:
        url = m.get("downloadUrl")
        if url is None:
            continue  # no verified mirror known — nothing to probe, never guessed.

        prior_status = m.get("_lastProbeStatus")
        if simulate_drift and not simulated_one:
            # Deterministically exercise the drift path without depending on a real
            # mirror actually being down during this run.
            status = 404
            simulated_one = True
        else:
            status = probe_url(url)

        was_ok = prior_status == 200
        is_ok = status == 200
        had_prior = prior_status is not None

        m["_lastProbeStatus"] = status
        if is_ok:
            m["verifiedAt"] = now

        if had_prior and was_ok != is_ok:
            drifted.append(
                f"- **{m['name']}** (`{m['id']}`): HTTP {prior_status} -> HTTP {status} "
                f"({'now resolving' if is_ok else 'no longer resolving — url may need a refresh'})"
            )
        print(f"  {m['id']}: HTTP {status}")

    MODELS_PATH.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return (len(drifted) > 0, drifted)


# ------------------------------------------------------------------------ free tiers


def age_days(last_updated: str) -> int | None:
    for fmt in ("%Y-%m-%d", "%Y-%m"):
        try:
            d = dt.datetime.strptime(last_updated, fmt).date()
            return (dt.date.today() - d).days
        except ValueError:
            continue
    return None


def fetch_upstream_free_tiers() -> str | None:
    try:
        with urllib.request.urlopen(UPSTREAM_FREE_TIERS, timeout=PROBE_TIMEOUT_SECONDS) as r:
            return r.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001 - best effort
        print(f"(could not fetch upstream free-tier reference: {e})")
        return None


def check_free_tiers() -> tuple[bool, int | None]:
    """Returns (stale, age_days). Never rewrites free-tiers.json."""
    data = json.loads(FREE_TIERS_PATH.read_text(encoding="utf-8"))
    last = data.get("lastUpdated", "")
    age = age_days(last)
    print(f"free-tiers.json lastUpdated={last!r} ({len(data.get('providers', []))} providers)")
    if age is None:
        print("could not parse lastUpdated — treat as stale")
    else:
        print(f"age: {age} days (threshold {FREE_TIER_STALE_DAYS})")

    upstream = fetch_upstream_free_tiers()
    if upstream:
        scratch = ROOT / "build" / "free_tiers_upstream.md"
        scratch.parent.mkdir(parents=True, exist_ok=True)
        scratch.write_text(upstream, encoding="utf-8")
        print(f"upstream snapshot written to {scratch} ({len(upstream)} bytes) — diff by hand to refresh figures")

    stale = age is None or age > FREE_TIER_STALE_DAYS
    return stale, age


# -------------------------------------------------------------------------------- main


def main() -> int:
    simulate = os.environ.get("SIMULATE_DRIFT") == "1"
    gh_output = os.environ.get("GITHUB_OUTPUT")

    print("== probing ai-catalogue/models.json ==")
    models_drifted, drift_lines = check_models(simulate)

    print("\n== checking ai-catalogue/free-tiers.json freshness ==")
    free_tiers_stale, age = check_free_tiers()

    if models_drifted:
        report = ["# ai-catalogue models drift report", "", f"{len(drift_lines)} model(s) changed availability:", ""]
        report += drift_lines
        DRIFT_REPORT_PATH.write_text("\n".join(report) + "\n", encoding="utf-8")
        print(f"\n{len(drift_lines)} model(s) drifted — see {DRIFT_REPORT_PATH}")
    else:
        print("\nno model drift detected.")

    if free_tiers_stale:
        print(f"free-tiers.json is STALE (age={age if age is not None else 'unknown'} days) — refresh by hand against sourceUrl.")

    if gh_output:
        with open(gh_output, "a", encoding="utf-8") as f:
            f.write(f"models_drifted={'true' if models_drifted else 'false'}\n")
            f.write(f"models_summary={len(drift_lines)} model(s) drifted\n")
            f.write(f"free_tiers_stale={'true' if free_tiers_stale else 'false'}\n")
            f.write(f"free_tiers_age={age if age is not None else -1}\n")

    # Exit non-zero only signals "something needs a human" for local/manual runs;
    # the workflow branches on the GITHUB_OUTPUT flags above, not on this exit code.
    return 1 if (models_drifted or free_tiers_stale) else 0


if __name__ == "__main__":
    sys.exit(main())
