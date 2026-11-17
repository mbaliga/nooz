#!/usr/bin/env python3
"""Policy checker for the Nooz click layout (plan step U3's 'policy checker').

Usage: check-click.py <installed click root>

Checks the things a reviewer would bounce and the things this repo's rules forbid:
  * manifest.json parses, name/version/framework match config/identity.env (single source)
  * the AppArmor policy asks ONLY for common groups: networking + webview (plan section 6.2);
    no unconfined, no accounts/location/camera/microphone/etc, no `read_path`/`write_path`
  * the .desktop file points at a file that exists and sets no telemetry/proxy environment
  * no secrets-shaped files and no analytics/crash-reporter strings in what ships (rule: no
    telemetry, ever). A string scan is a tripwire, not a proof.
Exit 0 only when everything holds. This is a lint, not click-review.
"""
import json
import re
import sys
from pathlib import Path

ALLOWED_GROUPS = {"networking", "webview"}
SCAN_SUFFIXES = {".qml", ".js", ".json", ".desktop", ".apparmor", ".html"}
# Strings that would mean the shipped app phones home. The reader's own code mentions none.
BANNED = [r"google-analytics", r"googletagmanager", r"sentry\.io", r"crashlytics", r"mixpanel",
          r"segment\.io", r"plausible\.io", r"posthog", r"telemetry\.", r"datadoghq"]
errors = []


def err(msg):
    errors.append(msg)


def identity(repo_ub):
    out = {}
    for line in (repo_ub / "config/identity.env").read_text().splitlines():
        m = re.match(r"^([A-Z_]+)=(.*)$", line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def main(root):
    root = Path(root)
    ub = Path(__file__).resolve().parents[1]
    ident = identity(ub)

    mf = root / "manifest.json"
    try:
        man = json.loads(mf.read_text())
    except Exception as e:  # noqa: BLE001
        err(f"manifest.json unreadable: {e}")
        man = {}
    for key, want in (("name", "CLICK_NAME"), ("version", "CLICK_VERSION"), ("framework", "CLICK_FRAMEWORK")):
        if man.get(key) != ident.get(want):
            err(f"manifest {key}={man.get(key)!r} != identity.env {want}={ident.get(want)!r}")
    if man.get("architecture") != "all":
        err("a pure-QML click must be architecture 'all'")
    if re.search(r"@[A-Z_]+@", json.dumps(man)):
        err("manifest still has an unreplaced @TOKEN@")
    if not re.match(r"^[a-z0-9][a-z0-9.+-]*$", man.get("name", "")):
        err("click name is not a valid lowercase click name")
    hooks = man.get("hooks", {})
    if set(hooks) != {"nooz"}:
        err(f"expected exactly one hook 'nooz', got {sorted(hooks)}")

    for hook in hooks.values():
        for kind in ("apparmor", "desktop"):
            if not (root / hook.get(kind, "<missing>")).is_file():
                err(f"hook file {hook.get(kind)!r} not in the click")

    try:
        pol = json.loads((root / "nooz.apparmor").read_text())
        extra = set(pol.get("policy_groups", [])) - ALLOWED_GROUPS
        if extra:
            err(f"AppArmor groups beyond the common set: {sorted(extra)}")
        if set(pol.get("policy_groups", [])) != ALLOWED_GROUPS:
            err("AppArmor must request exactly networking + webview")
        for forbidden in ("template", "read_path", "write_path"):
            if forbidden in pol:
                err(f"AppArmor key {forbidden!r} widens access; not allowed")
    except Exception as e:  # noqa: BLE001
        err(f"nooz.apparmor unreadable: {e}")

    desktop = (root / "nooz.desktop").read_text()
    exec_line = next((ln for ln in desktop.splitlines() if ln.startswith("Exec=")), "")
    m = re.search(r"qmlscene \$@ (\S+)$", exec_line)
    if not m or not (root / m.group(1)).is_file():
        err(f"Exec does not launch an existing qml file: {exec_line!r}")
    if re.search(r"(?i)proxy|telemetry|analytics", exec_line):
        err("Exec sets a proxy/telemetry-looking variable")
    icon = re.search(r"^Icon=(.+)$", desktop, re.M)
    if not icon or not (root / icon.group(1)).is_file():
        err("Icon= file missing from the click")

    for must in ("qml/Main.qml", "qml/js/HostBridge.js", "www/index.html", "www/app.bundle.js",
                 "shims/nooz-shims.js", "shims/Readability-LICENSE.md"):
        if not (root / must).is_file():
            err(f"missing {must}")
    if (root / "www/api").exists() or (root / "www/js").exists():
        err("server helpers / unbundled js shipped")
    if '<script type="module"' in (root / "www/index.html").read_text():
        err("module script in index.html (cannot load from file://)")

    for p in root.rglob("*"):
        if not p.is_file():
            continue
        if re.search(r"(?i)\.(keystore|jks|p12|pem|key)$|^\.env$|secret", p.name):
            err(f"secret-shaped file shipped: {p.relative_to(root)}")
        if p.suffix in SCAN_SUFFIXES:
            text = p.read_text(errors="ignore")
            for pat in BANNED:
                if re.search(pat, text, re.I):
                    err(f"{p.relative_to(root)}: matches banned telemetry pattern /{pat}/")

    if errors:
        print("check-click: FAIL")
        for e in errors:
            print("  -", e)
        return 1
    print(f"check-click: OK ({sum(1 for _ in root.rglob('*') if _.is_file())} files, name={man['name']} v{man['version']})")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1]))
