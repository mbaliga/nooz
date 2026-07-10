package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The reading room's gestures (owner's spec, 2026-07):
 *  - one-finger horizontal swipe: right = back to the stand, left = settings;
 *  - two-finger vertical drag: a sliding brightness scale (in-app window
 *    brightness — needs no permission);
 *  - two-finger horizontal flick: next theme tint (one flick, one step).
 *
 * Two-finger handling watches the Initial pass and consumes its events so the
 * article's own vertical scroll doesn't fight the drag. Every gesture here has
 * a visible, accessible equivalent elsewhere (back = system back; theme and
 * brightness live in Settings/quick settings) — gestures are shortcuts, never
 * the only door.
 */
fun Modifier.readerGestures(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onThemeFlick: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        // Two-finger gestures, seen before children (Initial pass).
        val flickThresholdPx = 90.dp.toPx()
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            var twoFinger = false
            var totalDx = 0f
            var flicked = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    twoFinger = true
                    val dx = pressed.sumOf { it.positionChange().x.toDouble() }.toFloat() / pressed.size
                    val dy = pressed.sumOf { it.positionChange().y.toDouble() }.toFloat() / pressed.size
                    if (abs(dy) > abs(dx)) {
                        // Sliding scale: full screen height ≈ full brightness range.
                        onBrightnessDelta(-dy / size.height)
                    } else {
                        totalDx += dx
                        if (!flicked && abs(totalDx) > flickThresholdPx) {
                            flicked = true
                            onThemeFlick()
                        }
                    }
                    event.changes.forEach { it.consume() }
                } else if (twoFinger) {
                    // Finger lifted mid-gesture: keep consuming until all are up
                    // so the list doesn't inherit a phantom scroll.
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
    .pointerInput(Unit) {
        // One-finger horizontal navigation.
        val navThresholdPx = 110.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total > navThresholdPx -> onSwipeRight()
                    total < -navThresholdPx -> onSwipeLeft()
                }
            },
        ) { _, dragAmount ->
            total += dragAmount
        }
    }
