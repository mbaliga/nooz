package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.RiverLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val WEEK_DESCRIPTION_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * The river's centerpiece drawing (brief §P4): topic-stacked columns over time.
 * Within each topic's band, the **read** portion renders in the full CVD-safe
 * topic color, while the **unread** remainder is muted toward near-black — a
 * single shared dark direction (not per-pair distinct hues), so it reads as one
 * undifferentiated "dark mass" rather than competing for attention with the
 * read colors (brief: "negative space is the protagonist"). Custom Canvas, no
 * chart library, so it stays token-driven and CVD-safe by construction (the
 * hues used are exactly `TopicPalette`'s, already pairwise-verified — see
 * `PaletteCvdTest` in `:core:model`).
 */
@Composable
fun RiverCanvas(
    columns: List<RiverLayout.WeekColumn>,
    selectedIndex: Int?,
    onSelectWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unreadBase = Tokens.Palette.fieldNear
    val selectionOutline = Tokens.Color.borderFocus
    val gapWidth = 3.dp

    Box(modifier = modifier.fillMaxWidth().height(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (columns.isEmpty()) return@Canvas
            val gapPx = gapWidth.toPx()
            val n = columns.size
            val colWidth = ((size.width - gapPx * (n - 1)) / n).coerceAtLeast(1f)

            columns.forEachIndexed { index, column ->
                val x = index * (colWidth + gapPx)
                var yFromBottom = 0f
                for (band in column.bands) {
                    val readTop = size.height * band.readFraction.toFloat()
                    val unreadTop = size.height * band.streamFraction.toFloat()
                    val topicColor = band.topic.toComposeColor()
                    val unreadColor = lerp(topicColor, unreadBase, 0.82f)

                    // Read sub-segment: full vivid topic color, drawn first (nearest the baseline).
                    drawRect(
                        color = topicColor,
                        topLeft = Offset(x, size.height - yFromBottom - readTop),
                        size = Size(colWidth, readTop),
                    )
                    // Unread sub-segment: muted toward near-black — the dark mass.
                    drawRect(
                        color = unreadColor,
                        topLeft = Offset(x, size.height - yFromBottom - unreadTop),
                        size = Size(colWidth, unreadTop - readTop),
                    )
                    yFromBottom += unreadTop
                }
                if (index == selectedIndex) {
                    drawSelectionOutline(x, colWidth, size.height, selectionOutline)
                }
            }
        }

        // Accessibility + tap layer (brief §P7: "TalkBack labels including...
        // river regions"). A `Canvas` draws pixels with no accessibility tree of
        // its own, so this overlay — one real, focusable, describable element per
        // week, laid out with the same weight+gap proportions as the Canvas's own
        // column math above — is what makes the chart navigable and readable by
        // TalkBack at all, not just visually. It also now owns tap-to-select
        // (replacing the old manual `pointerInput` + pixel-offset math): Compose's
        // layout system hit-tests each column for free once it is a real element.
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gapWidth)) {
            columns.forEachIndexed { index, column ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = index == selectedIndex, onClick = { onSelectWeek(index) })
                        .semantics(mergeDescendants = true) { contentDescription = describeColumn(column) },
                )
            }
        }
    }
}

private fun describeColumn(column: RiverLayout.WeekColumn): String {
    val date = WEEK_DESCRIPTION_FORMAT.format(Instant.ofEpochMilli(column.weekStart).atZone(ZoneId.systemDefault()))
    if (column.bands.isEmpty()) return "Week of $date. Nothing flowed this week."
    val perTopic = column.bands.joinToString(". ") { band ->
        val percent = if (band.streamCount > 0) (band.readCount * 100.0 / band.streamCount).roundToInt() else 0
        "${band.topic.placeholderLabel}: $percent% read, ${band.readCount} of ${band.streamCount}"
    }
    return "Week of $date. $perTopic. ${column.totalRead} of ${column.totalStream} read overall."
}

private fun DrawScope.drawSelectionOutline(x: Float, width: Float, height: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x, 0f),
        size = Size(width, height),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
    )
}
