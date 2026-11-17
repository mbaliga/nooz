# Linux packaging (scaffold)

Packages the **empty skeleton window** from `desktop/` as a per-user tarball. It reads
no feeds and stores nothing; the window itself has not been launched on a display from
the build container. The tarball is **UNSIGNED** and named `...-UNSIGNED-not-for-release`.

```
./gradlew -p desktop createDistributable
bash packaging/linux/build-tarball.sh          # desktop/build/packaging/*.tar.gz
tar xzf nooz-linux-x64-UNSIGNED-not-for-release.tar.gz && ./nooz-linux-x64-UNSIGNED-not-for-release/install.sh
./install.sh --uninstall
```

`install.sh` copies the app image to `~/.local/opt/nooz` and links `~/.local/bin/nooz`.
It writes only under `$HOME`, uses no network and no privileges, and installs no
desktop entry or reverse-DNS app id: none exists before the owner adds a NAMES.md row
(R11, OQ-25). `check.sh` runs `bash -n`, `shellcheck` when present, and `lint_ids.py`,
which fails if an app id or desktop entry appears here.

Not done: deb/rpm, Flatpak and Snap wait on OQ-12. The CI `package` job (tags and manual
runs only) writes a draft GitHub Release and uploads no Actions artifacts (R6).
