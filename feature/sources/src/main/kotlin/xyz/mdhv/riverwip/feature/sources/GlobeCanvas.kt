package xyz.mdhv.riverwip.feature.sources

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
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.GlobeModel
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.Topic

/**
 * The region-picker globe (owner's interactive reference): a dotted
 * orthographic earth — drag to spin, pinch to widen the selection band — inside
 * a ring showing the aimed region's real topic mix. All projection/sector math
 * is the pure [GlobeModel]; colours are the CVD-verified palette. The globe is
 * a shortcut, never the only door: the sector label beneath it names the
 * selection, and the topic chips beside it do the same job without gestures.
 */
@Composable
fun GlobeCanvas(
    yaw: Double,
    pitch: Double,
    bandHalf: Double,
    ringMix: Map<Topic, Int>,
    onSpin: (dYaw: Double, dPitch: Double) -> Unit,
    onZoomBand: (factor: Double) -> Unit,
    modifier: Modifier = Modifier,
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
                    if (pan != Offset.Zero) onSpin(pan.x * 0.4, -pan.y * 0.4)
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val ringOuter = size.minDimension / 2f - 2.dp.toPx()
            val ringWidth = 11.dp.toPx()
            val r = ringOuter - ringWidth - 15.dp.toPx()

            // Topic-mix ring (real counts; empty mix = quiet hairline ring).
            val total = ringMix.values.sum()
            if (total > 0) {
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
            } else {
                drawCircle(rim, radius = ringOuter - ringWidth / 2, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(ringWidth / 3))
            }

            // Sphere + dotted land.
            drawCircle(sphere, radius = r, center = Offset(cx, cy))
            drawCircle(rim, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            for (dot in GlobeModel.dots) {
                val p = GlobeModel.project(dot[0], dot[1], yaw, pitch) ?: continue
                val shade = (0.45 + 0.55 * kotlin.math.cos(p.distance)).toFloat()
                val selected = GlobeModel.inBand(dot[0], yaw, bandHalf)
                drawCircle(
                    color = if (selected) dotSelected.copy(alpha = 0.6f + 0.4f * shade) else dotDim,
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
