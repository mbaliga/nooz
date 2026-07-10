package xyz.mdhv.riverwip.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.TextScale

/**
 * Type scale. Sizes/weights mirror Hyle `typography.json`; the reading
 * families are the two Hyle actually ships — **Archivo** (sans, UI voice) and
 * **JetBrains Mono** (data voice), bundled as verified variable TTFs (OFL,
 * see `third_party/fonts/`) — plus a serif. Display/headline/title roles stay
 * serif: the newspaper voice of the wordmark, titles, and the loom. Hyle
 * defines no serif family, so the serif is the platform's (Noto Serif) until
 * the owner adds one to Hyle; swapping it in later touches this file alone.
 */
val DisplayFontFamily: FontFamily = FontFamily.Serif

/** Archivo — Hyle `font.family.sans`. Variable TTF; weight settings resolve per declaration. */
val HyleSans: FontFamily = FontFamily(
    Font(R.font.archivo_variable, weight = FontWeight.Normal),
    Font(R.font.archivo_variable, weight = FontWeight.Medium),
    Font(R.font.archivo_variable, weight = FontWeight.SemiBold),
)

/** JetBrains Mono — Hyle `font.family.mono`, the data voice. */
val HyleMono: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_variable, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono_variable, weight = FontWeight.Medium),
)

/**
 * Material3 [Typography] for the chosen reader font at the chosen text size
 * (the Settings mock's three-step scale). Feature code keeps using named roles.
 */
fun riverTypography(
    readerFont: ReaderFont = ReaderFont.SANS,
    textScale: TextScale = TextScale.STANDARD,
): Typography {
    val k = textScale.multiplier
    fun style(family: FontFamily, sizeSp: Int, weight: FontWeight, lineSp: Int) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (sizeSp * k).sp,
        lineHeight = (lineSp * k).sp,
    )

    val body = when (readerFont) {
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.SANS -> HyleSans
        ReaderFont.MONO -> HyleMono
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

/** Default typography — kept for previews and as the pre-settings-load fallback. */
val AppTypography = riverTypography()
