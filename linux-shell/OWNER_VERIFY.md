# Owner verification: Linux desktop thin client

Everything below needs a real Linux desktop (the authoring container only had Xvfb and software
GL). Report results as pass / fail / n.a.; a fail with the terminal output is more useful than a
screenshot. Evidence class for all of these: NEEDS-DEVICE-VALIDATION.

1. **Install.** Install the Qt 5 packages from README, download the CI tarball (draft release), run
   `./install.sh`, then `nooz`. Does a window open, and does the reader render with its fonts?
2. **Wide window.** Resize from narrow to full screen. Does the reader lay out sensibly, or is it a
   phone column in the middle (expected today; no desktop IA exists)?
3. **Add a feed** (https). It should load without a proxy. Check with `ss -tnp | grep qmlscene`
   or a firewall prompt that only the feed's host is contacted.
4. **Read an article** (Readability extraction through the bridge). Images load; links open in your
   default browser, not inside the window.
5. **Persistence.** Quit and relaunch: feeds and read state remain. Look in
   `~/.local/share/nooz/QtWebEngine/nooz`.
6. **Files.** OPML export: a file lands in `~/Downloads` and a toast names it. OPML import: does a
   file dialog open (needs `qml-module-qtquick-dialogs`)? Clipping image export likewise.
7. **Sandbox.** Run `nooz` from a terminal; no "sandbox" warnings. (If Chromium's sandbox fails on
   your kernel config, report the message; do not work around it by setting
   `QTWEBENGINE_DISABLE_SANDBOX` without telling me.)
8. **Wayland session.** Does it start (via XWayland)? Fractional scaling blurry? HiDPI sizes right?
9. **Uninstall.** `./install.sh --uninstall` removes the launcher and install dir; data stays.
10. **Distros.** Which distro and Qt version did you try? Is QtWebEngine 5 still packaged there?

Owner follow-ups that are not tests: NAMES.md row for the Linux app id (then a `.desktop` file and
window icon), the OQ-12 licence ruling (unlocks deb/rpm/Flatpak), and whether the Compose route
(plan L1 to L7) should proceed or this shell is enough for now.
