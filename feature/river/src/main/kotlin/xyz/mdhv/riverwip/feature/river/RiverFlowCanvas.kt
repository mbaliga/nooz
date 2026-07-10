package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.RiverFlowLayout
import xyz.mdhv.riverwip.model.TopicPalette
import xyz.mdhv.riverwip.model.formatCompactCount

/**
 * The hourglass flow (owner's Viz mock, 2026-07): supply streams fan in from
 * the top, converge to the waist — the honest ratio of read-to-flowed — and
 * what was read fans back out by topic below. Geometry comes from the pure
 * [RiverFlowLayout]; this maps fractions to pixels and draws the curves.
 *
 * Colours: bottom streams are the CVD-verified topic palette (their true
 * semantic hues — the same pairwise-ΔE-tested set as everywhere else, kept
 * over the mock's exact stream colours, which include red/green pairs the
 * build-failing palette test exists to prevent). Top source streams cycle the
 * same verified hues *decoratively* — colour is not a semantic channel for
 * sources; the accessibility description names them with counts instead.
 */
@Composable
fun RiverFlowCanvas(
    flow: RiverFlowLayout.Flow,
    enabledSourceCount: Int,
    sourceTitles: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val paletteCycle = TopicPalette.colors.values.map { Color(it) }
    val description = describeFlow(flow, enabledSourceCount, sourceTitles)
    val waistThread = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(460.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val waistY = h * 0.54f
            val waistHalf = (flow.waistWidth * w / 2f).toFloat()
            val waistL = w / 2f - waistHalf
            val waistR = w / 2f + waistHalf

            // Top: one stream per source, fading in at the top edge and
            // converging on the waist. Drawn widest-first so thinner streams
            // read on top of the pile-up near the waist.
            flow.sources.forEachIndexed { i, s ->
                val color = paletteCycle[i % paletteCycle.size]
                val left = ((s.xCenter - s.width / 2) * w).toFloat()
                val right = ((s.xCenter + s.width / 2) * w).toFloat()
                val straightY = waistY * 0.45f
                val path = Path().apply {
                    moveTo(left, 0f)
                    lineTo(left, straightY)
                    cubicTo(left, straightY + (waistY - straightY) * 0.6f, waistL, waistY - (waistY - straightY) * 0.25f, waistL, waistY)
                    lineTo(waistR, waistY)
                    cubicTo(waistR, waistY - (waistY - straightY) * 0.25f, right, straightY + (waistY - straightY) * 0.6f, right, straightY)
                    lineTo(right, 0f)
                    close()
                }
                drawPath(
                    path,
                    brush = Brush.verticalGradient(
                        0f to color.copy(alpha = 0f),
                        0.16f to color,
                        startY = 0f,
                        endY = waistY,
                    ),
                )
            }

            // Bottom: one stream per read topic, diverging from the waist to
            // its band position — in the topic's true CVD-verified colour.
            flow.topics.forEach { t ->
                val color = t.topic.toComposeColor()
                val left = ((t.xCenter - t.width / 2) * w).toFloat()
                val right = ((t.xCenter + t.width / 2) * w).toFloat()
                val straightY = waistY + (h - waistY) * 0.55f
                val path = Path().apply {
                    moveTo(waistL, waistY)
                    cubicTo(waistL, waistY + (straightY - waistY) * 0.25f, left, straightY - (straightY - waistY) * 0.6f, left, straightY)
                    lineTo(left, h)
                    lineTo(right, h)
                    lineTo(right, straightY)
                    cubicTo(right, straightY - (straightY - waistY) * 0.6f, waistR, waistY + (straightY - waistY) * 0.25f, waistR, waistY)
                    close()
                }
                drawPath(
                    path,
                    brush = Brush.verticalGradient(
                        0.9f to color,
                        1f to color.copy(alpha = 0.25f),
                        startY = waistY,
                        endY = h,
                    ),
                )
            }

            // Nothing read this period: mark the pinch point so the empty
            // bottom half reads as a statement, not a rendering gap.
            if (flow.totalRead == 0 && flow.totalStream > 0) {
                drawCircle(color = waistThread, radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w / 2f, waistY))
            }
        }

        // Labels (real text, merged into the container's a11y description).
        val numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal)
        Column(modifier = Modifier.align(BiasAlignment(-0.82f, -0.02f))) {
            Text(formatCompactCount(flow.totalStream), style = numberStyle)
            Text(
                "SOURCE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.align(BiasAlignment(0.84f, 0.24f)), horizontalAlignment = Alignment.End) {
            Text(
                "CONSUMPTION",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatCompactCount(flow.totalRead), style = numberStyle)
        }

        // The mock labels the dominant consumption stream in place. Colour is
        // never the only channel: the label itself names the topic.
        val biggest = flow.topics.maxByOrNull { it.width }
        if (biggest != null) {
            val streamColor = biggest.topic.toComposeColor()
            Text(
                biggest.topic.placeholderLabel.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = if (streamColor.luminance() > 0.5f) Color(0xFF141414) else Color(0xFFF7F6F3),
                modifier = Modifier.align(BiasAlignment((biggest.xCenter * 2 - 1).toFloat(), 0.82f)),
            )
        }
    }
}

private fun describeFlow(
    flow: RiverFlowLayout.Flow,
    enabledSourceCount: Int,
    sourceTitles: Map<String, String>,
): String {
    if (flow.totalStream == 0) return "Nothing flowed from your $enabledSourceCount sources this period."
    val topics = if (flow.topics.isEmpty()) {
        "you read none of it"
    } else {
        "you read ${flow.totalRead}: " + flow.topics.joinToString(", ") { "${it.topic.placeholderLabel} ${it.count}" }
    }
    val topSources = flow.sources.take(5).joinToString(", ") { s ->
        "${sourceTitles[s.sourceId] ?: "a source"} ${s.count}"
    }
    val more = if (flow.sources.size > 5) ", and ${flow.sources.size - 5} more" else ""
    return "${flow.totalStream} stories flowed from your $enabledSourceCount sources this period; " +
        "$topics. Largest supplies: $topSources$more."
}
