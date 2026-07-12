package xyz.mdhv.riverwip.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import xyz.mdhv.riverwip.model.ListDensity
import kotlin.math.roundToInt

/**
 * The Stand's and Clippings' shared density control (owner's #1): a
 * horizontal slider across the four [ListDensity] steps, detail through big
 * tiles. Hidden by the caller in immersive mode, where a pinch gesture on the
 * list itself steps density instead — see [pinchDensityDelta].
 */
@Composable
fun DensitySlider(
    density: ListDensity,
    onDensityChange: (ListDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = ListDensity.entries
    Column(modifier.fillMaxWidth()) {
        Slider(
            value = density.ordinal.toFloat(),
            onValueChange = { raw ->
                val index = raw.roundToInt().coerceIn(0, steps.lastIndex)
                if (steps[index] != density) onDensityChange(steps[index])
            },
            valueRange = 0f..(steps.size - 1).toFloat(),
            steps = steps.size - 2,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "View density: ${density.label}" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (d in steps) {
                Text(
                    d.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == density) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Maps an accumulated pinch zoom factor to a density step, once it crosses a
 * deliberate threshold (owner's #1: pinch in immersive mode, where the slider
 * itself is hidden). Pinching out (zoom > 1, spreading fingers) moves toward
 * more detail; pinching in moves toward more compact — the same sense as
 * pinch-to-zoom everywhere else. Returns 0 until the accumulated gesture is
 * decisive, so a single small jitter doesn't flip the view.
 */
fun pinchDensityDelta(accumulatedZoom: Float): Int = when {
    accumulatedZoom > 1.15f -> -1
    accumulatedZoom < 0.87f -> 1
    else -> 0
}
