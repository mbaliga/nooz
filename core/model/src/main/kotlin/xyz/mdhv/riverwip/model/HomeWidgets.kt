package xyz.mdhv.riverwip.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * What Nooz's home-screen widgets show, decided here rather than in the app
 * module so every rule is unit-tested on a bare JVM (D2). The app module only
 * loads rows, calls these, and turns the result into RemoteViews and words.
 *
 * The rules the widgets inherit from the app are the ones that matter most on
 * a home screen, which is a public surface that the reader glances at without
 * deciding to: nothing is ordered by engagement, every total names its
 * denominator (the reader's own sources), every list ends, and nothing counts
 * what the reader has not done yet.
 */

/** The three layout patterns the widgets are built from (D-AD's `list`, `stat` and `image`). */
enum class WidgetTemplate { LIST, STAT, IMAGE }

/**
 * Size classes. [TINY] is anything below the template's minimum: the widget
 * collapses to one tappable summary line rather than squeezing its controls.
 */
enum class WidgetSize { TINY, SMALL, MEDIUM, LARGE }

/** How many lines one list row may use: its title, and the meta line(s) under it. */
data class RowLines(val title: Int, val meta: Int)

/**
 * What a list widget's rows hold. Rows differ in height between the list
 * widgets, so each style has its own row shapes and its own breakpoints.
 */
enum class ListStyle {
    /** A headline or a clipping: the title, and one line naming the source and a date. */
    ITEMS,

    /** Today in History: a year beside the event's text, with no meta line. */
    EVENTS,

    /** Source health: a summary of every source, then the sources not fetching normally. */
    HEALTH,
}

/**
 * Which layout a widget gets at which size, and how much each layout holds.
 *
 * The launcher picks a layout from a size map without asking the app, so each
 * size class's breakpoint has to be the height its layout really needs: if
 * it were smaller, the launcher would pick a layout that cannot fit and clip
 * its bottom, which is where the end-of-list mark and the "as of" time sit.
 * So the breakpoints are not chosen numbers; they are added up here from the
 * parts each layout is built from (padding, a 12sp line, a 14sp serif line,
 * a 48dp row), every text line at its most, at the reader's font scale.
 * WidgetLayoutBudgetTest in `:app` pins the layout XML to these parts, so the
 * two cannot drift apart.
 */
object WidgetSizing {

    /** The smallest size, in dp, a size class is laid out for. */
    data class Breakpoint(val size: WidgetSize, val widthDp: Float, val heightDp: Float)

    /** Below every other breakpoint: the one-line summary is drawn at any size. */
    const val TINY_DP = 40f

    /** A narrow widget (two cells on most phones) and a wide one (four cells). */
    const val NARROW_DP = 110f
    const val WIDE_DP = 250f

    // ---- the parts the layouts are built from, in dp at 1x font scale ----

    /** widget_padding, above and below. */
    const val PADDING = 24f

    /** The one-line summary's padding, above and below. */
    const val TINY_PADDING = 12f

    /** One line of 12sp text, font padding included: the title, meta lines, labels, captions, footers. */
    const val LINE_SMALL = 16f

    /** One line of 14sp serif text: row titles, messages, the one-line summary. */
    const val LINE_BODY = 20f

    /** The small stat layout's 28sp value, and the 24sp values of the others. */
    const val LINE_VALUE_SMALL = 40f
    const val LINE_VALUE = 34f

    /** A row's text sits 4dp from its top and bottom edges. */
    const val ROW_PADDING = 8f

    /** Every tappable row is at least a 48dp touch target. */
    const val ROW_MIN = 48f

    /** Space above a caption or a footer, and above a stat's values. */
    const val GAP = 4f

    /** Space above a list's end-of-list line. */
    const val LIST_FOOTER_GAP = 2f

    /** Space above the picture, and above the legend's heading. */
    const val PICTURE_GAP = 6f

    /** Space between the legend's heading and its entries. */
    const val LEGEND_GAP = 2f

    /** The least height the picture is drawn at, per size. The labelled layouts also need two label lines beside it. */
    const val PICTURE_SMALL = 40f
    const val PICTURE_MEDIUM = 48f
    const val PICTURE_LARGE = 56f

    /** The legend's heading lines, and its entry lines: two topics a line, so ten topics take five. */
    const val LEGEND_HEAD_LINES = 2
    const val LEGEND_LINES = 5
    const val LEGEND_SLOTS = 10

    /** A list never shows more than five rows: a widget is a glance, not a feed. */
    const val MAX_ROWS = 5

    /**
     * The rows a list shows at each size, and the lines each may use. The
     * health summary row takes two meta lines so its counts are never cut;
     * a health source row takes two so its state and its last attempt both show.
     */
    fun rows(style: ListStyle, size: WidgetSize): List<RowLines> = when (size) {
        WidgetSize.TINY -> emptyList()
        WidgetSize.SMALL -> when (style) {
            ListStyle.ITEMS -> listOf(RowLines(3, 1))
            ListStyle.EVENTS -> listOf(RowLines(4, 0))
            // The summary names its own rule ("…within 48 hours"), so it gets room not to cut it.
            ListStyle.HEALTH -> listOf(RowLines(5, 2))
        }
        WidgetSize.MEDIUM -> when (style) {
            ListStyle.ITEMS -> List(3) { RowLines(1, 1) }
            ListStyle.EVENTS -> List(3) { RowLines(2, 0) }
            ListStyle.HEALTH -> listOf(RowLines(2, 2), RowLines(1, 2))
        }
        WidgetSize.LARGE -> when (style) {
            ListStyle.ITEMS -> List(4) { RowLines(2, 1) }
            ListStyle.EVENTS -> List(5) { RowLines(2, 0) }
            ListStyle.HEALTH -> listOf(RowLines(2, 2)) + List(3) { RowLines(1, 2) }
        }
    }

    /** How many rows a list of [style] shows at [size]. */
    fun listRows(style: ListStyle, size: WidgetSize): Int = rows(style, size).size

    /** Caption lines: the denominator, and in the large stat layout the formulas. Lists have none. */
    fun captionLines(template: WidgetTemplate, size: WidgetSize): Int = when (template) {
        WidgetTemplate.LIST -> 0
        WidgetTemplate.STAT -> when (size) {
            WidgetSize.TINY -> 0
            WidgetSize.SMALL -> 3
            WidgetSize.MEDIUM -> 2
            WidgetSize.LARGE -> 8
        }
        WidgetTemplate.IMAGE -> when (size) {
            WidgetSize.TINY -> 0
            WidgetSize.SMALL -> 3
            WidgetSize.MEDIUM, WidgetSize.LARGE -> 2
        }
    }

    /** Footer lines: a list's end-of-list mark may wrap; the others hold only an "as of" time. */
    fun footerLines(template: WidgetTemplate): Int = if (template == WidgetTemplate.LIST) 2 else 1

    /** One row's height: its text plus padding, never under the 48dp touch target. */
    fun rowHeight(row: RowLines, fontScale: Float = 1f): Float =
        maxOf(ROW_MIN, ROW_PADDING + (row.title * LINE_BODY + row.meta * LINE_SMALL) * fontScale)

    /** The least height of the picture at [size]; the labelled layouts also fit two label lines beside it. */
    fun pictureHeight(size: WidgetSize, fontScale: Float = 1f): Float = when (size) {
        WidgetSize.TINY -> 0f
        WidgetSize.SMALL -> PICTURE_SMALL
        WidgetSize.MEDIUM -> maxOf(PICTURE_MEDIUM, 2 * LINE_SMALL * fontScale)
        WidgetSize.LARGE -> maxOf(PICTURE_LARGE, 2 * LINE_SMALL * fontScale)
    }

    /**
     * The height, in dp, a layout needs with every text line at its most, at
     * [fontScale]. This is the breakpoint: at this height nothing is clipped.
     */
    fun requiredHeight(
        template: WidgetTemplate,
        size: WidgetSize,
        style: ListStyle = ListStyle.ITEMS,
        fontScale: Float = 1f,
    ): Float {
        fun text(lines: Int, lineDp: Float) = lines * lineDp * fontScale
        if (size == WidgetSize.TINY) return TINY_PADDING + text(1, LINE_SMALL) + text(1, LINE_BODY)
        val header = text(1, LINE_SMALL)
        val caption = captionLines(template, size).let { if (it == 0) 0f else GAP + text(it, LINE_SMALL) }
        val body = when (template) {
            WidgetTemplate.LIST ->
                rows(style, size).sumOf { rowHeight(it, fontScale).toDouble() }.toFloat() +
                    LIST_FOOTER_GAP + text(footerLines(template), LINE_SMALL)
            WidgetTemplate.STAT ->
                GAP + text(1, if (size == WidgetSize.SMALL) LINE_VALUE_SMALL else LINE_VALUE) + text(2, LINE_SMALL) +
                    caption + GAP + text(footerLines(template), LINE_SMALL)
            WidgetTemplate.IMAGE -> {
                val legend = if (size == WidgetSize.LARGE) {
                    PICTURE_GAP + text(LEGEND_HEAD_LINES, LINE_SMALL) + LEGEND_GAP + text(LEGEND_LINES, LINE_SMALL)
                } else {
                    0f
                }
                PICTURE_GAP + pictureHeight(size, fontScale) + legend + caption + GAP + text(footerLines(template), LINE_SMALL)
            }
        }
        return PADDING + header + body
    }

    /**
     * One breakpoint per size class, the keys of the size-mapped RemoteViews
     * the launcher chooses from: each is the width its layout is drawn for and
     * the [requiredHeight] at the reader's [fontScale]. A larger font moves
     * every breakpoint up, so a widget falls back to a smaller layout, and in
     * the end to the one-line summary, rather than clipping.
     */
    fun breakpoints(template: WidgetTemplate, style: ListStyle = ListStyle.ITEMS, fontScale: Float = 1f): List<Breakpoint> =
        listOf(
            Breakpoint(WidgetSize.TINY, TINY_DP, TINY_DP),
            Breakpoint(WidgetSize.SMALL, NARROW_DP, requiredHeight(template, WidgetSize.SMALL, style, fontScale)),
            Breakpoint(WidgetSize.MEDIUM, WIDE_DP, requiredHeight(template, WidgetSize.MEDIUM, style, fontScale)),
            Breakpoint(WidgetSize.LARGE, WIDE_DP, requiredHeight(template, WidgetSize.LARGE, style, fontScale)),
        )

    /**
     * The size class a widget of [widthDp] by [heightDp] is drawn at: the
     * largest breakpoint that fits in both directions, or [WidgetSize.TINY]
     * when none does. Android makes this choice itself from the size-mapped
     * RemoteViews; this states the same rule so the tests (and the table in
     * docs/widgets.md) can say which layout a given widget size gets.
     */
    fun classify(
        template: WidgetTemplate,
        widthDp: Float,
        heightDp: Float,
        style: ListStyle = ListStyle.ITEMS,
        fontScale: Float = 1f,
    ): WidgetSize =
        breakpoints(template, style, fontScale)
            .filter { it.widthDp <= widthDp && it.heightDp <= heightDp }
            .maxByOrNull { it.widthDp * it.heightDp }
            ?.size
            ?: WidgetSize.TINY
}

/**
 * A whole-number percentage that never contradicts the counts printed next to
 * it: 0% only when nothing, 100% only when everything, so 199 of 200 reads 99%
 * and 1 of 300 reads 1%. The same lesson the river's accessibility pass
 * recorded (STATE.md, "a rounded percentage can contradict the exact count").
 */
object HonestPercent {
    fun of(part: Int, whole: Int): Int {
        if (whole <= 0 || part <= 0) return 0
        if (part >= whole) return 100
        return (part * 100.0 / whole).roundToInt().coerceIn(1, 99)
    }
}

/** One topic's share of a week: what flowed from the reader's sources against what they read. */
data class TopicFlow(
    val topic: Topic,
    val flowed: Int,
    val read: Int,
    /** This topic's share of everything that flowed, as an honest percent. */
    val flowedPercent: Int,
    /** This topic's share of everything read, as an honest percent. */
    val readPercent: Int,
)

/**
 * The week the picture widget draws. Built from the persisted weekly
 * aggregate, the same row the cross-section opens on, so its totals are the
 * cross-section's. The per-topic shares are this widget's own (the
 * cross-section shows over/under ratios instead) and use [HonestPercent].
 */
data class WeekRiver(
    val weekStart: Long,
    val flowed: Int,
    val read: Int,
    /** Every topic that flowed or was read, in the taxonomy's own fixed order. Never ranked. */
    val topics: List<TopicFlow>,
    /** False when the latest aggregate is an earlier week (nothing has flowed yet this week). */
    val isCurrentWeek: Boolean,
)

object WeekRiverModel {

    /** The latest week with an aggregate, or null when no aggregate has anything in it. */
    fun latest(aggregates: List<WeeklyAggregate>, now: Long): WeekRiver? =
        aggregates.maxByOrNull { it.weekStart }?.let { from(it, now) }

    fun from(aggregate: WeeklyAggregate, now: Long): WeekRiver? {
        val stream = aggregate.streamByTopic()
        val read = aggregate.readByTopic()
        val totalFlowed = stream.values.sum()
        val totalRead = read.values.sum()
        if (totalFlowed == 0 && totalRead == 0) return null
        val topics = Topic.entries.mapNotNull { topic ->
            val f = stream[topic] ?: 0
            val r = read[topic] ?: 0
            if (f == 0 && r == 0) null
            else TopicFlow(topic, f, r, HonestPercent.of(f, totalFlowed), HonestPercent.of(r, totalRead))
        }
        return WeekRiver(
            weekStart = aggregate.weekStart,
            flowed = totalFlowed,
            read = totalRead,
            topics = topics,
            isCurrentWeek = aggregate.weekStart == WeekBucketing.periodStart(now),
        )
    }
}

/**
 * The picture the river widget draws: the week's supply along the top edge,
 * the reader's intake along the bottom, one band per topic joining its share of
 * each. A topic nobody read pinches to a point at the bottom, which is the
 * whole argument of the app in one shape.
 *
 * Everything is a fraction of the picture's width (0..1), so it is independent
 * of pixels and testable here. Topics keep the taxonomy's order along both
 * edges, so bands never cross and the same topic is always in the same place.
 */
object RiverPicture {

    data class Band(
        val topic: Topic,
        val topStart: Float,
        val topEnd: Float,
        val bottomStart: Float,
        val bottomEnd: Float,
    ) {
        val topWidth: Float get() = topEnd - topStart
        val bottomWidth: Float get() = bottomEnd - bottomStart
    }

    /** Gap between neighbouring segments on one edge, as a fraction of the width. */
    const val GAP = 0.012f

    fun layout(river: WeekRiver, gap: Float = GAP): List<Band> {
        val topics = river.topics
        val tops = edge(topics.map { it.flowed }, gap)
        val bottoms = edge(topics.map { it.read }, gap)
        return topics.indices.map { i ->
            Band(topics[i].topic, tops[i].first, tops[i].second, bottoms[i].first, bottoms[i].second)
        }
    }

    /**
     * Segments along one edge, proportional to [counts], separated by [gap]
     * where both neighbours have width. A zero count is a point at the cursor,
     * so its band still has somewhere to land.
     */
    private fun edge(counts: List<Int>, gap: Float): List<Pair<Float, Float>> {
        val total = counts.sum()
        if (total == 0) return counts.map { 0f to 0f }
        val nonZero = counts.count { it > 0 }
        val usable = (1f - gap * (nonZero - 1)).coerceAtLeast(0f)
        var cursor = 0f
        var placed = 0
        return counts.map { c ->
            if (c == 0) {
                cursor to cursor
            } else {
                if (placed > 0) cursor += gap
                val start = cursor
                cursor += usable * c / total
                placed++
                start to cursor.coerceAtMost(1f)
            }
        }
    }
}

/** One headline row: title, the source it came from, and when it was published. */
data class HeadlineRow(
    val itemId: String,
    val title: String,
    val sourceTitle: String,
    val publishedAt: Long,
)

object HeadlinesModel {

    private val WHITESPACE = Regex("""\s+""")

    /**
     * The newest items from the reader's sources, newest first by publication
     * time and nothing else (README: no engagement ranking). Items whose own
     * feed declared them explicit are withheld: a home screen is seen by
     * whoever picks the phone up. Blank titles are skipped rather than drawn
     * as empty rows.
     */
    fun select(items: List<Item>, sourceTitles: Map<String, String>, limit: Int = WidgetSizing.MAX_ROWS): List<HeadlineRow> =
        items.asSequence()
            .filterNot { it.declaredNsfw }
            .map { it to it.title.replace(WHITESPACE, " ").trim() }
            .filter { (_, title) -> title.isNotEmpty() }
            .sortedWith(compareByDescending<Pair<Item, String>> { it.first.publishedAt }.thenBy { it.first.id })
            .take(limit.coerceAtLeast(0))
            .map { (item, title) ->
                HeadlineRow(item.id, title, sourceTitles[item.sourceId] ?: "", item.publishedAt)
            }
            .toList()
}

/** What the Today in History widget can honestly show. */
sealed interface HistoryWidgetState {
    /** The reader has the column switched off, so the widget shows nothing from Wikipedia at all. */
    data object Off : HistoryWidgetState

    /** On, but Nooz has not loaded a column yet (it loads when the Stand shows the column). */
    data object NotLoaded : HistoryWidgetState

    /** A column, with the date it is for. [isToday] false means it is an earlier day's, and says so. */
    data class Column(val date: LocalDate, val events: List<HistoricalEvent>, val isToday: Boolean) : HistoryWidgetState
}

object HistoryWidgetModel {
    /**
     * The widget never fetches. It shows the column the app already cached
     * when the reader opened the Stand with the column on, dated, so an
     * earlier day's column is never passed off as today's.
     */
    fun state(enabled: Boolean, cachedDate: LocalDate?, cachedEvents: List<HistoricalEvent>, today: LocalDate): HistoryWidgetState {
        if (!enabled) return HistoryWidgetState.Off
        if (cachedDate == null || cachedEvents.isEmpty()) return HistoryWidgetState.NotLoaded
        return HistoryWidgetState.Column(cachedDate, TodayInHistory.column(cachedEvents), cachedDate == today)
    }
}

/** One enabled source as the health widget lists it. */
data class HealthRow(
    val sourceId: String,
    val title: String,
    val status: HealthStatus,
    val lastFetchAt: Long?,
)

data class HealthSummary(
    /** The denominator: the reader's enabled sources. */
    val enabled: Int,
    val counts: Map<HealthStatus, Int>,
    /** Sources not fetching normally, in [SourceHealthSummary.ATTENTION_ORDER] then by title. */
    val notNormal: List<HealthRow>,
    /** The most recent fetch attempt across enabled sources, or null if none has run. */
    val lastFetchAt: Long?,
) {
    val fetchingNormally: Int get() = counts[HealthStatus.OK] ?: 0
    fun count(status: HealthStatus): Int = counts[status] ?: 0
}

object SourceHealthSummary {

    /**
     * A fixed order by what the reader can do about it, not a ranking of
     * sources: failing first, then rate-limited, stale, and not yet checked.
     */
    val ATTENTION_ORDER: List<HealthStatus> =
        listOf(HealthStatus.FAILING, HealthStatus.RATE_LIMITED, HealthStatus.STALE, HealthStatus.UNKNOWN)

    /** Enabled sources only: a source the reader switched off is not part of their news. */
    fun summarize(sources: List<Source>, health: List<SourceHealth>): HealthSummary {
        val byId = health.associateBy { it.sourceId }
        val rows = sources.filter { it.enabled }.map { s ->
            val h = byId[s.id]
            HealthRow(s.id, s.title, h?.status ?: HealthStatus.UNKNOWN, h?.lastFetchAt)
        }
        val counts = rows.groupingBy { it.status }.eachCount()
        val notNormal = rows.filter { it.status != HealthStatus.OK }
            .sortedWith(compareBy<HealthRow> { ATTENTION_ORDER.indexOf(it.status) }.thenBy { it.title.lowercase() })
        return HealthSummary(rows.size, counts, notNormal, rows.mapNotNull { it.lastFetchAt }.maxOrNull())
    }
}

/**
 * The record the coverage widget prints: the three numbers the cross-section
 * (Edit › Region & Topics) shows for the same week, each with its formula
 * there: coverage, and breadth for the stream and for the reader. Nothing
 * else, so every number on the widget is one its tap explains. A plain
 * record, not a goal: nothing here is drawn as progress toward anything.
 */
data class CoverageRecord(
    val weekStart: Long,
    val flowed: Int,
    val read: Int,
    /** Rounded exactly as the cross-section rounds it, so both say the same number. */
    val coveragePercent: Int,
    val breadthStream: Double,
    val breadthYou: Double,
    val isCurrentWeek: Boolean,
)

object CoverageRecordModel {

    fun from(aggregate: WeeklyAggregate?, now: Long): CoverageRecord? {
        aggregate ?: return null
        val stream = aggregate.streamByTopic()
        val read = aggregate.readByTopic()
        val flowed = stream.values.sum()
        val opened = read.values.sum()
        if (flowed == 0) return null
        return CoverageRecord(
            weekStart = aggregate.weekStart,
            flowed = flowed,
            read = opened,
            coveragePercent = crossSectionPercent(RiverAnalysis.coverage(stream, read)),
            breadthStream = RiverAnalysis.breadth(stream),
            breadthYou = RiverAnalysis.breadth(read),
            isCurrentWeek = aggregate.weekStart == WeekBucketing.periodStart(now),
        )
    }

    /** CrossSectionPanel's rounding, `(coverage * 100).roundToInt()`, kept identical on purpose. */
    fun crossSectionPercent(coverage: Double): Int = (coverage * 100).roundToInt()
}

/** One clipping as the clippings widget lists it. */
data class ClippingRow(
    val itemId: String,
    val title: String,
    val sourceTitle: String?,
    val topic: Topic,
    val savedAt: Long,
)

object ClippingsModel {
    /** Most recently clipped first: the shelf's own order, never anything else. */
    fun rows(clippings: List<Clipping>, limit: Int = WidgetSizing.MAX_ROWS): List<ClippingRow> =
        clippings.sortedWith(compareByDescending<Clipping> { it.savedAt }.thenBy { it.itemId })
            .take(limit.coerceAtLeast(0))
            .map { ClippingRow(it.itemId, it.title.trim(), it.sourceTitle, it.topic, it.savedAt) }
}

object WidgetClock {

    /**
     * The next moment a widget's content changes with time alone, with no new
     * data: the next local midnight (Today in History's date turns over) or
     * the next UTC Monday (the river's week, which [WeekBucketing] counts in
     * UTC), whichever comes first. One inexact alarm at this instant keeps the
     * dated widgets honest without polling.
     */
    fun nextBoundary(now: Long, zone: ZoneId): Long {
        val instant = Instant.ofEpochMilli(now)
        val midnight = instant.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = WeekBucketing.periodRange(now).last + 1
        return minOf(midnight, weekEnd)
    }

    /** The calendar date [now] falls on in [zone]. */
    fun localDate(now: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    /** True when [then] is the same calendar day as [now] in [zone]. */
    fun sameDay(then: Long, now: Long, zone: ZoneId): Boolean = localDate(then, zone) == localDate(now, zone)
}
