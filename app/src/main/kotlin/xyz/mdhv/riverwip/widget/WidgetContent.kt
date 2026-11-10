package xyz.mdhv.riverwip.widget

import android.content.Context
import android.icu.text.NumberFormat
import android.icu.text.SimpleDateFormat
import android.text.BidiFormatter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import kotlinx.coroutines.flow.first
import xyz.mdhv.riverwip.AppContainer
import xyz.mdhv.riverwip.data.repo.TodayInHistoryRepository
import xyz.mdhv.riverwip.design.R as DesignR
import xyz.mdhv.riverwip.model.ClippingsModel
import xyz.mdhv.riverwip.model.CoverageRecordModel
import xyz.mdhv.riverwip.model.HeadlinesModel
import xyz.mdhv.riverwip.model.HealthStatus
import xyz.mdhv.riverwip.model.HistoryWidgetModel
import xyz.mdhv.riverwip.model.HistoryWidgetState
import xyz.mdhv.riverwip.model.ListStyle
import xyz.mdhv.riverwip.model.SourceHealthSummary
import xyz.mdhv.riverwip.model.TopicPalette
import xyz.mdhv.riverwip.model.WeekRiverModel
import xyz.mdhv.riverwip.model.WidgetClock
import xyz.mdhv.riverwip.model.WidgetSize
import xyz.mdhv.riverwip.model.WidgetSizing
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * What each of Nooz's widgets says, in the reader's chosen language, built
 * from the app's own stored data and the rules in `:core:model`
 * (HomeWidgets.kt). No network: every value here was already on the device.
 */
internal object WidgetContent {

    /** How many rows a list widget ever asks the database for. */
    private const val ROWS = WidgetSizing.MAX_ROWS

    suspend fun render(context: Context, container: AppContainer, kind: NoozWidget, appWidgetId: Int, now: Long): RemoteViews {
        val words = Words(context, now)
        return when (kind) {
            NoozWidget.RIVER -> river(context, container, words, appWidgetId)
            NoozWidget.HEADLINES -> headlines(context, container, words, appWidgetId)
            NoozWidget.HISTORY -> history(context, container, words, appWidgetId)
            NoozWidget.HEALTH -> health(context, container, words, appWidgetId)
            NoozWidget.CLIPPINGS -> clippings(context, container, words, appWidgetId)
            NoozWidget.COVERAGE -> coverage(context, container, words, appWidgetId)
        }
    }

    /** The error state, in words, for when reading the data itself failed. */
    fun error(context: Context, kind: NoozWidget, appWidgetId: Int): RemoteViews {
        val words = Words(context, System.currentTimeMillis())
        val title = words.s(kind.title)
        val message = words.s(DesignR.string.widget_error)
        val frame = Frame(
            title = title,
            open = WidgetLinks.open(context, appWidgetId, 0, WidgetDestination.STAND),
            action = words.s(DesignR.string.widget_open_stand),
            summary = message,
            message = message,
        )
        return when (kind.template) {
            NoozWidget.Template.LIST -> WidgetTemplates.list(context, ListSpec(frame, emptyList()))
            NoozWidget.Template.STAT -> WidgetTemplates.stat(context, StatSpec(frame, emptyList()))
            NoozWidget.Template.IMAGE -> WidgetTemplates.image(context, ImageSpec(frame, null, message))
        }
    }

    private fun noSources(context: Context, words: Words, kind: NoozWidget, appWidgetId: Int): Frame {
        val title = words.s(kind.title)
        val message = words.s(DesignR.string.widget_no_sources)
        return Frame(
            title = title,
            open = WidgetLinks.open(context, appWidgetId, 0, WidgetDestination.SOURCES),
            action = words.s(DesignR.string.list_open_sources),
            summary = message,
            message = message,
        )
    }

    /** What a tap on the river or the coverage widget does: opens Edit › Region & Topics, named as the app names it. */
    private fun openRegionTopics(words: Words): String =
        words.s(DesignR.string.widget_open_cross_section, words.s(DesignR.string.edit_edit) + " › " + REGION_TOPICS_TAB)

    /** Edit's tab label, which the app itself shows in English (EditScreen's TabLabel), so the spoken name matches it. */
    private const val REGION_TOPICS_TAB = "Region & Topics"

    // ---- Flowed and read, this week (image; RiverWidgetProvider) ----

    private suspend fun river(context: Context, container: AppContainer, words: Words, id: Int): RemoteViews {
        val repo = container.homeWidgetRepository
        val pairs = repo.sourcesWithHealth()
        val health = SourceHealthSummary.summarize(pairs.map { it.first }, pairs.map { it.second })
        if (health.enabled == 0) {
            return WidgetTemplates.image(context, ImageSpec(noSources(context, words, NoozWidget.RIVER, id), null, ""))
        }
        val sources = words.sources(health.enabled)
        val title = words.s(NoozWidget.RIVER.title)
        val river = repo.latestAggregate()?.let { WeekRiverModel.from(it, words.now) }
        if (river == null) {
            val message = words.s(DesignR.string.widget_river_empty, sources)
            val frame = Frame(
                title = title,
                open = WidgetLinks.open(context, id, 0, WidgetDestination.STAND),
                action = words.s(DesignR.string.widget_open_stand),
                summary = message,
                message = message,
            )
            return WidgetTemplates.image(context, ImageSpec(frame, null, message))
        }

        val week = words.day(river.weekStart)
        val weekOf = if (river.isCurrentWeek) null else words.s(DesignR.string.widget_week_of, week)
        val totals = words.s(DesignR.string.loom_totals, river.flowed, river.read, sources)
        val alt = words.s(
            DesignR.string.widget_river_alt,
            week,
            sources,
            river.flowed,
            river.read,
            river.topics.joinToString(", ") {
                words.s(DesignR.string.widget_river_alt_topic, it.topic.placeholderLabel, it.flowedPercent, it.readPercent)
            },
        )
        // One entry per band, in the picture's own order: the swatch is never
        // shown without the topic's name beside it.
        val legend = river.topics.map { flow ->
            SpannableStringBuilder().apply {
                append("■")
                setSpan(ForegroundColorSpan(TopicPalette.colorFor(flow.topic)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(' ').append(flow.topic.placeholderLabel).append(' ')
                append(words.s(DesignR.string.contrast_share_pair, flow.flowedPercent, flow.readPercent))
            }
        }
        val frame = Frame(
            title = title,
            // An earlier week is labelled as one; this week needs no label beyond the title.
            aside = weekOf,
            open = WidgetLinks.open(context, id, 0, WidgetDestination.CROSS_SECTION),
            action = openRegionTopics(words),
            // The one-line summary keeps the week's label too, so an earlier week never reads as this one.
            summary = weekOf?.let { words.pair(it, totals) } ?: totals,
        )
        return WidgetTemplates.image(
            context,
            ImageSpec(
                frame = frame,
                draw = { coloured, w, h -> RiverBitmap.draw(river, w, h, coloured) },
                alt = alt,
                topLabel = words.s(DesignR.string.contrast_flowed),
                bottomLabel = words.s(DesignR.string.contrast_read),
                legendHead = words.s(DesignR.string.widget_river_legend_head),
                legend = legend,
                caption = totals,
                // The aggregate is rolled up after each fetch, so it is as of the last one.
                footer = health.lastFetchAt?.let { words.s(DesignR.string.widget_as_of, words.stamp(it)) },
            ),
        )
    }

    // ---- Headlines from your sources (list) ----

    private suspend fun headlines(context: Context, container: AppContainer, words: Words, id: Int): RemoteViews {
        val repo = container.homeWidgetRepository
        val sourceRows = repo.sourcesWithHealth().map { it.first }
        val enabled = sourceRows.count { it.enabled }
        if (enabled == 0) return WidgetTemplates.list(context, ListSpec(noSources(context, words, NoozWidget.HEADLINES, id), emptyList()))
        val sources = words.sources(enabled)
        val title = words.s(NoozWidget.HEADLINES.title)
        val rows = HeadlinesModel.select(repo.newestHeadlines(ROWS), sourceRows.associate { it.id to it.title }, ROWS)
        val open = WidgetLinks.open(context, id, 0, WidgetDestination.STAND)
        val action = words.s(DesignR.string.widget_open_stand)
        if (rows.isEmpty()) {
            val message = words.s(DesignR.string.widget_headlines_empty, sources)
            return WidgetTemplates.list(context, ListSpec(Frame(title, null, open, action, message, message), emptyList()))
        }
        val listRows = rows.mapIndexed { i, row ->
            val headline = words.isolate(row.title)
            val meta = listOfNotNull(row.sourceTitle.takeIf { it.isNotBlank() }?.let(words::isolate), words.stamp(row.publishedAt))
                .joinToString(" · ")
            Row(
                title = headline,
                meta = meta,
                tap = WidgetLinks.open(context, id, i + 1, WidgetDestination.ITEM, row.itemId),
                description = words.pair(headline, meta),
            )
        }
        return WidgetTemplates.list(
            context,
            ListSpec(
                frame = Frame(title, null, open, action, summary = listRows.first().title),
                rows = listRows,
                style = ListStyle.ITEMS,
                // The river has banks: every size ends with a mark saying how
                // many it showed and from whose sources.
                footer = { size ->
                    val shown = minOf(WidgetSizing.listRows(ListStyle.ITEMS, size), listRows.size)
                    words.s(DesignR.string.widget_headlines_end, shown, sources)
                },
            ),
        )
    }

    // ---- Today in History (list) ----

    private suspend fun history(context: Context, container: AppContainer, words: Words, id: Int): RemoteViews {
        val enabled = container.settingsRepository.observeSettings().first().todayInHistoryEnabled
        val cached = container.todayInHistoryRepository.cachedColumn()
        val state = HistoryWidgetModel.state(enabled, cached?.date, cached?.events.orEmpty(), words.today)
        val title = words.s(NoozWidget.HISTORY.title)
        val stand = WidgetLinks.open(context, id, 0, WidgetDestination.STAND)
        return when (state) {
            HistoryWidgetState.Off -> {
                val message = words.s(DesignR.string.widget_history_off)
                val frame = Frame(
                    title = title,
                    open = WidgetLinks.open(context, id, 0, WidgetDestination.SETTINGS),
                    action = words.s(DesignR.string.settings_open_all),
                    summary = message,
                    message = message,
                )
                WidgetTemplates.list(context, ListSpec(frame, emptyList(), ListStyle.EVENTS))
            }
            HistoryWidgetState.NotLoaded -> {
                val message = words.s(DesignR.string.widget_history_not_loaded)
                val frame = Frame(title, null, stand, words.s(DesignR.string.widget_open_stand), message, message)
                WidgetTemplates.list(context, ListSpec(frame, emptyList(), ListStyle.EVENTS))
            }
            is HistoryWidgetState.Column -> {
                val date = words.longDay(state.date)
                val rows = state.events.map { event ->
                    val year = event.year.toString()
                    val text = words.isolate(event.text)
                    Row(title = text, lead = year, description = words.pair(year, text))
                }
                val earlier = if (state.isToday) null else words.s(DesignR.string.widget_history_earlier, date)
                // Wikipedia's text is CC BY-SA, so the credit is in view wherever an
                // event is, and first, so a narrow footer can never cut it off. An
                // earlier day's date is also beside the title at every size.
                val footer = earlier?.let { "${TodayInHistoryRepository.ATTRIBUTION} · $it" } ?: TodayInHistoryRepository.ATTRIBUTION
                WidgetTemplates.list(
                    context,
                    ListSpec(
                        frame = Frame(
                            title = title,
                            aside = date,
                            open = stand,
                            action = words.s(DesignR.string.widget_open_stand),
                            // The one-line summary has no room for the credit, so it shows
                            // no Wikipedia text: only the column's date, and for an earlier
                            // day's column, that it is an earlier day's.
                            summary = earlier ?: date,
                        ),
                        rows = rows,
                        style = ListStyle.EVENTS,
                        footer = { footer },
                    ),
                )
            }
        }
    }

    // ---- Source health (list) ----

    private suspend fun health(context: Context, container: AppContainer, words: Words, id: Int): RemoteViews {
        val pairs = container.homeWidgetRepository.sourcesWithHealth()
        val summary = SourceHealthSummary.summarize(pairs.map { it.first }, pairs.map { it.second })
        if (summary.enabled == 0) {
            return WidgetTemplates.list(context, ListSpec(noSources(context, words, NoozWidget.HEALTH, id), emptyList(), ListStyle.HEALTH))
        }
        val title = words.s(NoozWidget.HEALTH.title)
        val open = WidgetLinks.open(context, id, 0, WidgetDestination.SOURCES)
        // Every state's word is its own rule ("No fetch in 48 hours"), because
        // the screen a tap opens shows no health rule of its own.
        val headline = words.s(DesignR.string.widget_health_summary, summary.fetchingNormally, words.sources(summary.enabled))
        val present = SourceHealthSummary.ATTENTION_ORDER.filter { summary.count(it) > 0 }
        val counts = present.joinToString(" · ") { "${glyph(it)} ${words.s(DesignR.string.widget_health_count, words.status(it), summary.count(it))}" }
        val spokenCounts = present.joinToString(", ") { words.s(DesignR.string.widget_health_count, words.status(it), summary.count(it)) }
        // The summary row has no shape of its own: it counts every state, so no one state's shape fits it.
        val first = Row(
            title = headline,
            meta = counts.ifEmpty { null },
            tap = open,
            description = if (spokenCounts.isEmpty()) headline else words.pair(headline, spokenCounts),
        )
        val sourceSlots = (WidgetSizing.MAX_ROWS - 1)
        val rest = summary.notNormal.take(sourceSlots).mapIndexed { i, row ->
            val name = words.isolate(row.title)
            val meta = listOfNotNull(
                words.status(row.status),
                row.lastFetchAt?.let { words.s(DesignR.string.widget_health_last_tried, words.stamp(it)) },
            ).joinToString(" · ")
            Row(
                title = name,
                meta = meta,
                lead = glyph(row.status),
                tap = WidgetLinks.open(context, id, i + 2, WidgetDestination.SOURCES),
                description = words.pair(name, meta),
            )
        }
        val asOf = words.s(DesignR.string.widget_as_of, words.stamp(words.now))
        return WidgetTemplates.list(
            context,
            ListSpec(
                frame = Frame(title, null, open, words.s(DesignR.string.list_open_sources), headline),
                rows = listOf(first) + rest,
                style = ListStyle.HEALTH,
                footer = { size ->
                    val hidden = summary.notNormal.size - (WidgetSizing.listRows(ListStyle.HEALTH, size) - 1).coerceAtLeast(0)
                    if (hidden > 0) "$asOf · ${words.s(DesignR.string.widget_and_more, hidden)}" else asOf
                },
            ),
        )
    }

    /** A shape for each state, always beside its word: colour never carries it. */
    private fun glyph(status: HealthStatus): String = when (status) {
        HealthStatus.OK -> "●"
        HealthStatus.FAILING -> "▲"
        HealthStatus.RATE_LIMITED -> "◆"
        HealthStatus.STALE -> "◐"
        HealthStatus.UNKNOWN -> "○"
    }

    // ---- Clippings (list) ----

    private suspend fun clippings(context: Context, container: AppContainer, words: Words, id: Int): RemoteViews {
        val repo = container.homeWidgetRepository
        val count = repo.clippingCount()
        val title = words.s(NoozWidget.CLIPPINGS.title)
        val open = WidgetLinks.open(context, id, 0, WidgetDestination.CLIPPINGS)
        val action = words.s(DesignR.string.list_open_clippings)
        if (count == 0) {
            val message = words.s(DesignR.string.clippings_empty_body)
            val frame = Frame(title, null, open, action, words.s(DesignR.string.clippings_none_yet), message)
            return WidgetTemplates.list(context, ListSpec(frame, emptyList()))
        }
        val countText = words.s(
            if (count == 1) DesignR.string.widget_clippings_count_one else DesignR.string.widget_clippings_count_many,
            count,
        )
        val rows = ClippingsModel.rows(repo.newestClippings(ROWS), ROWS).mapIndexed { i, clip ->
            val headline = words.isolate(clip.title)
            val meta = listOfNotNull(
                clip.sourceTitle?.takeIf { it.isNotBlank() }?.let(words::isolate),
                words.s(DesignR.string.widget_clipped_on, words.day(clip.savedAt)),
            ).joinToString(" · ")
            Row(
                title = headline,
                meta = meta,
                tap = WidgetLinks.open(context, id, i + 1, WidgetDestination.CLIPPING, clip.itemId),
                description = words.pair(headline, meta),
            )
        }
        return WidgetTemplates.list(
            context,
            ListSpec(
                frame = Frame(title, countText, open, action, countText),
                rows = rows,
                style = ListStyle.ITEMS,
                footer = { size ->
                    val hidden = count - minOf(WidgetSizing.listRows(ListStyle.ITEMS, size), rows.size)
                    if (hidden > 0) words.s(DesignR.string.widget_and_more, hidden) else null
                },
            ),
        )
    }

    // ---- Coverage and breadth (stat) ----

    private suspend fun coverage(context: Context, container: AppContainer, words: Words, id: Int): RemoteViews {
        val repo = container.homeWidgetRepository
        val pairs = repo.sourcesWithHealth()
        val summary = SourceHealthSummary.summarize(pairs.map { it.first }, pairs.map { it.second })
        if (summary.enabled == 0) return WidgetTemplates.stat(context, StatSpec(noSources(context, words, NoozWidget.COVERAGE, id), emptyList()))
        val sources = words.sources(summary.enabled)
        val title = words.s(NoozWidget.COVERAGE.title)
        val record = CoverageRecordModel.from(repo.latestAggregate(), words.now)
        if (record == null) {
            val message = words.s(DesignR.string.widget_river_empty, sources)
            val frame = Frame(
                title = title,
                open = WidgetLinks.open(context, id, 0, WidgetDestination.STAND),
                action = words.s(DesignR.string.widget_open_stand),
                summary = message,
                message = message,
            )
            return WidgetTemplates.stat(context, StatSpec(frame, emptyList()))
        }
        val coverage = words.percent(record.coveragePercent)
        val totals = words.s(DesignR.string.loom_totals, record.flowed, record.read, sources)
        val opened = words.s(DesignR.string.widget_coverage_opened, record.read, record.flowed, sources)
        val rule = words.s(DesignR.string.widget_coverage_rule)
        val weekOf = if (record.isCurrentWeek) null else words.s(DesignR.string.widget_week_of, words.day(record.weekStart))
        val asOf = summary.lastFetchAt?.let { words.s(DesignR.string.widget_as_of, words.stamp(it)) }
        // The one-line summary keeps the denominator and, for an earlier week, the week's label.
        val tinyLine = words.pair(words.pair(words.s(DesignR.string.cross_coverage), coverage), totals)
        return WidgetTemplates.stat(
            context,
            StatSpec(
                frame = Frame(
                    title = title,
                    aside = weekOf,
                    open = WidgetLinks.open(context, id, 0, WidgetDestination.CROSS_SECTION),
                    action = openRegionTopics(words),
                    summary = weekOf?.let { words.pair(it, tinyLine) } ?: tinyLine,
                ),
                stats = listOf(
                    Stat(coverage, words.s(DesignR.string.cross_coverage)),
                    Stat(words.decimal(record.breadthYou), words.s(DesignR.string.cross_breadth_you)),
                    Stat(words.decimal(record.breadthStream), words.s(DesignR.string.cross_breadth_stream)),
                ),
                caption = { size -> if (size == WidgetSize.LARGE) "$opened $rule" else totals },
                footer = asOf,
            ),
        )
    }
}

/** The widget kinds, each with its provider, its name and its template. */
internal enum class NoozWidget(val provider: Class<out NoozWidgetProvider>, val title: Int, val template: Template) {
    RIVER(RiverWidgetProvider::class.java, DesignR.string.widget_river_name, Template.IMAGE),
    HEADLINES(HeadlinesWidgetProvider::class.java, DesignR.string.widget_headlines_name, Template.LIST),
    HISTORY(HistoryWidgetProvider::class.java, DesignR.string.tour_history_name, Template.LIST),
    HEALTH(SourceHealthWidgetProvider::class.java, DesignR.string.widget_health_name, Template.LIST),
    CLIPPINGS(ClippingsWidgetProvider::class.java, DesignR.string.screen_clippings, Template.LIST),
    COVERAGE(CoverageWidgetProvider::class.java, DesignR.string.widget_coverage_name, Template.STAT),
    ;

    enum class Template { LIST, STAT, IMAGE }
}

/**
 * Formatting in the reader's chosen language: the context passed in is
 * already wrapped in it (AppLocale), so dates, digits and percent signs follow
 * that language rather than the phone's.
 */
private class Words(private val context: Context, val now: Long) {
    private val locale = context.resources.configuration.locales[0]
    private val zone = ZoneId.systemDefault()
    private val bidi = BidiFormatter.getInstance(locale)

    val today: LocalDate = WidgetClock.localDate(now, zone)

    fun s(id: Int): String = context.getString(id)

    fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun pair(a: CharSequence, b: CharSequence): String = s(DesignR.string.widget_pair, a, b)

    /** "12 sources" / "1 source": the denominator, always the reader's own. */
    fun sources(count: Int): String =
        s(if (count == 1) DesignR.string.loom_source_one else DesignR.string.loom_source_many, count)

    /** Text from a feed or from Wikipedia, isolated so a left-to-right headline cannot reorder a right-to-left line around it. */
    fun isolate(text: String): String = bidi.unicodeWrap(text)

    private fun format(skeleton: String, millis: Long): String =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(millis))

    /** A short date: "4 Nov". */
    fun day(millis: Long): String = format("dMMM", millis)

    /** A long date: "4 November". */
    fun longDay(date: LocalDate): String = format("dMMMM", date.atStartOfDay(zone).toInstant().toEpochMilli())

    /**
     * A date and a time, always: a bare time would turn false at midnight if
     * the redraw that follows were late, so the stamp stays true without one.
     */
    fun stamp(millis: Long): String = format("dMMMjmm", millis)

    fun percent(value: Int): String = NumberFormat.getPercentInstance(locale).format(value / 100.0)

    fun decimal(value: Double): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(value)

    fun status(status: HealthStatus): String = when (status) {
        HealthStatus.OK -> s(DesignR.string.widget_health_ok)
        HealthStatus.FAILING -> s(DesignR.string.widget_health_failing)
        HealthStatus.RATE_LIMITED -> s(DesignR.string.widget_health_rate_limited)
        HealthStatus.STALE -> s(DesignR.string.widget_health_stale)
        HealthStatus.UNKNOWN -> s(DesignR.string.sources_not_checked)
    }
}
