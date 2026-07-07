# river *(working name — final name RESERVED)*

A news reader whose subject is **omission**. It shows the shape of what flowed
past — *from the sources you chose* — against the shape of what you actually read,
abstracted over time. It never claims to show "all the news": the denominator is
always your declared source-set.

This is a tool-as-argument. Every mechanism is **legible** (you can see what it
did), **contestable** (you can see why, and disagree), and **reversible** (you can
undo it). Where a conventional news app optimizes engagement, this one refuses it
explicitly.

## Refusals (features, not omissions)
- **No telemetry, analytics, or transmitting crash reporters.** Nothing leaves the
  device except your own fetches to your own chosen sources.
- **No push notifications.** The app never interrupts.
- **No engagement ranking by default** — chronological or source-grouped; any
  other order is user-selected and labeled with its rule.
- **The river has banks:** feeds end with an explicit end-of-feed marker, never
  infinite backfill.
- **No streaks, badges, goals, or gamification.**
- **Total inspectability:** every classification, flag, and number is tappable to
  reveal the exact rule, lexicon entry, or formula behind it.

## Stack
Kotlin + Jetpack Compose, native Android (minSdk 31). Multi-module, manual DI
(no Hilt/Koin). Room + DataStore + Android Keystore + WorkManager. Two flavors:
`foss` (F-Droid-eligible, zero proprietary deps) and `full` (adds an ML Kit
inference provider). Design tokens from the [Hyle Design System](https://github.com/mbaliga/hyle-design-system).

## Modules
| Module | Role |
| ------ | ---- |
| `:core:model` | Pure Kotlin/JVM domain + analysis (testable without an SDK) |
| `:core:data` | Room, DataStore, fetch, dedup, classify, repositories |
| `:core:inference` | One inference interface, three providers, fidelity guard |
| `:core:design` | Design token contract, theme, register-correct copy helpers |
| `:feature:sources` | Add/manage sources, autodiscovery, OPML, starters |
| `:feature:reader` | Typography-first reader + full-text extraction |
| `:feature:river` | The centerpiece visualization + cross-section metrics |
| `:feature:lens` | Tap-to-defuse affect-span detection + guarded rewrite |

## Build
```bash
./gradlew :core:model:test          # pure-JVM core tests (no Android SDK needed)
./gradlew assembleFossDebug         # F-Droid-eligible flavor
./gradlew assembleFullDebug         # adds ML Kit provider where supported
```

## Status
See [`STATE.md`](STATE.md). Currently: P0 scaffold complete, P1 sources in progress.

## License
**RESERVED** — see [`LICENSE.RESERVED`](LICENSE.RESERVED). F-Droid eligibility for
`foss` is gated on this choice.
