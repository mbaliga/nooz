package xyz.mdhv.riverwip.feature.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import xyz.mdhv.riverwip.design.R as DesignR

/**
 * Share as a newspaper clipping (owner's spec, 2026-07). The Share action
 * renders the headline as a paper-white masthead card — the "Nooz" wordmark, a
 * double rule, the real title set in the Hyle Print serif, and the real
 * source · author · date — then hands the PNG to the system chooser. Everything
 * on the card is real: the app's own name, the article's own title/source/
 * author, and today's date; nothing is invented. If the image can't be written
 * for any reason it falls back to a plain-text share, so Share never dead-ends.
 */
object NewspaperShare {

    fun share(context: Context, title: String, source: String?, author: String?, url: String?) {
        val uri = runCatching {
            val bitmap = render(context, title, source, author)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "nooz-clipping.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

        val caption = buildString {
            append(title)
            if (!url.isNullOrBlank()) append('\n').append(url)
            append("\n— clipped with Nooz")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, caption)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    private const val W = 1080
    private const val MARGIN = 84f
    private val PAPER = Color.rgb(0xF7, 0xF5, 0xEF)
    private val INK = Color.rgb(0x1A, 0x1A, 0x1A)
    private val MUTED = Color.rgb(0x6B, 0x66, 0x5E)

    private fun render(context: Context, title: String, source: String?, author: String?): Bitmap {
        val serif = ResourcesCompat.getFont(context, DesignR.font.hyle_print_medium) ?: Typeface.SERIF
        val serifHeavy = ResourcesCompat.getFont(context, DesignR.font.hyle_print_heavy)
            ?: Typeface.create(serif, Typeface.BOLD)
        val sans = ResourcesCompat.getFont(context, DesignR.font.hyle_grotesk_classic_medium) ?: Typeface.SANS_SERIF

        val contentW = (W - 2 * MARGIN).toInt()

        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; typeface = serif; textSize = 78f
        }
        val headline = StaticLayout.Builder
            .obtain(title.trim(), 0, title.trim().length, headlinePaint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1f)
            .setIncludePad(false)
            .build()

        // Fixed vertical budget for masthead, rules, byline, and footer.
        val mastheadBlock = 200f
        val bylineBlock = 120f
        val footerBlock = 96f
        val height = (MARGIN + mastheadBlock + headline.height + bylineBlock + footerBlock + MARGIN).toInt()

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(PAPER)

        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; strokeWidth = 3f }
        val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; strokeWidth = 1.5f }

        var y = MARGIN

        // Masthead wordmark.
        val mast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; typeface = serifHeavy; textSize = 128f; textAlign = Paint.Align.CENTER
        }
        y += 116f
        canvas.drawText("Nooz", W / 2f, y, mast)
        y += 40f
        canvas.drawLine(MARGIN, y, W - MARGIN, y, rule); y += 11f
        canvas.drawLine(MARGIN, y, W - MARGIN, y, hairline); y += 42f

        // Headline.
        canvas.save(); canvas.translate(MARGIN, y); headline.draw(canvas); canvas.restore()
        y += headline.height + 34f
        canvas.drawLine(MARGIN, y, W - MARGIN, y, hairline); y += 52f

        // Byline: source left, author right (the Paper mock's layout).
        val bylineLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED; typeface = sans; textSize = 32f; letterSpacing = 0.1f
        }
        val bylineRight = Paint(bylineLeft).apply { textAlign = Paint.Align.RIGHT }
        source?.trim()?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it.uppercase(Locale.getDefault()), MARGIN, y, bylineLeft)
        }
        author?.trim()?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it.uppercase(Locale.getDefault()), W - MARGIN, y, bylineRight)
        }

        // Footer: date + provenance, centred.
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED; typeface = sans; textSize = 28f; textAlign = Paint.Align.CENTER; letterSpacing = 0.08f
        }
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        canvas.drawText("$date  ·  clipped with Nooz", W / 2f, height - MARGIN, footer)

        return bmp
    }
}
