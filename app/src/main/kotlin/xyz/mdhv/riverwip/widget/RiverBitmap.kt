package xyz.mdhv.riverwip.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import xyz.mdhv.riverwip.model.RiverPicture
import xyz.mdhv.riverwip.model.TopicPalette
import xyz.mdhv.riverwip.model.WeekRiver
import kotlin.math.max

/**
 * Draws the river widget's picture: the week's supply along the top edge, the
 * reader's intake along the bottom, a band per topic between them. The
 * geometry is [RiverPicture]'s (pure, unit-tested); this only paints it.
 *
 * Topic colours come from the CVD-verified [TopicPalette], the same the Loom
 * uses, and only where the layout prints a legend naming every band (the large
 * one): there each swatch sits beside its topic's name. Everywhere else the
 * bands are drawn in one neutral grey, alternating in strength so neighbours
 * stay apart, because a hue with no name beside it would be colour carrying
 * meaning on its own. The pinch is the argument either way, and the alt text
 * carries every number. Each segment gets a darker outline so the palette's
 * pale catch-all grey still reads on the day surface. Drawn on transparency,
 * so it sits on the widget's own day or night surface, and always left to
 * right, like the Loom's canvas.
 */
internal object RiverBitmap {

    /** Thickness of the two edges, as a fraction of the picture's height. */
    private const val EDGE = 0.18f

    /** Band opacity between the edges: the edges are the data, the bands join them. */
    private const val BAND_ALPHA = 140

    /** Outline of an empty reading edge, and the neutral bands: mid grey, at least 3:1 on both widget surfaces. */
    private const val EMPTY_EDGE = 0xFF8A8A86.toInt()

    /** The neutral bands' two strengths, alternating so neighbouring bands stay apart. */
    private const val NEUTRAL_BAND_ALPHA = 150
    private const val NEUTRAL_BAND_ALPHA_ALT = 80

    /**
     * Draws [river] at [width] by [height] pixels: in topic colours when
     * [coloured], which the caller asks for only where a legend names every
     * band, otherwise in neutral grey.
     */
    fun draw(river: WeekRiver, width: Int, height: Int, coloured: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val edge = max(2f, h * EDGE)
        val bottom = h - edge
        val mid = h / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, h / 90f)
        }
        val path = Path()

        for ((index, band) in RiverPicture.layout(river).withIndex()) {
            val color = if (coloured) TopicPalette.colorFor(band.topic) else EMPTY_EDGE
            val bandAlpha = when {
                coloured -> BAND_ALPHA
                index % 2 == 0 -> NEUTRAL_BAND_ALPHA
                else -> NEUTRAL_BAND_ALPHA_ALT
            }
            val x0 = band.topStart * w
            val x1 = band.topEnd * w
            val b0 = band.bottomStart * w
            val b1 = band.bottomEnd * w

            if (band.topWidth > 0f) {
                path.reset()
                path.moveTo(x0, edge)
                path.lineTo(x1, edge)
                path.cubicTo(x1, mid, b1, mid, b1, bottom)
                path.lineTo(b0, bottom)
                path.cubicTo(b0, mid, x0, mid, x0, edge)
                path.close()
                fill.color = color
                fill.alpha = bandAlpha
                canvas.drawPath(path, fill)

                fill.alpha = 255
                canvas.drawRect(x0, 0f, x1, edge, fill)
                outline.color = darker(color)
                canvas.drawRect(x0, 0f, x1, edge, outline)
            }
            if (band.bottomWidth > 0f) {
                fill.color = color
                fill.alpha = 255
                canvas.drawRect(b0, bottom, b1, h, fill)
                outline.color = darker(color)
                canvas.drawRect(b0, bottom, b1, h, outline)
            }
        }

        if (river.read == 0) {
            // Nothing read yet: the bottom edge is drawn empty, not omitted, so
            // the picture still says where reading would land.
            val dashed = Paint(outline).apply {
                color = EMPTY_EDGE
                pathEffect = DashPathEffect(floatArrayOf(edge / 2f, edge / 2f), 0f)
            }
            canvas.drawRect(outline.strokeWidth, bottom, w - outline.strokeWidth, h - outline.strokeWidth, dashed)
        }
        return bitmap
    }

    private fun darker(color: Int): Int = Color.rgb(
        (Color.red(color) * 0.7f).toInt(),
        (Color.green(color) * 0.7f).toInt(),
        (Color.blue(color) * 0.7f).toInt(),
    )
}
