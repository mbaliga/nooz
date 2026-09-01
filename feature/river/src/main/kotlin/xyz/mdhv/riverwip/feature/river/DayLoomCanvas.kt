package xyz.mdhv.riverwip.feature.river

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
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
    val ghostColor = MaterialTheme.colorScheme.onSurfaceVariant
    val w = DayLoomLayout.W.toFloat()
    val h = DayLoomLayout.H.toFloat()

    // Pin the label overlay to LTR so its BiasAlignment matches the Canvas's
    // always-LTR coordinate space (DrawScope never mirrors) — otherwise SOURCE
    // and CONSUMPTION flip to the wrong side in an RTL locale.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = description
                // The Loom is the app's centrepiece and it was, to a screen
                // reader, a single silent node: a Robolectric semantics dump
                // showed its whole action list as
                // [SetTextSubstitution, ShowTextSubstitution,
                //  ClearTextSubstitution, GetTextLayoutResult] — no click,
                // nothing custom — while its own description ended "Tap a
                // stream for its counts." The app was instructing a gesture it
                // would not accept, and the per-tube counts existed nowhere
                // else in speech.
                //
                // One action per stream, carrying the numbers in the label
                // itself: the custom-action menu is read aloud in sequence, so
                // the answer has to be *in* the item rather than behind
                // selecting it.
                customActions = loom.bands.map { band ->
                    CustomAccessibilityAction(
                        label = "${band.topic.placeholderLabel}: ${band.flowed} flowed, ${band.read} read",
                        action = { selected = band; true },
                    )
                }
                selected?.let {
                    stateDescription = "${it.topic.placeholderLabel} selected: ${it.flowed} flowed, ${it.read} read"
                }
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Composite offscreen so the DstIn fade mask erases only the
                // tubes (not the paper behind), fading each end to transparent.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
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
            // Nothing read yet today: the bottom half would otherwise be blank
            // (every band pinches to a point at the waist and stops — brief §1's
            // honesty, but reading as broken rather than "zero"). A dotted ghost
            // funnel stands in for the fan reading would draw (owner's #2).
            if (loom.totalRead == 0) {
                drawGhostFan(sx, sy, ghostColor)
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
            // Fade the tube ends to transparent at the very top and bottom, so
            // every stream dissolves into the page (owner's Viz: no hard stubs).
            val fade = 0.11f
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    fade to Color.Black,
                    1f - fade to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
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
 * The empty-consumption placeholder (owner's #2): a dot-matrix funnel from the
 * waist to the bottom edge, widening the way a real read-fan would, at low
 * alpha so it reads as absence rather than data. Echoes the globe's own
 * dotted-landmass idiom elsewhere in the app — "nothing here yet" is drawn the
 * same way twice, not invented fresh per screen.
 */
private fun DrawScope.drawGhostFan(sx: Float, sy: Float, color: Color) {
    val waistY = (DayLoomLayout.WAIST_Y * sy).toFloat()
    val bottomY = (DayLoomLayout.H * sy).toFloat()
    val centerX = (DayLoomLayout.W / 2 * sx).toFloat()
    val halfWidthAtBottom = (70.0 * sx).toFloat()
    val dotRadius = (1.6 * sx).toFloat().coerceAtLeast(1f)
    val stepX = (10.0 * sx).toFloat().coerceAtLeast(6f)
    val stepY = (14.0 * sy).toFloat().coerceAtLeast(8f)
    val dotColor = color.copy(alpha = 0.35f)

    var y = waistY + stepY
    while (y < bottomY) {
        val t = ((y - waistY) / (bottomY - waistY)).coerceIn(0f, 1f)
        val half = halfWidthAtBottom * t
        var x = centerX - half
        while (x <= centerX + half) {
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
            x += stepX
        }
        y += stepY
    }
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

/** How many supply topics the spoken summary names before summarising the tail. */
private const val SPOKEN_SUPPLY_TOPICS = 5

private fun describeLoom(loom: DayLoomLayout.Loom, enabledSourceCount: Int): String {
    if (loom.totalFlowed == 0) return "Nothing flowed from your $enabledSourceCount sources this day."

    // The supply side used to be a single grand total, because this only ever
    // enumerated `bands.filter { it.consumed }` — topics with at least one read.
    // A topic that flooded the feed and was never opened was therefore never
    // named, which is precisely the omission this whole screen exists to show.
    val bySupply = loom.bands.sortedByDescending { it.flowed }
    val named = bySupply.take(SPOKEN_SUPPLY_TOPICS).filter { it.flowed > 0 }
    val rest = bySupply.drop(SPOKEN_SUPPLY_TOPICS).count { it.flowed > 0 }
    val flowedLine = buildString {
        append("${loom.totalFlowed} stories flowed from your $enabledSourceCount sources")
        if (named.isNotEmpty()) {
            append(": ")
            append(named.joinToString(", ") { "${it.topic.placeholderLabel} ${it.flowed}" })
            if (rest > 0) append(", and $rest more topics")
        }
    }

    val read = loom.bands.filter { it.consumed }.sortedByDescending { it.read }
    val readLine = if (read.isEmpty()) {
        "You read none of it"
    } else {
        "You read ${loom.totalRead}: " + read.joinToString(", ") { "${it.topic.placeholderLabel} ${it.read}" }
    }

    // No longer "Tap a stream" — a screen reader cannot land a tap on one, and
    // the custom actions above are the route that actually exists for them.
    return "Day loom. $flowedLine. $readLine. Use actions for a single stream's counts."
}
