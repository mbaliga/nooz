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
 *  - one-finger horizontal drag: the paper follows the finger and slides as a
 *    rigid sheet ([onDrag] per-frame, [onDragEnd] to settle) — right reveals
 *    the stand, left reveals settings;
 *  - two-finger vertical drag: a sliding brightness scale (in-app window
 *    brightness — needs no permission);
 *  - two-finger horizontal flick: next theme tint (one flick, one step).
 *
 * Two-finger handling watches the Initial pass and consumes its events so the
 * article's own vertical scroll (and the one-finger slide) don't fight the
 * drag. Every gesture here has a visible, accessible equivalent elsewhere
 * (back = system back; theme and brightness live in Settings) — gestures are
 * shortcuts, never the only door.
 */
fun Modifier.readerGestures(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
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
                    // so neither the list nor the slide inherits a phantom drag.
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
    .pointerInput(Unit) {
        // One-finger horizontal slide: forward each frame's delta so the sheet
        // tracks the finger, then settle on release.
        detectHorizontalDragGestures(
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
        ) { change, dragAmount ->
            onDrag(dragAmount)
            change.consume()
        }
    }
