# Owner verification: Nooz iOS shell

Nothing below has been done. Every item is NEEDS-DEVICE-VALIDATION (or CI-RUN-NEEDED). The
container had no Mac, Xcode, simulator or iPhone/iPad.

## First: make CI go green
- [ ] Push; open the **iOS** workflow. `shell-logic-linux` should pass; `ios-build` is the first real
      compile of `Nooz/Sources/*.swift`. Expect to fix compile errors there (the app target has never
      been through swiftc). Send the log back.
- [ ] If `xcodegen generate` complains about `project.yml`, the spec is the first suspect.
- [ ] `macos-15` and the default Xcode on it are assumptions; the deployment target is iOS 16.

## Simulator or device (iPad Pro M4, iPhone 12 mini on record)
- [ ] App launches to the reader, not a blank page (custom-scheme loading, ES modules via the handler).
- [ ] Add a source, close and reopen the app: sources and reads persist (IndexedDB on a custom scheme).
- [ ] A feed with no CORS headers loads (native `/api/feed`).
- [ ] Open an article from a headline-only feed: full text appears (shim + Readability + `/api/page`).
- [ ] Tapping an external link opens Safari; the reader does not navigate away.
- [ ] An `http://` feed fails with a legible error (D-iOS-1) rather than hanging.
- [ ] Safe areas: notch, home indicator, iPad multitasking and rotation. Fonts load (Hyle, PT Serif).
- [ ] Offline launch: reader opens, cached items read, fetch errors are honest.
- [ ] Airplane mode then reconnect; large feed memory; 3 MB page cap on a heavy page.

## Before any submission
- [ ] Enrol in the Apple Developer Program; add a **NAMES.md** row for `com.asoc.nooz` (plan R11).
- [ ] Re-run Xcode's privacy report on a real archive and compare to `PrivacyInfo.xcprivacy`.
- [ ] Decide the App Review risk: a web-wrapper can be rejected under guideline 4.2; the shell's native
      networking is the defence, unproven.
- [ ] Name the Apple crash-report exception (PT:D-BD) on the listing and an about/privacy page.
- [ ] Licence: nooz is RESERVED; an App Store listing is a distribution choice (OQ-12), yours.
- [ ] Signing lane: create a GitHub environment with required reviewers and add the secrets there.
