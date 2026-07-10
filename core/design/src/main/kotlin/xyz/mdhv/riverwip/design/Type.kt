package xyz.mdhv.riverwip.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.model.ReaderFont

/**
 * Type scale. Sizes/weights still mirror Hyle `typography.json`; the *families*
 * now follow the owner's design mocks (2026-07): display/headline/title roles
 * are always serif — the newspaper voice of the wordmark, article titles, and
 * the viz — while body/label default to the platform sans, switchable via the
 * Settings "Font" preference ([ReaderFont]).
 *
 * Platform families only ([FontFamily.Serif] is Noto Serif on modern Android):
 * bundling the mocks' exact typeface files would require font assets this
 * session cannot verify — same live-verification bar as feeds and model
 * mirrors. Swapping in the final files later touches this file alone.
 */
val DisplayFontFamily: FontFamily = FontFamily.Serif

private fun style(
    family: FontFamily,
    sizeSp: Int,
    weight: FontWeight = FontWeight.Normal,
    lineSp: Int = (sizeSp * 1.5f).toInt(),
) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = sizeSp.sp, lineHeight = lineSp.sp)

/** Material3 [Typography] for the chosen reader font. Feature code keeps using named roles. */
fun riverTypography(readerFont: ReaderFont = ReaderFont.SANS): Typography {
    val body = when (readerFont) {
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.SANS -> FontFamily.SansSerif
        ReaderFont.SYSTEM -> FontFamily.Default
    }
    return Typography(
        displayLarge = style(DisplayFontFamily, 34, FontWeight.Medium, 40),  // article headline (Paper mock)
        headlineMedium = style(DisplayFontFamily, 24, FontWeight.Medium, 30),
        headlineSmall = style(DisplayFontFamily, 21, FontWeight.Medium, 27), // list titles (Stand mock)
        titleLarge = style(DisplayFontFamily, 18, FontWeight.Medium, 24),
        titleMedium = style(body, 16, FontWeight.Medium, 22),
        bodyLarge = style(body, 16, FontWeight.Normal, 26),                  // reading body, relaxed
        bodyMedium = style(body, 14, FontWeight.Normal, 21),
        bodySmall = style(body, 12, FontWeight.Normal, 18),
        labelLarge = style(body, 14, FontWeight.Medium, 18),
        labelMedium = style(body, 12, FontWeight.Medium, 16),
        labelSmall = style(body, 11, FontWeight.Medium, 14),
    )
}

/** Default typography (sans body) — kept for previews and as the pre-settings-load fallback. */
val AppTypography = riverTypography(ReaderFont.SANS)
