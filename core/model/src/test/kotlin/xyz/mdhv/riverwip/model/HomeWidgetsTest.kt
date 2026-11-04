package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * The home-screen widgets' rules: what each one shows, in what order, and
 * which honest state it falls back to. Android only draws what these decide.
 */
class HomeWidgetsTest {

    // Wednesday 2026-11-04 12:00 UTC.
    private val now = ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val thisWeek = WeekBucketing.periodStart(now)
    private val lastWeek = thisWeek - 7L * 86_400_000L

    private fun aggregate(weekStart: Long, stream: Map<Topic, Int>, read: Map<Topic, Int>) = WeeklyAggregate(
        weekStart = weekStart,
        streamCountsByTopic = stream.mapKeys { it.key.key },
        readCountsByTopic = read.mapKeys { it.key.key },
        sourceCounts = emptyMap(),
    )

    private fun item(id: String, sourceId: String, publishedAt: Long, title: String = "Story $id", nsfw: Boolean = false) = Item(
        id = id, sourceId = sourceId, canonicalUrl = "https://ex.com/$id", title = title,
        publishedAt = publishedAt, fetchedAt = publishedAt, declaredNsfw = nsfw,
    )

    private fun source(id: String, title: String, enabled: Boolean = true) =
        Source(id, SourceKind.RSS, "https://ex.com/$id.xml", title, Tier.USER, enabled, 0L)

    private fun health(id: String, status: HealthStatus, lastFetchAt: Long? = now) =
        SourceHealth(id, lastFetchAt, null, 0, status)

    // ---- sizing ----

    /** Every (template, list style) pair a widget is built from. */
    private val shapes: List<Pair<WidgetTemplate, ListStyle>> =
        ListStyle.entries.map { WidgetTemplate.LIST to it } +
            listOf(WidgetTemplate.STAT to ListStyle.ITEMS, WidgetTemplate.IMAGE to ListStyle.ITEMS)

    @Test fun everyTemplateHasFourSizesFromSmallestToLargest() {
        for ((template, style) in shapes) {
            for (scale in listOf(0.85f, 1f, 1.3f, 2f)) {
                val points = WidgetSizing.breakpoints(template, style, scale)
                assertEquals(WidgetSize.entries, points.map { it.size })
                points.zipWithNext().forEach { (a, b) ->
                    assertTrue("$template/$style ${b.size} is not larger than ${a.size} at $scale", b.widthDp * b.heightDp > a.widthDp * a.heightDp)
                }
            }
        }
    }

    // Android's own sizing table (portrait phone): 2x2 is 130x220dp, 4x2
    // 276x220, 4x3 276x337, 4x1 276x102, 2x1 130x102.

    @Test fun commonCellSizesGetTheLayoutDesignedForThem() {
        for ((template, style) in shapes) {
            fun at(w: Float, h: Float) = WidgetSizing.classify(template, w, h, style)
            assertEquals("$template/$style 2x2", WidgetSize.SMALL, at(130f, 220f))
            assertEquals("$template/$style 4x2", WidgetSize.MEDIUM, at(276f, 220f))
            assertEquals("$template/$style 4x3", WidgetSize.LARGE, at(276f, 337f))
            assertEquals("$template/$style 4x1", WidgetSize.TINY, at(276f, 102f))
            assertEquals("$template/$style 2x1", WidgetSize.TINY, at(130f, 102f))
        }
    }

    @Test fun belowTheMinimumIsTheOneLineSummary() {
        assertEquals(WidgetSize.TINY, WidgetSizing.classify(WidgetTemplate.IMAGE, 20f, 20f))
        // The one-line summary itself fits a single landscape row (about 51dp).
        assertTrue(WidgetSizing.requiredHeight(WidgetTemplate.LIST, WidgetSize.TINY) <= 51f)
    }

    @Test fun breakpointsAreTheSumOfTheLayoutsParts() {
        // Worked by hand from the layout XML, so a change to the arithmetic shows up here.
        // List, items, large: padding 24 + title 16 + four rows of (8 + 2x20 + 16) + 2 + two footer lines 32.
        assertEquals(24f + 16f + 4 * 64f + 2f + 32f, WidgetSizing.requiredHeight(WidgetTemplate.LIST, WidgetSize.LARGE), 0.01f)
        // Health, small: one summary row of five title lines and two meta lines.
        assertEquals(24f + 16f + (8f + 100f + 32f) + 2f + 32f, WidgetSizing.requiredHeight(WidgetTemplate.LIST, WidgetSize.SMALL, ListStyle.HEALTH), 0.01f)
        // Short rows still take the 48dp touch target.
        assertEquals(48f, WidgetSizing.rowHeight(RowLines(1, 1)), 0.01f)
        // Stat, small: 4 + value 40 + label 32, caption 4 + 48, footer 4 + 16.
        assertEquals(24f + 16f + 4f + 40f + 32f + 52f + 20f, WidgetSizing.requiredHeight(WidgetTemplate.STAT, WidgetSize.SMALL), 0.01f)
        // Image, large: picture 6 + 56, legend 6 + 32 + 2 + 80, caption 4 + 32, footer 4 + 16.
        assertEquals(
            24f + 16f + 62f + 120f + 36f + 20f,
            WidgetSizing.requiredHeight(WidgetTemplate.IMAGE, WidgetSize.LARGE),
            0.01f,
        )
    }

    @Test fun aLargerFontMovesEveryBreakpointUpSoNothingIsClipped() {
        for ((template, style) in shapes) {
            val normal = WidgetSizing.breakpoints(template, style, 1f)
            val large = WidgetSizing.breakpoints(template, style, 1.3f)
            normal.zip(large).drop(1).forEach { (a, b) -> assertTrue("$template/$style ${a.size}", b.heightDp > a.heightDp) }
        }
        // At 1.3x a 4x2 list no longer gets the medium layout, which only fits at 1x:
        // items and events fall back to one row, health (its row is taller) to the summary line.
        assertEquals(WidgetSize.SMALL, WidgetSizing.classify(WidgetTemplate.LIST, 276f, 220f, ListStyle.ITEMS, 1.3f))
        assertEquals(WidgetSize.SMALL, WidgetSizing.classify(WidgetTemplate.LIST, 276f, 220f, ListStyle.EVENTS, 1.3f))
        assertEquals(WidgetSize.TINY, WidgetSizing.classify(WidgetTemplate.LIST, 276f, 220f, ListStyle.HEALTH, 1.3f))
        // At double size, a 2x2 list falls back to the one-line summary rather than a clipped row.
        assertEquals(WidgetSize.TINY, WidgetSizing.classify(WidgetTemplate.LIST, 130f, 220f, ListStyle.HEALTH, 2f))
    }

    @Test fun listsShowAtMostFiveRows() {
        assertEquals(listOf(0, 1, 3, 4), WidgetSize.entries.map { WidgetSizing.listRows(ListStyle.ITEMS, it) })
        assertEquals(listOf(0, 1, 3, 5), WidgetSize.entries.map { WidgetSizing.listRows(ListStyle.EVENTS, it) })
        assertEquals(listOf(0, 1, 2, 4), WidgetSize.entries.map { WidgetSizing.listRows(ListStyle.HEALTH, it) })
        for (style in ListStyle.entries) {
            assertTrue(WidgetSize.entries.all { WidgetSizing.listRows(style, it) <= WidgetSizing.MAX_ROWS })
        }
    }

    // ---- honest percent ----

    @Test fun percentNeverContradictsTheCounts() {
        assertEquals(0, HonestPercent.of(0, 200))
        assertEquals(1, HonestPercent.of(1, 300))
        assertEquals(99, HonestPercent.of(199, 200))
        assertEquals(100, HonestPercent.of(200, 200))
        assertEquals(50, HonestPercent.of(1, 2))
        assertEquals(0, HonestPercent.of(3, 0))
    }

    // ---- river ----

    @Test fun riverUsesTheLatestWeekAndTheTaxonomyOrder() {
        val river = WeekRiverModel.latest(
            listOf(
                aggregate(lastWeek, mapOf(Topic.SPORT to 5), emptyMap()),
                aggregate(thisWeek, mapOf(Topic.SPORT to 30, Topic.POLITICS to 10), mapOf(Topic.POLITICS to 4)),
            ),
            now,
        )!!
        assertEquals(thisWeek, river.weekStart)
        assertTrue(river.isCurrentWeek)
        assertEquals(40, river.flowed)
        assertEquals(4, river.read)
        // Politics comes before Sport because the taxonomy says so, not because of volume.
        assertEquals(listOf(Topic.POLITICS, Topic.SPORT), river.topics.map { it.topic })
        assertEquals(25, river.topics[0].flowedPercent)
        assertEquals(100, river.topics[0].readPercent)
        assertEquals(0, river.topics[1].readPercent)
    }

    @Test fun anEarlierWeekIsMarkedAsSuch() {
        val river = WeekRiverModel.latest(listOf(aggregate(lastWeek, mapOf(Topic.TECH to 3), emptyMap())), now)!!
        assertFalse(river.isCurrentWeek)
    }

    @Test fun noFlowMeansNoRiver() {
        assertNull(WeekRiverModel.latest(emptyList(), now))
        assertNull(WeekRiverModel.latest(listOf(aggregate(thisWeek, emptyMap(), emptyMap())), now))
    }

    @Test fun pictureBandsKeepOrderAndUnreadTopicsPinchToAPoint() {
        val river = WeekRiverModel.from(
            aggregate(thisWeek, mapOf(Topic.POLITICS to 10, Topic.TECH to 10, Topic.SPORT to 20), mapOf(Topic.TECH to 3, Topic.SPORT to 1)),
            now,
        )!!
        val bands = RiverPicture.layout(river)
        assertEquals(listOf(Topic.POLITICS, Topic.TECH, Topic.SPORT), bands.map { it.topic })
        // Top edge: proportional to flow, in order, inside the picture.
        assertEquals(0f, bands.first().topStart, 1e-6f)
        assertEquals(1f, bands.last().topEnd, 1e-4f)
        bands.zipWithNext().forEach { (a, b) -> assertTrue(b.topStart >= a.topEnd) }
        assertEquals(bands[2].topWidth, bands[0].topWidth * 2, 1e-4f)
        // Bottom edge: politics was never read, so it is a point.
        assertEquals(0f, bands[0].bottomWidth, 0f)
        assertEquals(bands[1].bottomWidth, bands[2].bottomWidth * 3, 1e-4f)
        bands.zipWithNext().forEach { (a, b) -> assertTrue(b.bottomStart >= a.bottomEnd) }
    }

    @Test fun nothingReadLeavesTheBottomEmpty() {
        val river = WeekRiverModel.from(aggregate(thisWeek, mapOf(Topic.CLIMATE to 4), emptyMap()), now)!!
        val band = RiverPicture.layout(river).single()
        assertEquals(1f, band.topWidth, 1e-6f)
        assertEquals(0f, band.bottomWidth, 0f)
    }

    // ---- headlines ----

    @Test fun headlinesAreNewestFirstAndEnd() {
        val items = (1..8).map { item("i$it", "bbc", now - it * 60_000L) }
        val rows = HeadlinesModel.select(items, mapOf("bbc" to "BBC"), limit = 5)
        assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), rows.map { it.itemId })
        assertEquals("BBC", rows.first().sourceTitle)
    }

    @Test fun sourceDeclaredExplicitItemsNeverReachTheHomeScreen() {
        val rows = HeadlinesModel.select(
            listOf(item("a", "s", now, nsfw = true), item("b", "s", now - 1)),
            emptyMap(),
        )
        assertEquals(listOf("b"), rows.map { it.itemId })
    }

    @Test fun headlineTitlesAreTidiedAndBlankOnesSkipped() {
        val rows = HeadlinesModel.select(
            listOf(item("a", "s", now, title = "  Two\n lines  "), item("b", "s", now - 1, title = "   ")),
            emptyMap(),
        )
        assertEquals(listOf("Two lines"), rows.map { it.title })
        assertEquals("", rows.single().sourceTitle)
    }

    @Test fun headlineTiesAreDeterministic() {
        val rows = HeadlinesModel.select(listOf(item("b", "s", now), item("a", "s", now)), emptyMap())
        assertEquals(listOf("a", "b"), rows.map { it.itemId })
    }

    // ---- today in history ----

    private val today = LocalDate.of(2026, 11, 4)
    private val events = (1..9).map { HistoricalEvent(1900 + it * 10, "Event $it") }

    @Test fun historyOffShowsNothingFromWikipedia() {
        assertEquals(HistoryWidgetState.Off, HistoryWidgetModel.state(false, today, events, today))
    }

    @Test fun historyNotLoadedYet() {
        assertEquals(HistoryWidgetState.NotLoaded, HistoryWidgetModel.state(true, null, emptyList(), today))
        assertEquals(HistoryWidgetState.NotLoaded, HistoryWidgetModel.state(true, today, emptyList(), today))
    }

    @Test fun historyTodayUsesTheAppsOwnColumn() {
        val state = HistoryWidgetModel.state(true, today, events, today) as HistoryWidgetState.Column
        assertTrue(state.isToday)
        assertEquals(TodayInHistory.column(events), state.events)
    }

    @Test fun anEarlierDaysColumnIsNeverPassedOffAsToday() {
        val yesterday = today.minusDays(1)
        val state = HistoryWidgetModel.state(true, yesterday, events, today) as HistoryWidgetState.Column
        assertFalse(state.isToday)
        assertEquals(yesterday, state.date)
    }

    // ---- source health ----

    @Test fun healthCountsOnlyEnabledSources() {
        val summary = SourceHealthSummary.summarize(
            listOf(source("a", "Alpha"), source("b", "Beta"), source("c", "Gamma", enabled = false)),
            listOf(health("a", HealthStatus.OK), health("b", HealthStatus.FAILING), health("c", HealthStatus.FAILING)),
        )
        assertEquals(2, summary.enabled)
        assertEquals(1, summary.fetchingNormally)
        assertEquals(1, summary.count(HealthStatus.FAILING))
        assertEquals(listOf("b"), summary.notNormal.map { it.sourceId })
    }

    @Test fun healthListsByWhatTheReaderCanDoThenByName() {
        val summary = SourceHealthSummary.summarize(
            listOf(source("1", "zed"), source("2", "Able"), source("3", "Mid"), source("4", "New"), source("5", "Ok")),
            listOf(
                health("1", HealthStatus.STALE),
                health("2", HealthStatus.STALE),
                health("3", HealthStatus.RATE_LIMITED),
                health("5", HealthStatus.OK),
            ),
        )
        // "New" has no health row at all, so it has never been checked.
        assertEquals(listOf("Mid", "Able", "zed", "New"), summary.notNormal.map { it.title })
        assertEquals(HealthStatus.UNKNOWN, summary.notNormal.last().status)
    }

    @Test fun healthLastFetchIsTheMostRecentAttempt() {
        val summary = SourceHealthSummary.summarize(
            listOf(source("a", "A"), source("b", "B")),
            listOf(health("a", HealthStatus.OK, now - 5_000L), health("b", HealthStatus.OK, now - 1_000L)),
        )
        assertEquals(now - 1_000L, summary.lastFetchAt)
        assertNull(SourceHealthSummary.summarize(emptyList(), emptyList()).lastFetchAt)
    }

    // ---- coverage record ----

    @Test fun coverageRecordMatchesTheCrossSectionsFormulas() {
        val agg = aggregate(thisWeek, mapOf(Topic.POLITICS to 50, Topic.SPORT to 50), mapOf(Topic.POLITICS to 9, Topic.SPORT to 1))
        val record = CoverageRecordModel.from(agg, now)!!
        assertEquals(100, record.flowed)
        assertEquals(10, record.read)
        assertEquals(10, record.coveragePercent)
        assertEquals(RiverAnalysis.breadth(agg.streamByTopic()), record.breadthStream, 1e-9)
        assertEquals(RiverAnalysis.breadth(agg.readByTopic()), record.breadthYou, 1e-9)
    }

    @Test fun coverageIsRoundedExactlyAsTheCrossSectionRoundsIt() {
        // CrossSectionPanel prints (coverage * 100).roundToInt(); the widget's tap
        // lands there, so the two must agree even at the edges.
        for ((read, flowed) in listOf(199 to 200, 1 to 300, 0 to 7, 7 to 7, 9 to 7)) {
            val agg = aggregate(thisWeek, mapOf(Topic.TECH to flowed), mapOf(Topic.TECH to read))
            val expected = (RiverAnalysis.coverage(agg.streamByTopic(), agg.readByTopic()) * 100).roundToInt()
            assertEquals("$read of $flowed", expected, CoverageRecordModel.from(agg, now)!!.coveragePercent)
        }
    }

    @Test fun coverageNeedsSomethingToHaveFlowed() {
        assertNull(CoverageRecordModel.from(null, now))
        assertNull(CoverageRecordModel.from(aggregate(thisWeek, emptyMap(), emptyMap()), now))
        val record = CoverageRecordModel.from(aggregate(thisWeek, mapOf(Topic.TECH to 7), emptyMap()), now)
        assertNotNull(record)
        assertEquals(0, record!!.coveragePercent)
    }

    // ---- clippings ----

    @Test fun clippingsAreMostRecentlyClippedFirst() {
        fun clip(id: String, savedAt: Long) = Clipping(id, " Title $id ", "Src", null, "https://ex.com/$id", "tech", 0L, savedAt, null)
        val rows = ClippingsModel.rows(listOf(clip("old", 1L), clip("new", 3L), clip("mid", 2L)), limit = 2)
        assertEquals(listOf("new", "mid"), rows.map { it.itemId })
        assertEquals("Title new", rows.first().title)
        assertEquals(Topic.TECH, rows.first().topic)
    }

    // ---- time boundaries ----

    @Test fun nextBoundaryIsLocalMidnight() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val at = ZonedDateTime.of(2026, 11, 4, 21, 0, 0, 0, kolkata).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 5, 0, 0, 0, 0, kolkata).toInstant().toEpochMilli()
        assertEquals(expected, WidgetClock.nextBoundary(at, kolkata))
    }

    @Test fun nextBoundaryIsTheWeekTurningWhenThatComesFirst() {
        // The river's week turns at Monday 00:00 UTC, which is Sunday 19:00 in
        // New York: five hours before New York's own midnight.
        val ny = ZoneId.of("America/New_York")
        val sunday1800 = ZonedDateTime.of(2026, 11, 8, 18, 0, 0, 0, ny).toInstant().toEpochMilli()
        val mondayUtc = ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(mondayUtc, WidgetClock.nextBoundary(sunday1800, ny))
    }

    @Test fun boundaryIsAlwaysInTheFuture() {
        val zone = ZoneId.of("Europe/Berlin")
        var t = now
        repeat(50) {
            val next = WidgetClock.nextBoundary(t, zone)
            assertTrue(next > t)
            t = next
        }
    }
}
