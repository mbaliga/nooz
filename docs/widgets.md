# Home-screen widgets

Nooz ships six standard Android App Widgets (`AppWidgetProvider` + `appwidget-provider`
XML + `RemoteViews`), so any launcher can host them: Pixel Launcher, One UI, Nova, Orrery
Home. They are built and unit-tested here; **none of their on-device behaviour has been seen
running** (this build environment has no device or emulator). See "Owner-verify" at the end.

They follow the app's refusals on a surface the reader glances at without deciding to:

- **No engagement order.** Headlines are newest first by publication time and nothing else;
  clippings are most recently clipped first; topics keep the taxonomy's fixed order.
- **Every total names its denominator**: "from your 12 sources", never "all the news".
- **Lists end.** The headlines widget always closes with an end mark that says how many it
  showed and from whose sources, and it only counts rows the layout really has room for.
- **Nothing counts what you have not done.** No unread counts, streaks, goals or progress
  bars. Coverage and breadth are printed as a plain record, not as progress toward anything.
- **Every number leads to its rule.** The picture widget and the coverage widget open
  EDIT › Region & Topics, where the cross-section explains each of their numbers with its
  formula, and the coverage widget prints only numbers the cross-section shows. Edit ›
  Sources does not explain source health, so the health widget's words *are* the rules:
  "Last fetch failed", "No fetch in 48 hours", and so on.
- **Colour never carries meaning alone.** Topic colours (the CVD-verified `TopicPalette`)
  appear only in the large picture, where a legend names every band beside its swatch. The
  small and medium pictures draw the bands in neutral grey. Health states are a shape and
  words (▲ last fetch failed, ◆ asked Nooz to slow down, ◐ no fetch in 48 hours, ○ not
  checked yet). No red/green pair.
- **No network, no notifications, no telemetry, no new permissions, no new dependencies.**
  A widget only reads what the app already stored. Today in History never fetches from a
  widget; it shows the column the Stand already loaded.
- **No new metaphor in the product.** The picture widget is called "Flowed and read, this
  week", in the app's own existing words. Metaphorical naming is reserved to the owner
  (STATE.md §RESERVED); "river" stays an internal name (`RiverWidgetProvider`, `RiverBitmap`).

## The six widgets

Sizes are size classes chosen by the launcher from the widget's current size. Every widget
resizes from 2×1 upward; below its smallest layout it collapses to **tiny**: its name and
one summary line, the whole widget one tap.

| Widget | Template | Small | Medium | Large |
| ------ | -------- | ----- | ------ | ----- |
| Flowed and read, this week | image | grey picture + totals + "as of" | + "flowed"/"read" edge labels | coloured picture + a legend naming every band (name, % flowed / % read) |
| Headlines from your sources | list | 1 headline (3 lines) + end mark | 3 headlines (1 line) + end mark | 4 headlines (2 lines) + end mark |
| Today in History | list | 1 event (4 lines) + credit | 3 events | 5 events |
| Source health | list | summary row (5 lines) with counts | + 1 source not fetching normally | + 3 such sources, "and N more" |
| Clippings | list | newest clipping | 3 clippings | 4 clippings, "and N more" |
| Coverage and breadth | stat | coverage % + totals | + breadth (you) | + breadth (stream) and the formulas |

What tiny shows: the picture and coverage widgets, the totals (with "Week of …" when the
week is not the current one, and coverage with its denominator); headlines, the newest
headline; Today in History, only the column's date ("From 3 November…" for an earlier day),
never Wikipedia text, because there is no room for the CC BY-SA credit; health, the summary;
clippings, the count.

### Breakpoints: added up, not chosen

The launcher picks a layout from a size map without asking the app, so each breakpoint is
the height its layout really needs: if it were less, the launcher could pick a layout that
does not fit and clip its bottom, where the end mark and the "as of" time sit.
`WidgetSizing` (`:core:model`) adds each breakpoint up from the layout's parts, with every
text line at its most: 24dp padding, a 16dp line for 12sp text, a 20dp line for 14sp serif
text, rows of at least 48dp, and the margins. `WidgetLayoutBudgetTest` (`:app`) reads the
layout XML and fails if a text size, line count, margin or row slot there stops matching.
The part that takes the spare height (the rows, the picture, the stat caption) sits above
the footer, so the footer is never the part clipped.

The text parts scale with the reader's font size: at 1.3× every breakpoint moves up, so a
4×2 list falls back to its one-row layout rather than clipping its third row, and at 2× a
2×2 widget becomes the one-line summary. A font-size change redraws the widgets when Nooz's
process is running; otherwise at the next redraw.

At 1× font scale (dp, width × height; Android's own sizing table gives a portrait 2×2 as
130×220, 4×2 as 276×220, 4×3 as 276×337):

| Template | Small | Medium | Large |
| -------- | ----- | ------ | ----- |
| list, items (headlines, clippings) | 110×158 | 250×218 | 250×330 |
| list, events (Today in History) | 110×162 | 250×218 | 250×314 |
| list, health | 110×214 | 250×214 | 250×334 |
| stat (coverage) | 110×188 | 250×166 | 250×262 |
| image (flowed and read) | 110×158 | 250×150 | 250×278 |

So on a typical phone in portrait, 2×2 gets small, 4×2 medium and 4×3 large; 2×1 and 4×1
get tiny (`HomeWidgetsTest` pins this). The line heights are estimates from the fonts'
metrics; whether they hold on the owner's phone is on the owner-verify list.

### Flowed and read, this week (`RiverWidgetProvider`)
- **Shows** the latest weekly aggregate as a picture the app draws itself
  (`RiverBitmap`, geometry from `RiverPicture`): each topic's share of what flowed along the
  top edge, its share of what you read along the bottom, a band joining them. A topic you
  did not read pinches to a point. The image's alt text carries every number ("Week of
  2 Nov, from your 12 sources: 240 stories flowed and you read 18. By topic, …").
- **Colour**: only at large, where the legend has one entry per band (two a line, ten slots
  for the ten topics), each a swatch beside the topic's name and shares. Small and medium
  have no legend, so their bands are neutral grey in two alternating strengths.
- **Data**: `WidgetDao.latestAggregate()` (the same row the cross-section opens on) and the
  enabled-source count. The aggregate is rolled up after each fetch, so it prints "as of"
  the latest fetch; reads after that roll in at the next fetch, exactly as on the
  cross-section. An earlier week is labelled "Week of …" at every size, tiny included.
- **Tap**: EDIT › Region & Topics (the cross-section). Empty: "Nothing has flowed from your
  N sources yet." (tap: the Stand).
- The per-topic shares are the widget's own (the cross-section shows over/under ratios) and
  use `HonestPercent`: 0% and 100% only when exact, so a share never contradicts the count.

### Headlines from your sources (`HeadlinesWidgetProvider`)
- **Shows** the newest items from your *enabled* sources, newest first by publication time,
  each with its source and time, then the end mark "End · the newest 3 from your 12 sources".
- **Data**: `WidgetDao.newestFromEnabledSources(5)`: a bounded join, never the whole item
  table. Items whose own feed declared them explicit (`declaredNsfw`) never appear on the
  home screen. Rules in `HeadlinesModel`.
- **Tap**: a headline opens that article in the reader (it records a read, as any open does);
  anywhere else opens the Stand. An article that has aged out of retention opens the Stand.
- The standing region/topic filter is **not** applied: the widget's denominator is all of
  your enabled sources, and it says so.

### Today in History (`HistoryWidgetProvider`)
- **Shows** the column the Stand already loaded from Wikipedia (`TodayInHistoryRepository.
  cachedColumn()`, cache only), with its date, and the CC BY-SA credit wherever an event is
  shown.
- **States**: off ("The Today in History column is off. Tap to turn it on in Settings.",
  tap: EDIT's settings tab, labelled "Reader"); not loaded yet ("Today's column appears here
  once the Stand has loaded it.", tap: the Stand); an earlier day's column, labelled with
  its own date at every size.
- **Tap**: the Stand, where the column lives.

### Source health (`SourceHealthWidgetProvider`)
- **Shows** "10 of your 12 sources: last fetch succeeded, within 48 hours", then counts by
  state ("▲ Last fetch failed: 1 · ◐ No fetch in 48 hours: 1"), then the sources not
  fetching normally in a fixed order (failed, asked to slow down, no fetch in 48 hours, not
  checked yet; then by name), each with its state and last attempt.
- **The words are the rules** (`SourceHealthClassifier`): a source's state comes from its
  last fetch attempt. An error that asked Nooz to slow down (HTTP 429, Retry-After) reads
  "Asked Nooz to slow down"; any other error "Last fetch failed"; no attempt for 48 hours
  "No fetch in 48 hours"; none ever "Not checked yet". The summary row has no shape, because
  it counts every state. "As of" is the redraw time, with its date.
- **Data**: `WidgetDao.sources()` + `SourceHealthClassifier`, enabled sources only.
- **Tap**: Edit › Sources (which manages sources but does not explain the states, which is
  why the widget's words carry them).

### Clippings (`ClippingsWidgetProvider`)
- **Shows** your clippings, most recently clipped first: headline, source, the date clipped,
  and the total count. No excerpts, no topic colour.
- **Data**: `WidgetDao.newestClippings(5)` + `clippingCount()`.
- **Tap**: a clipping opens the article in the reader if it is still stored, otherwise the
  Clippings shelf; anywhere else opens the shelf.

### Coverage and breadth (`CoverageWidgetProvider`)
- **Shows** the cross-section's three numbers for the latest week as a plain record:
  coverage (opened ÷ flowed), breadth for you and for the stream (exp of Shannon entropy),
  the denominator, and at large both formulas. Nothing else: the contrast panel's "widest
  gap" is not here, because it is computed over the Loom's selected days and the
  cross-section does not show it.
- **Rounding**: coverage is rounded exactly as the cross-section rounds it
  (`(coverage × 100).roundToInt()`), so the number tapped and the number explained agree.
- **Data**: the same aggregate as the picture widget (`CoverageRecordModel`).
- **Tap**: EDIT › Region & Topics, where each number is tap-explained.

## Updates: pushed, never polled

`updatePeriodMillis` is 0 on every widget. They redraw when:

1. **A background fetch finishes** (success or not): `FetchWorker` calls the app's
   `afterFetch` hook inside the worker, so the redraw cannot be lost to the process going.
2. **Any table a widget draws from changes**, from anywhere in the app (a read, a clipping,
   a source switched on or off, a pull to refresh): Room's own invalidation signal
   (`RiverData.addChangeListener`), debounced 1.5 s so a fetch redraws once, not per row.
3. **Today in History** is switched on/off, or its column arrives for a new day.
4. **Time boundaries**: one inexact, non-waking alarm, `AlarmManager.setWindow(RTC, at,
   10 min)`, at the next local midnight or the picture's UTC week turn, whichever is first
   (`WidgetClock.nextBoundary`). The 10-minute window bounds how late it comes while the
   phone is awake (a plain inexact alarm can come up to an hour late). No exact-alarm
   permission, and a sleeping phone is not woken. Every "as of" stamp carries its date, so
   it stays true even if this redraw is late or never comes.
5. **Clock, zone, language or font-size changes**: `TIME_SET`, `TIMEZONE_CHANGED`,
   `LOCALE_CHANGED` to each provider; the per-app language (`AppLocale`) on every API
   level; a font-size change through `RiverApplication.onConfigurationChanged`.

A widget renders in the reader's chosen Nooz language (`AppLocale.wrap`), with dates, digits
and percent signs from that locale; feed text is bidi-isolated so an English headline cannot
reorder an Urdu line. Day and night colours follow the phone (`values-night`), mirroring the
Paper and Dark tints.

Nothing a widget does can take the app down: the updater's scope swallows what escapes it
(nothing is logged or sent), a failed delivery falls back to the plain error state, and a
broken settings file stops only the settings watch.

## Taps and what they may do

- Every tap is an explicit, immutable `PendingIntent` to `.widget.WidgetTap`, a
  **non-exported** `<activity-alias>` of `MainActivity`. MainActivity has to be exported (it
  is the launcher entry), so `WidgetLinks.parse` honours a widget request only when it came
  through the alias: another app cannot start the alias, so it cannot open an article and
  record a read in your history. A relaunch from Recents
  (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) is ignored too, so the tap that started a task is
  not acted on twice.
- Providers are exported, as every widget provider must be, and only ever redraw their own
  placed widgets, whatever ids a broadcast names.

## Accessibility

- A clickable container's description replaces its children's text for TalkBack, so each
  widget's description is built from exactly what its current size shows, in order, then
  what a tap does: the title, the aside (a date, a count, "Week of …"), the message or the
  body (the picture's alt text with every number, each stat as "Coverage, 7%", the caption),
  the footer (end mark, credit, "as of", "and N more"). List rows are their own focus
  targets and say themselves. (`WidgetTemplates`, joined by `WidgetSpeech`.)
- Text is in sp with line limits and ellipsis; layouts use start/end, so they mirror in RTL.

## Privacy

A home screen is public. Widgets show only what the app already shows on its own screens,
and nothing is cached for them: every redraw reads Room and DataStore directly, so there is
no new file to leak into backup. Withheld from the home screen: source-declared explicit
items. Shown on purpose (you place the widget to see them): news headlines from your
sources, the headlines of articles you clipped, source names, and aggregate topic shares.
No article text and no excerpts. The Clippings widget is a list of your own saves, item by
item; there is no "counts only" setting for it yet (see STATE.md D60: an owner decision).

## Strings

All copy is in `i18n/strings/en.json` (section `widget_`) and generated into `strings.xml`
by `tools/i18n/generate.py`. The widget keys are translated into the 28 locales that were
complete; Kashmiri, which already shipped partial, falls back to English per key for them,
as it does for the rest of its untranslated keys. The translations were written in build
sessions, not by native speakers, so a native review is welcome, as for the earlier tranches.

## Code map

- `core/model/.../HomeWidgets.kt`: every rule above (sizes and their budget, states,
  ordering, privacy, percentages, the picture's geometry, time boundaries). Tested in
  `HomeWidgetsTest`.
- `core/data/.../db/WidgetDao.kt`, `repo/HomeWidgetRepository.kt`: bounded reads. The SQL is
  tested against a real in-memory Room database in `WidgetDaoTest`.
- `app/.../widget/`: providers, templates, content, the picture's bitmap, taps
  (`WidgetLinks`, tested in `WidgetLinksTest`), spoken descriptions (`WidgetSpeech`, tested
  in `WidgetSpeechTest`), the updater and the boundary alarm. `WidgetLayoutBudgetTest` pins
  the layout XML to the model's height budget.
- `app/src/main/res/layout/widget_*.xml`, `xml/widget_info_*.xml`: plain XML layouts using
  only RemoteViews-allowed classes (LinearLayout, RelativeLayout, FrameLayout, TextView,
  ImageView); a plain `View` would make a launcher refuse to inflate the widget.

## Owner-verify (device only; none of this has been observed)

- Each widget appears in the picker of Pixel Launcher, One UI, Nova and Orrery Home, with
  its preview (previewLayout on Android 12+, the previewImage vector elsewhere).
- Each renders without "Problem loading widget", in day and night, at 2×1, 2×2, 4×2, 4×3 and
  4×4, and switches layouts as it is resized. At each size nothing is cut off at the bottom:
  the end mark, the credit and "as of" stay whole, and the headlines end mark's count matches
  the headlines visible. (The breakpoints are added up from estimated line heights; if a
  launcher's cells or the phone's fonts are taller than estimated, this is where it shows.)
- The same at 1.3× and 2× font scale: widgets fall back to smaller layouts or the one-line
  summary rather than clipping, after Nooz has redrawn them.
- The picture is sharp enough at small, medium and large; the neutral bands are readable on
  both surfaces, and the large legend names every coloured band, including the pale
  catch-all topic.
- TalkBack: focusing each whole widget reads its numbers, the picture's alt text, the end
  mark or credit, the "as of" time and then what a tap does; each row reads itself.
- RTL (Arabic, Urdu): layout mirrors, headlines in English are not scrambled.
- Taps land where described, from a cold start and with Nooz already open, including a
  headline that has aged out (should open the Stand) and Today in History when off
  (should open EDIT's "Reader" tab). Reopening Nooz from Recents after a force-stop does not
  repeat a widget tap.
- A background fetch updates the widgets; reading an article, clipping one and switching a
  source off update them within a couple of seconds.
- The midnight / week-turn alarm fires within about ten minutes while the phone is awake,
  and on OEM battery managers late is acceptable; after a force-stop Android greys the
  widget until Nooz is opened again, which is expected.
- Changing Nooz's language (Settings › Language) re-renders the widgets in it on API 31–32
  and 33+.
- Native-speaker review of the widget strings in the 28 locales.
