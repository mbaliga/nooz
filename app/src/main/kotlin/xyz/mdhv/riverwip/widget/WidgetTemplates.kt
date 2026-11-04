package xyz.mdhv.riverwip.widget

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import xyz.mdhv.riverwip.R
import xyz.mdhv.riverwip.design.R as DesignR
import xyz.mdhv.riverwip.model.ListStyle
import xyz.mdhv.riverwip.model.WidgetSize
import xyz.mdhv.riverwip.model.WidgetSizing
import xyz.mdhv.riverwip.model.WidgetTemplate

/*
 * The three templates every Nooz widget is drawn with (D-AD's `list`, `stat`
 * and `image`), each as one RemoteViews per size class, handed to the launcher
 * as a size map so it picks the layout for the widget's current size itself,
 * without waking the app. The widgets fill these specs in WidgetContent.kt;
 * these functions only bind them, so every widget obeys the same layout rules.
 *
 * Each size's breakpoint is the height its layout needs at the reader's font
 * scale (WidgetSizing), so the launcher never picks a layout that cannot fit.
 *
 * What TalkBack says for a widget is built here, from exactly the text each
 * size shows, as it is bound: a clickable container's description replaces
 * its children's text for a screen reader, so a short one would hide the
 * numbers, the end-of-list mark and the "as of" time.
 */

/** What every template shares: its name, where a tap on it goes, and the one-line summary drawn below its minimum size. */
internal class Frame(
    val title: CharSequence,
    /** A short note beside the title: a date, a count. */
    val aside: CharSequence? = null,
    /** The tap on anything that is not its own row. */
    val open: PendingIntent,
    /** What that tap does, said after everything the widget shows ("Opens the Stand"). */
    val action: CharSequence,
    /** The single line shown at [WidgetSize.TINY]. */
    val summary: CharSequence,
    /**
     * Replaces the body when there is nothing to show: empty, off, not yet
     * loaded, or an error, always in words.
     */
    val message: CharSequence? = null,
)

internal class Row(
    val title: CharSequence,
    val meta: CharSequence? = null,
    /** A year or a status shape. Never read aloud on its own: [description] carries it. */
    val lead: CharSequence? = null,
    /** Null for rows that are not their own tap target. */
    val tap: PendingIntent? = null,
    val description: CharSequence,
)

internal class ListSpec(
    val frame: Frame,
    val rows: List<Row>,
    /** What the rows hold, which sets how many show and how many lines each takes at each size. */
    val style: ListStyle = ListStyle.ITEMS,
    /** Per size: the end-of-list mark, an attribution, an "as of" time, the overflow count. */
    val footer: (WidgetSize) -> CharSequence? = { null },
)

internal class Stat(val value: CharSequence, val label: CharSequence)

internal class StatSpec(
    val frame: Frame,
    /** Up to three, in order: the small layout shows the first, medium two, large all three. */
    val stats: List<Stat>,
    /** Caption text per size: the denominator, and in the large layout the formulas. */
    val caption: (WidgetSize) -> CharSequence? = { null },
    val footer: CharSequence? = null,
)

internal class ImageSpec(
    val frame: Frame,
    /**
     * Draws the picture at the given pixel size, in topic colours or in
     * neutral ink; null when the widget is in a message state. Topic colours
     * are only used where the legend names every band.
     */
    val draw: ((coloured: Boolean, width: Int, height: Int) -> Bitmap)?,
    /** The picture's alt text, carrying every number it shows. */
    val alt: CharSequence,
    val topLabel: CharSequence? = null,
    val bottomLabel: CharSequence? = null,
    val legendHead: CharSequence? = null,
    /** One entry per band, each a swatch and the topic's name and shares. */
    val legend: List<CharSequence> = emptyList(),
    val caption: CharSequence? = null,
    val footer: CharSequence? = null,
)

internal object WidgetTemplates {

    private val ROW_IDS = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
    private val ROW_TITLE_IDS = intArrayOf(R.id.row1_title, R.id.row2_title, R.id.row3_title, R.id.row4_title, R.id.row5_title)
    private val ROW_META_IDS = intArrayOf(R.id.row1_meta, R.id.row2_meta, R.id.row3_meta, R.id.row4_meta, R.id.row5_meta)
    private val ROW_LEAD_IDS = intArrayOf(R.id.row1_lead, R.id.row2_lead, R.id.row3_lead, R.id.row4_lead, R.id.row5_lead)
    private val STAT_IDS = intArrayOf(R.id.stat1, R.id.stat2, R.id.stat3)
    private val STAT_VALUE_IDS = intArrayOf(R.id.stat1_value, R.id.stat2_value, R.id.stat3_value)
    private val STAT_LABEL_IDS = intArrayOf(R.id.stat1_label, R.id.stat2_label, R.id.stat3_label)

    /** Legend slots, numbered across then down: 1 and 2 are the first line's two columns. */
    private val LEGEND_IDS = intArrayOf(
        R.id.legend1, R.id.legend2, R.id.legend3, R.id.legend4, R.id.legend5,
        R.id.legend6, R.id.legend7, R.id.legend8, R.id.legend9, R.id.legend10,
    )

    /**
     * The most pixels per dp a drawn picture gets. Two pictures travel in one
     * update (the neutral one for the small and medium layouts, the coloured
     * one for the large), and an update has to stay well under the binder's
     * 1 MB transaction limit.
     */
    private const val MAX_BITMAP_DENSITY = 2f

    /** The picture's drawing size, in dp: about what its layout gives it at a common cell size. The ImageView stretches it to fit. */
    private const val PICTURE_WIDTH_DP = 200f
    private const val PICTURE_HEIGHT_DP = 104f

    fun list(context: Context, spec: ListSpec): RemoteViews =
        sized(context, WidgetTemplate.LIST, spec.style, spec.frame) { size ->
            val layout = when (size) {
                WidgetSize.SMALL -> R.layout.widget_list_small
                WidgetSize.MEDIUM -> R.layout.widget_list_medium
                else -> R.layout.widget_list_large
            }
            RemoteViews(context.packageName, layout).apply {
                val speech = bindFrame(spec.frame)
                val inMessage = spec.frame.message != null
                val shapes = WidgetSizing.rows(spec.style, size)
                val shown = if (inMessage) 0 else shapes.size.coerceAtMost(spec.rows.size)
                setViewVisibility(R.id.widget_rows, if (inMessage) View.GONE else View.VISIBLE)
                // Every slot the layout has: those past what this size shows are hidden.
                val slots = when (size) {
                    WidgetSize.SMALL -> 1
                    WidgetSize.MEDIUM -> 3
                    else -> WidgetSizing.MAX_ROWS
                }
                for (i in 0 until slots) {
                    if (i >= shown) {
                        setViewVisibility(ROW_IDS[i], View.GONE)
                        continue
                    }
                    val row = spec.rows[i]
                    setViewVisibility(ROW_IDS[i], View.VISIBLE)
                    setTextViewText(ROW_TITLE_IDS[i], row.title)
                    setInt(ROW_TITLE_IDS[i], "setMaxLines", shapes[i].title)
                    if (shapes[i].meta > 0) {
                        setOptionalText(ROW_META_IDS[i], row.meta)
                        setInt(ROW_META_IDS[i], "setMaxLines", shapes[i].meta)
                    } else {
                        setViewVisibility(ROW_META_IDS[i], View.GONE)
                    }
                    setOptionalText(ROW_LEAD_IDS[i], row.lead)
                    // Rows are their own focus targets, so each says itself.
                    setContentDescription(ROW_IDS[i], row.description)
                    // A row with no target of its own falls through to the widget's.
                    setOnClickPendingIntent(ROW_IDS[i], row.tap ?: spec.frame.open)
                }
                val footer = if (inMessage) null else spec.footer(size)
                setOptionalText(R.id.widget_footer, footer)
                speech += footer
                describe(context, speech, spec.frame.action)
            }
        }

    fun stat(context: Context, spec: StatSpec): RemoteViews =
        sized(context, WidgetTemplate.STAT, ListStyle.ITEMS, spec.frame) { size ->
            val (layout, columns) = when (size) {
                WidgetSize.SMALL -> R.layout.widget_stat_small to 1
                WidgetSize.MEDIUM -> R.layout.widget_stat_medium to 2
                else -> R.layout.widget_stat_large to 3
            }
            RemoteViews(context.packageName, layout).apply {
                val speech = bindFrame(spec.frame)
                val inMessage = spec.frame.message != null
                setViewVisibility(R.id.widget_stats, if (inMessage) View.GONE else View.VISIBLE)
                for (i in 0 until columns) {
                    val stat = spec.stats.getOrNull(i)
                    if (inMessage || stat == null) {
                        setViewVisibility(STAT_IDS[i], View.GONE)
                        continue
                    }
                    setViewVisibility(STAT_IDS[i], View.VISIBLE)
                    setTextViewText(STAT_VALUE_IDS[i], stat.value)
                    setTextViewText(STAT_LABEL_IDS[i], stat.label)
                    speech += pair(context, stat.label, stat.value)
                }
                val caption = if (inMessage) null else spec.caption(size)
                setOptionalText(R.id.widget_caption, caption)
                speech += caption
                setOptionalText(R.id.widget_footer, spec.footer)
                speech += spec.footer
                describe(context, speech, spec.frame.action)
            }
        }

    fun image(context: Context, spec: ImageSpec): RemoteViews {
        // The small and medium layouts share one neutral picture, so an update
        // carries two bitmaps rather than three.
        val pictures = HashMap<Boolean, Bitmap>()
        val (w, h) = picturePixels(context)
        return sized(context, WidgetTemplate.IMAGE, ListStyle.ITEMS, spec.frame) { size ->
            val layout = when (size) {
                WidgetSize.SMALL -> R.layout.widget_image_small
                WidgetSize.MEDIUM -> R.layout.widget_image_medium
                else -> R.layout.widget_image_large
            }
            RemoteViews(context.packageName, layout).apply {
                val speech = bindFrame(spec.frame)
                val draw = spec.draw
                if (spec.frame.message != null || draw == null) {
                    setViewVisibility(R.id.widget_picture, View.GONE)
                } else {
                    // Topic colours only where the legend names every band; elsewhere
                    // the bands are neutral, so colour never carries meaning alone.
                    val coloured = size == WidgetSize.LARGE && spec.legend.isNotEmpty()
                    setViewVisibility(R.id.widget_picture, View.VISIBLE)
                    setImageViewBitmap(R.id.widget_image, pictures.getOrPut(coloured) { draw(coloured, w, h) })
                    setContentDescription(R.id.widget_image, spec.alt)
                    speech += spec.alt
                    if (size != WidgetSize.SMALL) {
                        setOptionalText(R.id.widget_label_top, spec.topLabel)
                        setOptionalText(R.id.widget_label_bottom, spec.bottomLabel)
                    }
                    if (coloured) {
                        setOptionalText(R.id.widget_legend_head, spec.legendHead)
                        setViewVisibility(R.id.widget_legend, View.VISIBLE)
                        LEGEND_IDS.forEachIndexed { i, id -> setOptionalText(id, spec.legend.getOrNull(i)) }
                    }
                    setOptionalText(R.id.widget_caption, spec.caption)
                    speech += spec.caption
                }
                setOptionalText(R.id.widget_footer, spec.footer)
                speech += spec.footer
                describe(context, speech, spec.frame.action)
            }
        }
    }

    /**
     * The picture's pixel size: [PICTURE_WIDTH_DP] by [PICTURE_HEIGHT_DP] at
     * the screen's density, capped at [MAX_BITMAP_DENSITY]. The ImageView
     * stretches it to the real size; the picture is flat bands, so a stretch
     * never distorts anything that carries meaning.
     */
    private fun picturePixels(context: Context): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density.coerceAtMost(MAX_BITMAP_DENSITY)
        return (PICTURE_WIDTH_DP * density).toInt().coerceAtLeast(1) to (PICTURE_HEIGHT_DP * density).toInt().coerceAtLeast(1)
    }

    private fun sized(
        context: Context,
        template: WidgetTemplate,
        style: ListStyle,
        frame: Frame,
        build: (WidgetSize) -> RemoteViews,
    ): RemoteViews {
        // The reader's font size moves every breakpoint, so a larger font gets
        // a roomier layout's worth of height before it gets that layout.
        val fontScale = context.resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
        return RemoteViews(
            WidgetSizing.breakpoints(template, style, fontScale).associate { point ->
                SizeF(point.widthDp, point.heightDp) to
                    if (point.size == WidgetSize.TINY) tiny(context, frame) else build(point.size)
            },
        )
    }

    private fun tiny(context: Context, frame: Frame): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_tiny).apply {
            val value = frame.message ?: frame.summary
            setTextViewText(R.id.widget_title, frame.title)
            setTextViewText(R.id.widget_value, value)
            setOnClickPendingIntent(android.R.id.background, frame.open)
            describe(context, mutableListOf(frame.title, value), frame.action)
        }

    /** Binds the title, the aside and the message, and returns them as the start of what TalkBack says. */
    private fun RemoteViews.bindFrame(frame: Frame): MutableList<CharSequence?> {
        setTextViewText(R.id.widget_title, frame.title)
        setOptionalText(R.id.widget_aside, frame.aside)
        setOptionalText(R.id.widget_message, frame.message)
        setOnClickPendingIntent(android.R.id.background, frame.open)
        return mutableListOf(frame.title, frame.aside, frame.message)
    }

    /** The widget's own description: everything it shows except its separately focusable rows, then what a tap does. */
    private fun RemoteViews.describe(context: Context, parts: List<CharSequence?>, action: CharSequence) {
        setContentDescription(android.R.id.background, WidgetSpeech.join(parts + action) { a, b -> pair(context, a, b) })
    }

    private fun pair(context: Context, a: CharSequence, b: CharSequence): String =
        context.getString(DesignR.string.widget_pair, a, b)

    private fun RemoteViews.setOptionalText(id: Int, text: CharSequence?) {
        if (text.isNullOrBlank()) {
            setViewVisibility(id, View.GONE)
        } else {
            setViewVisibility(id, View.VISIBLE)
            setTextViewText(id, text)
        }
    }
}

/** How a widget's parts become one spoken description. Pure, so it is tested on the JVM. */
internal object WidgetSpeech {

    /** Sentence ends after which the next part starts as its own sentence rather than after a comma. */
    private const val SENTENCE_ENDS = ".!?…。।؟۔"

    /**
     * Joins [parts] in order, skipping blank ones: after a part that ends a
     * sentence the next follows with a space, otherwise the two are joined
     * with [pair] (the language's own list comma, from `widget_pair`).
     */
    fun join(parts: List<CharSequence?>, pair: (CharSequence, CharSequence) -> CharSequence): String {
        var spoken: CharSequence? = null
        for (part in parts) {
            if (part.isNullOrBlank()) continue
            val text = part.trim()
            val so = spoken
            spoken = when {
                so == null -> text
                so.last() in SENTENCE_ENDS -> "$so $text"
                else -> pair(so, text)
            }
        }
        return spoken?.toString().orEmpty()
    }
}
