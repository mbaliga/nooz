package xyz.mdhv.riverwip.feature.reader

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import xyz.mdhv.riverwip.model.ImageStyle

/**
 * A feed's own image (owner's ask, 2026-07), styled per the reader's choice:
 * plain colour, a tasteful duotone black & white (not a flat desaturation —
 * see [TastefulBlackAndWhite]), or a halftone dot-print stylization in the
 * app's own newspaper-column ink, using the same jittered-dot technique
 * `core/design`'s `PaperGrain` already uses for its texture, adapted here to
 * an image's own per-cell luminance rather than noise (see [HalftoneImage]).
 *
 * Renders nothing at all — not even reserved space — when there's no image
 * to show: a null/blank [imageUrl], or a source's own feed having declared
 * this item adult/explicit while [hideNsfw] is on. That check is never this
 * app's own judgment; see `AppSettings.hideNsfwImages`'s doc for exactly
 * what "declared" means here.
 */
@Composable
fun FeedImage(
    imageUrl: String?,
    declaredNsfw: Boolean,
    hideNsfw: Boolean,
    style: ImageStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (imageUrl.isNullOrBlank() || (hideNsfw && declaredNsfw)) return

    when (style) {
        ImageStyle.COLOR -> AsyncImage(
            model = crossfadeRequest(imageUrl),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        ImageStyle.BLACK_AND_WHITE -> AsyncImage(
            model = crossfadeRequest(imageUrl),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(TastefulBlackAndWhite),
            modifier = modifier,
        )
        ImageStyle.HALFTONE -> HalftoneImage(imageUrl = imageUrl, contentDescription = contentDescription, modifier = modifier)
    }
}

@Composable
private fun crossfadeRequest(imageUrl: String) =
    ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build()

/**
 * Grayscale (standard BT.601 luminance weights) with a mild contrast boost
 * and a slight warm-tone lean — a flat, unweighted desaturation reads as
 * muddy and washed out, which is exactly the "ugly BW filter" the owner
 * asked to avoid; this reads closer to a newspaper's own halftone-photo
 * reproduction instead. Handcrafted as one 4x5 matrix (contrast folded into
 * the luminance weights, the warm tone into each channel's own translation
 * term) rather than chaining matrix multiplications, so the numbers here
 * are the whole story — no hidden composition to reverse-engineer later.
 */
private val TastefulBlackAndWhite = ColorMatrix(
    floatArrayOf(
        0.34385f, 0.67505f, 0.1311f, 0f, -13.2f,
        0.34385f, 0.67505f, 0.1311f, 0f, -17.2f,
        0.34385f, 0.67505f, 0.1311f, 0f, -23.2f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** How many dot columns the halftone grid samples across — rows follow from the source image's own aspect ratio. */
private const val HALFTONE_COLUMNS = 36

/**
 * The image reproduced as newsprint would: no photo drawn at all, only a
 * grid of dots whose radius tracks that cell's own darkness — the app's
 * existing paper-grain speckle technique (a seeded jittered dot field on a
 * `Canvas`), driven by the image's downsampled luminance instead of noise.
 * Decodes the source once into a plain software [android.graphics.Bitmap]
 * (Coil's own image loader, off the main thread — [produceState] runs its
 * block as a coroutine), then leans on [android.graphics.Bitmap.createScaledBitmap]
 * to do the per-cell averaging: scaling *down* to the dot grid's own
 * resolution is exactly a box-filtered average per output pixel, so no
 * separate luminance-sampling pass is needed.
 */
@Composable
private fun HalftoneImage(imageUrl: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, imageUrl) {
        value = decodeSoftwareBitmap(context, imageUrl)
    }

    val source = bitmap ?: return // still loading, or the decode failed -- reserve nothing, same as no image at all

    val cols = HALFTONE_COLUMNS
    val rows = (cols / (source.width.toFloat() / source.height.toFloat())).toInt().coerceAtLeast(1)
    val cells = remember(source, cols, rows) { android.graphics.Bitmap.createScaledBitmap(source, cols, rows, true) }

    val ink = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background
    val description = contentDescription
    val canvasModifier = if (description != null) {
        modifier.semantics { this.contentDescription = description }
    } else {
        modifier
    }

    Canvas(canvasModifier) {
        drawRect(paper)
        val cellW = size.width / cols
        val cellH = size.height / rows
        val maxRadius = minOf(cellW, cellH) / 2f
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val pixel = cells.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                val radius = (1f - luminance) * maxRadius * 0.95f
                if (radius > 0.5f) {
                    drawCircle(
                        color = ink,
                        radius = radius,
                        center = Offset(x * cellW + cellW / 2f, y * cellH + cellH / 2f),
                    )
                }
            }
        }
    }
}

/**
 * Decode [imageUrl] into a plain software [android.graphics.Bitmap] (via
 * Coil's own loader, `allowHardware(false)` so its pixels are readable), or
 * null on any failure. Pulled out of [HalftoneImage]'s `produceState` block
 * as its own plain suspend function — not just for readability: Compose
 * lint's `ProduceStateDoesNotAssignValue` check statically looks for a
 * direct `value = <expr>` in the producer lambda, and doesn't reliably see
 * one buried inside a `try/catch` expression body. `value = decodeSoftwareBitmap(...)`
 * is unambiguous either way.
 */
private suspend fun decodeSoftwareBitmap(context: Context, imageUrl: String): android.graphics.Bitmap? = try {
    val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
    drawable?.let {
        val w = it.intrinsicWidth.coerceAtLeast(1)
        val h = it.intrinsicHeight.coerceAtLeast(1)
        val decoded = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(decoded)
        it.setBounds(0, 0, w, h)
        it.draw(canvas)
        decoded
    }
} catch (_: Exception) {
    null
}
