package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.RiverLayout

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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(columns.size) {
                detectTapGestures { offset ->
                    if (columns.isEmpty()) return@detectTapGestures
                    val colWidth = size.width.toFloat() / columns.size
                    val idx = (offset.x / colWidth).toInt().coerceIn(0, columns.size - 1)
                    onSelectWeek(idx)
                }
            },
    ) {
        if (columns.isEmpty()) return@Canvas
        val gapPx = 3.dp.toPx()
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
}

private fun DrawScope.drawSelectionOutline(x: Float, width: Float, height: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x, 0f),
        size = Size(width, height),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
    )
}
