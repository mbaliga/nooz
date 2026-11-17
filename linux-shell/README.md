# linux-shell/ — Nooz thin client for the Linux desktop

**Read this first: what this is and is not.** This is **the Nooz web reader (`web/`) in a desktop
window**, hosted by the same QML + QtWebEngine shell as the Ubuntu Touch click
(`../ubuntu-touch/qml/Main.qml`, shipped here unmodified) with a small desktop window around it
(`qml/Desktop.qml`). It is a **thin client**: per plan rule R12 it is **never called a port of the
Android app**. It has no Room store, no on-device llama.cpp or Kokoro, no Lens, no widgets, no
background fetch and no BYOK keys.

The plan's Linux route (§6.1: Compose Multiplatform Desktop over a converted `:core:model` /
`:core:data`, steps L0 to L7, about 8 engineer-weeks) is **not done**; `../desktop/` still holds
only its L0 skeleton (empty window plus tests), and `../packaging/linux/` its tarball scaffold.
This shell exists because the reader is the one Nooz surface that already works everywhere, and a
native fetch bridge makes it better than the hosted copy on privacy (no proxy hop through anyone's
server; see D-LX-2). It does **not** replace L1 to L7.

Evidence key: **BUILT-HERE** (run in the authoring container, seen to pass) / **CI-LANE-WRITTEN**
(workflow exists, never run) / **CODE-ONLY** / **NEEDS-DEVICE-VALIDATION** (a real desktop).

## Layout

| Path | What | Evidence |
| --- | --- | --- |
| `qml/Desktop.qml` | Resizable `Window` around `Main.qml`; data directory name `nooz`; download handler (accepts `blob:`/`data:` only, saves to `~/Downloads`, shows a toast) | **BUILT-HERE**: loaded and exercised by `tests/smoke.sh` (Qt 5.15, Xvfb, software GL). Never seen on a real desktop |
| `bin/nooz` | Launcher: finds `qmlscene`, prints distro install hints, `exec`s it. Sets no sandbox-weakening variable | **BUILT-HERE**: `tests/launcher.test.sh` (fake `qmlscene`, symlink resolution, missing-Qt exit 127) |
| `scripts/build-tarball.sh` | stage (reuses `../ubuntu-touch/scripts/stage.sh`) and assemble `nooz-linux-thin-client-UNSIGNED-not-for-release.tar.gz`, arch-independent | **BUILT-HERE** |
| `scripts/install.sh` | Per-user install to `~/.local/opt/nooz-shell`, link `~/.local/bin/nooz`; `--uninstall`. No sudo, no network, no desktop entry | **BUILT-HERE** (throwaway `HOME`) |
| `tests/smoke.sh` | Builds, extracts, boots the window headless; checks reader boot, both shims, bridge refusal of an internal address, a `blob:` download saved, data dir | **BUILT-HERE** |
| `scripts/check.sh` | `bash -n`, `shellcheck` if present, identifier lint | **BUILT-HERE** |
| `../.github/workflows/linux-shell.yml` | Tests, lint, tarball, headless smoke, draft-release attach (tags only) | **CI-LANE-WRITTEN** |
| `OWNER_VERIFY.md` | What only a real Linux desktop can show | NEEDS-DEVICE-VALIDATION |

## Install and run (from a release tarball)

```
tar xzf nooz-linux-thin-client-UNSIGNED-not-for-release.tar.gz
cd nooz-linux-thin-client-UNSIGNED-not-for-release && ./install.sh
nooz                 # needs ~/.local/bin on PATH
./install.sh --uninstall
```

Runtime dependency: **Qt 5.15 `qmlscene` with QtWebEngine and QtQuick.Window**, from your
distribution. The shell does not bundle Qt.

- Debian/Ubuntu: `sudo apt install qmlscene qml-module-qtwebengine qml-module-qtquick-window2 qml-module-qtquick-dialogs   # dialogs: OPML import, untested`
- Fedora: `sudo dnf install qt5-qtdeclarative qt5-qtwebengine`
- Arch: `sudo pacman -S qt5-declarative qt5-webengine`

Qt 5 is end-of-life upstream and is being dropped by some distributions (QtWebEngine 5 in
particular); on such a system this shell will not install. That is the main durability risk of the
approach and the reason the plan's Compose route stays the destination (D-LX-1).

## Build from source

```
npm ci --prefix web
bash linux-shell/scripts/build-tarball.sh          # linux-shell/build/out/*.tar.gz
bash linux-shell/tests/launcher.test.sh            # no Qt needed
bash linux-shell/tests/smoke.sh                    # needs xvfb, mesa, qmlscene
```

## Where your data goes

- Feeds and articles: fetched by the shell straight from the publisher (https only, no internal
  addresses). Nothing is sent to any Nooz server; there is none. No telemetry, no update check.
- The reader's own storage (IndexedDB, local storage): `~/.local/share/nooz/QtWebEngine/nooz`.
  No cookies persist; no HTTP cache.
- Exports (OPML, clipping image): `~/Downloads`. `blob:`/`data:` downloads only.

## Decisions (LX-local, numbered so the owner can overrule)

- **D-LX-1 Shell first, Compose later.** The plan's L1 to L7 needs the KMP conversion of
  `:core:model` and `:core:data` and a desktop IA the owner has not mocked (NQ-2). This shell costs
  days, ships the reader exactly as it is on the web, and shares one QML source with the Ubuntu
  Touch click. If L2 lands, this becomes the fallback surface, not the product.
- **D-LX-2 Native fetch, no proxy** (same as D-UT-3): the stricter privacy posture than the hosted
  reader, where Vercel sees every feed URL. https only (D-UT-2).
- **D-LX-3 Tarball plus per-user install, no deb/rpm/Flatpak/Snap.** Those need the licence
  metadata ruling (OQ-12) and, for Flatpak/Snap, an app id (below).
- **D-LX-4 No desktop entry, no reverse-DNS id.** Plan R11 wants a NAMES.md row first (NAMES.md
  lives outside this repo), and `../packaging/linux/lint_ids.py` enforces it for that tree;
  `scripts/check.sh` runs the same lint here. Consequence: Nooz does not appear in the app menu;
  launch it as `nooz`. Owner follow-up: add the NAMES.md row, then a `.desktop` file and icon
  (the 512px icon is already in the tarball at `www/icons/`).
- **D-LX-5 Chromium sandbox stays on.** The launcher sets nothing. (The UT click turns it off for
  AppArmor reasons, D-UT-4; a desktop has no such reason.) Only the container smoke test disables
  it.
- **D-LX-6 qmlscene as the host.** A compiled launcher would give a proper app name, window class
  and icon, but then this is a C++/Qt project with per-arch builds. `qmlscene` keeps the artifact
  pure QML and arch-independent. Cost: the window class is `qmlscene` until a launcher exists, so
  window managers group it under that name.

## Known gaps (code not written, not "unverified")

- **No desktop entry or icon registration** (D-LX-4), no window icon.
- **No in-app file picker verification.** OPML import uses the reader's `<input type=file>`; the
  stock QtWebEngine QML file dialog needs `qml-module-qtquick-dialogs`, which is not in the install
  line above and has not been tested (OWNER_VERIFY step 6).
- **Wayland** runs through XWayland (Qt 5 default); native Wayland not tried.
- **Keyboard shortcuts and desktop IA** are whatever the web reader already has; none are added
  (NQ-2 unruled).
- **No background fetch**: the loom counts what flowed past while Nooz was open (NQ-5).
