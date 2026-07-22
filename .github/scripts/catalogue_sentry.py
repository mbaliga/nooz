#!/usr/bin/env python3
"""CI-side catalogue sentry (brief P6): probes Tier A/B endpoints, not the
device. Nothing here runs on a user's phone and no user data is involved —
this only ever looks at the app's own list of provider endpoints, which are
public news feeds/APIs.

Diffs the current probe results against catalogue/catalogue.snapshot.json's
last recorded state. On drift (or when SIMULATE_DRIFT=1, which exercises the
same path deterministically for testing — brief's own P6 gate: "simulated
tier change -> Action opens a correct PR on a fork") it rewrites the
snapshot and a plain-language DRIFT_REPORT.md, and tells the workflow (via
$GITHUB_OUTPUT) whether a PR should be opened.
"""
import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timezone

SNAPSHOT_PATH = "catalogue/catalogue.snapshot.json"
REPORT_PATH = "catalogue/DRIFT_REPORT.md"
TIMEOUT_SECONDS = 15
USER_AGENT = "river-catalogue-sentry/0.1 (+CI probe, not a user device)"


def probe(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            return resp.status, dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers or {})
    except Exception as e:
        return None, {"error": str(e)}


def is_rate_limited(status, headers):
    if status == 429:
        return True
    return any(k.lower() == "retry-after" for k in headers)


def main():
    with open(SNAPSHOT_PATH) as f:
        snapshot = json.load(f)

    simulate = os.environ.get("SIMULATE_DRIFT") == "1"
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    drifted = []
    simulated_one = False

    for svc in snapshot["services"]:
        probe_url = svc.get("probeUrl") or svc.get("url")
        if probe_url is None:
            continue  # keyed/builder-only entries: nothing to probe without a key

        prior = svc.get("lastProbe") or {}
        had_prior_check = prior.get("checkedAt") is not None

        if simulate and not simulated_one:
            # Deterministically exercise the drift path without depending on
            # a real provider actually being down during this CI run.
            status, headers = 503, {}
            simulated_one = True
        else:
            status, headers = probe(probe_url)

        rate_limited = is_rate_limited(status, headers)
        was_ok = prior.get("httpStatus") == 200 and not prior.get("rateLimited", False)
        is_ok = status == 200 and not rate_limited

        new_probe = {"checkedAt": now, "httpStatus": status, "rateLimited": rate_limited}
        svc["lastProbe"] = new_probe

        if had_prior_check and was_ok != is_ok:
            drifted.append({"id": svc["id"], "title": svc["title"], "prior": prior, "current": new_probe})

    with open(SNAPSHOT_PATH, "w") as f:
        json.dump(snapshot, f, indent=2)
        f.write("\n")

    gh_output = os.environ.get("GITHUB_OUTPUT")

    if drifted:
        lines = [
            "# Catalogue drift report",
            "",
            f"Checked {now}. {len(drifted)} service(s) changed availability:",
            "",
        ]
        for d in drifted:
            lines.append(f"- **{d['title']}** (`{d['id']}`): {d['prior']} -> {d['current']}")
        lines += [
            "",
            "This repo has no fork of / token for the real provider-catalogue "
            "project (see catalogue/README.md), so this PR targets this "
            "repo's own tracked mirror. Configure `CATALOGUE_FORK_REPO` "
            "(repo variable) and `CATALOGUE_FORK_TOKEN` (secret, a PAT with "
            "write access to that fork) to point it at the real project.",
        ]
        with open(REPORT_PATH, "w") as f:
            f.write("\n".join(lines) + "\n")
        summary = f"{len(drifted)} service(s) drifted"
        print(summary)
        if gh_output:
            with open(gh_output, "a") as f:
                f.write("drifted=true\n")
                f.write(f"summary={summary}\n")
    else:
        print("No drift detected.")
        if gh_output:
            with open(gh_output, "a") as f:
                f.write("drifted=false\n")


if __name__ == "__main__":
    main()
