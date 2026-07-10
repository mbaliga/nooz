package xyz.mdhv.riverwip.feature.river

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.DayLoomLayout
import xyz.mdhv.riverwip.model.formatCompactCount

/**
 * The day loom (owner's v7 reference): every topic's tube plunges from the
 * day's supply toward the waist; what was read passes through as stems and
 * fans out below. A curtain sweep reveals it top-to-bottom; tapping a tube
 * opens the inspector (label mode "tap" per the reference — no inline labels).
 * Geometry comes from the pure [DayLoomLayout]; colours are the CVD-verified
 * topic palette, never the reference's ad-libbed hues.
 */
@Composable
fun DayLoomCanvas(
    loom: DayLoomLayout.Loom,
    enabledSourceCount: Int,
    modifier: Modifier = Modifier,
) {
    var selected by remember(loom) { mutableStateOf<DayLoomLayout.Band?>(null) }
    val curtain = remember(loom) { Animatable(0f) }
    LaunchedEffect(loom) {
        curtain.animateTo(1f, tween(1_400, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)))
    }

    val description = describeLoom(loom, enabledSourceCount)
    val curtainColor = MaterialTheme.colorScheme.background
    val w = DayLoomLayout.W.toFloat()
    val h = DayLoomLayout.H.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio((DayLoomLayout.W / DayLoomLayout.H).toFloat())
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio((DayLoomLayout.W / DayLoomLayout.H).toFloat())
                .pointerInput(loom) {
                    detectTapGestures { pos ->
                        val sx = size.width / w
                        val sy = size.height / h
                        selected = hitBand(loom, pos.x / sx, pos.y / sy)
                    }
                },
        ) {
            val sx = size.width / w
            val sy = size.height / h
            for (band in loom.bands) {
                val faded = selected != null && selected !== band
                drawPath(
                    tubePath(band.stations, sx, sy),
                    color = band.topic.toComposeColor().copy(alpha = if (faded) 0.22f else 1f),
                )
            }
            // Curtain: a surface-coloured sheet slides down, revealing the loom
            // from the top (the reference's sweep).
            val revealed = curtain.value
            if (revealed < 1f) {
                drawRect(
                    color = curtainColor,
                    topLeft = Offset(0f, size.height * revealed),
                    size = Size(size.width, size.height * (1f - revealed)),
                )
            }
        }

        // Numbers + captions (real text; the container's description carries the a11y story).
        val numberStyle = MaterialTheme.typography.titleMedium
            .copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light)
        val captionStyle = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.5.sp)
        val ink = MaterialTheme.colorScheme.onBackground
        val dim = MaterialTheme.colorScheme.onSurfaceVariant

        Column(modifier = Modifier.align(BiasAlignment(-0.85f, 0.02f))) {
            Text(formatCompactCount(loom.totalFlowed), style = numberStyle, color = ink)
            Text("SOURCE", style = captionStyle, color = dim)
        }
        Column(modifier = Modifier.align(BiasAlignment(0.86f, 0.28f)), horizontalAlignment = Alignment.End) {
            Text("CONSUMPTION", style = captionStyle, color = dim)
            Text(formatCompactCount(loom.totalRead), style = numberStyle, color = ink)
        }

        // Inspector (label mode "tap"): the selected tube's honest line.
        val sel = selected
        if (sel != null) {
            Column(modifier = Modifier.align(BiasAlignment(-0.85f, 0.52f))) {
                Text(
                    sel.topic.placeholderLabel.uppercase(),
                    style = captionStyle,
                    color = sel.topic.toComposeColor(),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${sel.flowed} flowed" + if (sel.read > 0) " · ${sel.read} read" else " · none read",
                    style = MaterialTheme.typography.bodyMedium,
                    color = dim,
                )
            }
        }

        // Denominator honesty (brief §1), verbatim register from the reference.
        Text(
            "${loom.totalFlowed} flowed · ${loom.totalRead} read · ${Copy.fromSources(enabledSourceCount)}",
            style = MaterialTheme.typography.labelSmall,
            color = dim,
            modifier = Modifier.align(BiasAlignment(0f, 0.97f)),
        )
    }
}

/** Build one tube's closed path from its stations (port of the reference's `tube()`). */
private fun tubePath(stations: List<DayLoomLayout.Station>, sx: Float, sy: Float): Path {
    val eases = listOf(DayLoomLayout.EASE_TOP, DayLoomLayout.EASE_BOT)
    val path = Path()
    val first = stations.first()
    path.moveTo(((first.x - first.w / 2) * sx).toFloat(), (first.y * sy).toFloat())
    for (i in 0 until stations.size - 1) {
        val a = stations[i]
        val b = stations[i + 1]
        val dy = b.y - a.y
        val (k1, k2) = eases[i]
        path.cubicTo(
            ((a.x - a.w / 2) * sx).toFloat(), ((a.y + dy * k1) * sy).toFloat(),
            ((b.x - b.w / 2) * sx).toFloat(), ((b.y - dy * k2) * sy).toFloat(),
            ((b.x - b.w / 2) * sx).toFloat(), (b.y * sy).toFloat(),
        )
    }
    val last = stations.last()
    path.lineTo(((last.x + last.w / 2) * sx).toFloat(), (last.y * sy).toFloat())
    for (i in stations.size - 1 downTo 1) {
        val a = stations[i]
        val b = stations[i - 1]
        val dy = b.y - a.y
        val (k1, k2) = eases[i - 1]
        path.cubicTo(
            ((a.x + a.w / 2) * sx).toFloat(), ((a.y + dy * k2) * sy).toFloat(),
            ((b.x + b.w / 2) * sx).toFloat(), ((b.y - dy * k1) * sy).toFloat(),
            ((b.x + b.w / 2) * sx).toFloat(), (b.y * sy).toFloat(),
        )
    }
    path.close()
    return path
}

/**
 * Tap → tube, by interpolating each band's centre/width between its stations
 * at the tap's height (linear approximation of the cubic — ample for a finger).
 * Topmost-drawn wins, so iterate the draw order backwards.
 */
private fun hitBand(loom: DayLoomLayout.Loom, x: Float, y: Float): DayLoomLayout.Band? {
    for (band in loom.bands.asReversed()) {
        val s = band.stations
        for (i in 0 until s.size - 1) {
            val a = s[i]
            val b = s[i + 1]
            if (y < a.y || y > b.y) continue
            val t = if (b.y == a.y) 0.0 else (y - a.y) / (b.y - a.y)
            val cx = a.x + (b.x - a.x) * t
            val cw = (a.w + (b.w - a.w) * t).coerceAtLeast(12.0) // finger slack
            if (x >= cx - cw / 2 && x <= cx + cw / 2) return band
        }
    }
    return null
}

private fun describeLoom(loom: DayLoomLayout.Loom, enabledSourceCount: Int): String {
    if (loom.totalFlowed == 0) return "Nothing flowed from your $enabledSourceCount sources this day."
    val read = loom.bands.filter { it.consumed }.sortedByDescending { it.read }
    val readLine = if (read.isEmpty()) {
        "none of it read"
    } else {
        "${loom.totalRead} read: " + read.joinToString(", ") { "${it.topic.placeholderLabel} ${it.read}" }
    }
    return "Day loom. ${loom.totalFlowed} stories flowed from your $enabledSourceCount sources; $readLine. " +
        "Tap a stream for its counts."
}
