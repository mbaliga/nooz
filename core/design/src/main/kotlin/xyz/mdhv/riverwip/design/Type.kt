package xyz.mdhv.riverwip.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale, mirrored from Hyle `typography.json`.
 *
 * The font *family* is the one deliberate swap-point: the final typeface arrives
 * with the RESERVED asset drop (Hyle ships Archivo; the brief's placeholder was
 * Plus Jakarta Sans). Until the font files land we bind to the platform sans so
 * nothing depends on a missing asset. Swapping the typeface is this one `val`.
 */
val AppFontFamily: FontFamily = FontFamily.SansSerif

private fun body(sizeSp: Int, weight: FontWeight = FontWeight.Normal, lineSp: Int = (sizeSp * 1.5f).toInt()) =
    TextStyle(fontFamily = AppFontFamily, fontWeight = weight, fontSize = sizeSp.sp, lineHeight = lineSp.sp)

/**
 * Material3 [Typography] populated from the token scale. Feature code should
 * prefer these named roles over ad-hoc sizes.
 */
val AppTypography = Typography(
    displayLarge = body(32, FontWeight.SemiBold, 38),   // font.size.3xl
    headlineMedium = body(24, FontWeight.SemiBold, 29), // 2xl
    headlineSmall = body(20, FontWeight.Medium, 26),    // xl
    titleLarge = body(18, FontWeight.Medium, 24),       // lg
    titleMedium = body(16, FontWeight.Medium, 22),      // md
    bodyLarge = body(16, FontWeight.Normal, 26),        // md, relaxed for reading
    bodyMedium = body(14, FontWeight.Normal, 21),       // sm
    bodySmall = body(12, FontWeight.Normal, 18),        // xs
    labelLarge = body(14, FontWeight.Medium, 18),
    labelMedium = body(12, FontWeight.Medium, 16),
    labelSmall = body(11, FontWeight.Medium, 14),       // label
)
