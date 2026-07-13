package xyz.mdhv.riverwip.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.model.PaperGrain
import kotlin.random.Random

private class GrainSpec(val cellDp: Float, val dotRadiusDp: Float, val alpha: Float)

private fun specFor(grain: PaperGrain): GrainSpec? = when (grain) {
    PaperGrain.NONE -> null
    PaperGrain.FINE -> GrainSpec(cellDp = 6f, dotRadiusDp = 0.5f, alpha = 0.05f)
    PaperGrain.COARSE -> GrainSpec(cellDp = 13f, dotRadiusDp = 1.3f, alpha = 0.07f)
}

// A fixed seed, not one rolled per call site: the grain is a property of the
// paper texture itself, not of any one article or clipping — it should look
// identical everywhere it's drawn, not re-rolled screen to screen.
private const val GRAIN_SEED = 5851L

/**
 * A speckled paper-grain texture (owner's ask, 2026-07): three fixed steps —
 * "simplistic and minimal," texture felt rather than fine-tuned with a
 * slider. Draws behind this node's own content (never over text), and only
 * recomputes its speckle positions when the size changes, not every frame —
 * [ink] should already read legibly against the surface it sits on; the
 * texture only ever darkens it a little. [grain] of [PaperGrain.NONE] is a
 * no-op so callers can apply this unconditionally.
 */
fun Modifier.paperGrain(grain: PaperGrain, ink: Color): Modifier {
    val spec = specFor(grain) ?: return this
    val color = ink.copy(alpha = spec.alpha)
    return this.drawWithCache {
        val cellPx = spec.cellDp.dp.toPx()
        val radiusPx = spec.dotRadiusDp.dp.toPx()
        val cols = (size.width / cellPx).toInt() + 2
        val rows = (size.height / cellPx).toInt() + 2
        val rnd = Random(GRAIN_SEED)
        val points = ArrayList<Offset>(cols * rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val jitterX = rnd.nextFloat() * cellPx
                val jitterY = rnd.nextFloat() * cellPx
                points += Offset(col * cellPx + jitterX, row * cellPx + jitterY)
            }
        }
        onDrawBehind {
            for (point in points) drawCircle(color, radiusPx, point)
        }
    }
}
