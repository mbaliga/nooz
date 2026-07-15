package xyz.mdhv.riverwip.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.model.GlobeModel
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.Topic

/**
 * The region-picker globe (owner's interactive reference): a dotted
 * orthographic earth — drag to spin, pinch to widen the selection band — inside
 * a ring showing the aimed region's real topic mix, OR (owner #6b, Contrast)
 * a ring + shaded dots showing how much you've *read* from each region.
 * All projection/sector math is the pure [GlobeModel]; colours are the
 * CVD-verified palette. Lives in `:core:design` (not a feature module) so
 * both Edit's region picker and the Contrast read-heatmap can share the same
 * globe rather than two different pickers. The globe is a shortcut, never the
 * only door: a label beneath it (or the region chips beside it, in Contrast)
 * does the same job without gestures.
 *
 * Exactly one of [ringMix] (a topic breakdown) or [heatByRegion] (a read-count
 * per region) should be non-empty — [heatByRegion] takes priority when both
 * are supplied. With both empty the ring falls back to a quiet hairline.
 */
@Composable
fun GlobeCanvas(
    yaw: Double,
    pitch: Double,
    bandHalf: Double,
    onSpin: (dYaw: Double, dPitch: Double) -> Unit,
    onZoomBand: (factor: Double) -> Unit,
    modifier: Modifier = Modifier,
    ringMix: Map<Topic, Int> = emptyMap(),
    heatByRegion: Map<Region, Int> = emptyMap(),
) {
    val region = if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) {
        Region.GLOBAL
    } else {
        Region.forLongitude(GlobeModel.centerLongitude(yaw))
    }
    val dotSelected = MaterialTheme.colorScheme.onBackground
    val dotDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val sphere = MaterialTheme.colorScheme.surfaceVariant
    val rim = MaterialTheme.colorScheme.outlineVariant
    val guide = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription =
                    "Region globe, aimed at ${region.label}. Drag to spin, pinch to widen the band."
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) onZoomBand(zoom.toDouble())
                    // Horizontal spin only (owner): the earth yaws around its
                    // axis; it never tips. The vertical pan component is dropped
                    // so the poles stay put and regions read consistently.
                    if (pan.x != 0f) onSpin(pan.x * 0.4, 0.0)
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val ringOuter = size.minDimension / 2f - 2.dp.toPx()
            val ringWidth = 11.dp.toPx()
            val r = ringOuter - ringWidth - 15.dp.toPx()

            val heatMax = heatByRegion.values.maxOrNull()?.takeIf { it > 0 } ?: 1

            when {
                heatByRegion.isNotEmpty() -> {
                    // Read-heatmap ring (owner #6b): one arc per region, sized by
                    // its real longitude span, shaded by how much of your reading
                    // came from it — the globe standing in for the read-heatmap
                    // rather than a topic breakdown.
                    var startDeg = -90f
                    for (r2 in Region.entries) {
                        if (r2 == Region.GLOBAL) continue
                        val span = (r2.toLon - r2.fromLon).toFloat()
                        val v = heatByRegion[r2] ?: 0
                        val alpha = 0.14f + 0.6f * (v.toFloat() / heatMax)
                        drawArc(
                            color = dotSelected.copy(alpha = alpha),
                            startAngle = startDeg,
                            sweepAngle = (span - 1.5f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(cx - ringOuter + ringWidth / 2, cy - ringOuter + ringWidth / 2),
                            size = androidx.compose.ui.geometry.Size((ringOuter - ringWidth / 2) * 2, (ringOuter - ringWidth / 2) * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth),
                        )
                        startDeg += span
                    }
                }
                ringMix.isNotEmpty() -> {
                    // Topic-mix ring (real counts).
                    val total = ringMix.values.sum()
                    var startDeg = -90f
                    for (topic in Topic.entries) {
                        val v = ringMix[topic] ?: 0
                        if (v == 0) continue
                        val sweep = 360f * v / total
                        drawArc(
                            color = topic.toComposeColor(),
                            startAngle = startDeg,
                            sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(cx - ringOuter + ringWidth / 2, cy - ringOuter + ringWidth / 2),
                            size = androidx.compose.ui.geometry.Size((ringOuter - ringWidth / 2) * 2, (ringOuter - ringWidth / 2) * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth),
                        )
                        startDeg += sweep
                    }
                }
                else -> {
                    drawCircle(rim, radius = ringOuter - ringWidth / 2, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(ringWidth / 3))
                }
            }

            // Sphere + dotted land.
            drawCircle(sphere, radius = r, center = Offset(cx, cy))
            drawCircle(rim, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            for (dot in GlobeModel.dots) {
                val p = GlobeModel.project(dot[0], dot[1], yaw, pitch) ?: continue
                val shade = (0.45 + 0.55 * kotlin.math.cos(p.distance)).toFloat()
                val color = if (heatByRegion.isNotEmpty()) {
                    val v = heatByRegion[Region.forLongitude(dot[0])] ?: 0
                    val heatFrac = v.toFloat() / heatMax
                    dotSelected.copy(alpha = (0.12f + 0.75f * heatFrac) * (0.6f + 0.4f * shade))
                } else if (GlobeModel.inBand(dot[0], yaw, bandHalf)) {
                    dotSelected.copy(alpha = 0.6f + 0.4f * shade)
                } else {
                    dotDim
                }
                drawCircle(
                    color = color,
                    radius = (1.3f + 1.1f * shade).dp.toPx() * 0.9f,
                    center = Offset(cx + (p.x * r).toFloat(), cy + (p.y * r).toFloat()),
                )
            }

            // Band guides: the selected slice's edges.
            if (bandHalf < GlobeModel.GLOBAL_BAND_THRESHOLD) {
                val half = ((bandHalf / 180.0) * r * 0.7).toFloat().coerceAtLeast(1f)
                drawLine(guide, Offset(cx - half, cy - r - 6.dp.toPx()), Offset(cx - half, cy + r + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
                drawLine(guide, Offset(cx + half, cy - r - 6.dp.toPx()), Offset(cx + half, cy + r + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
        }
    }
}
