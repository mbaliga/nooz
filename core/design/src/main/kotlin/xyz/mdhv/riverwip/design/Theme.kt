package xyz.mdhv.riverwip.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.ThemeMode

/**
 * Extended token slots that Material3's [androidx.compose.material3.ColorScheme]
 * has no home for (provenance hues, hairlines). Provenance carries meaning only
 * when paired with a non-colour channel — see [Tokens].
 */
data class ExtendedColors(
    val provenanceNative: Color,
    val provenanceCloud: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val textFaint: Color,
)

private val DarkExtended = ExtendedColors(
    provenanceNative = Tokens.Color.provenanceNative,
    provenanceCloud = Tokens.Color.provenanceCloud,
    hairline = Tokens.Color.borderHairline,
    hairlineStrong = Tokens.Color.borderStrong,
    textFaint = Tokens.Color.textFaint,
)

// Provenance hues stay identical in both themes (the convention never inverts);
// they read fine on paper because they always pair with a label/icon anyway.
private val PaperExtended = ExtendedColors(
    provenanceNative = Tokens.Color.provenanceNative,
    provenanceCloud = Tokens.Color.provenanceCloud,
    hairline = Tokens.Palette.paperHairline,
    hairlineStrong = Tokens.Palette.paperHairlineStrong,
    textFaint = Tokens.Palette.paperInkFaint,
)

private val LocalExtendedColors = staticCompositionLocalOf { PaperExtended }

/** Access extended tokens: `AppTheme.extended.provenanceNative`. */
object AppTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

// The dark theme: Hyle dark-first values, unchanged — now the ThemeMode.DARK
// option rather than the default. `background` is the raised surface
// (#121212-class), never pure black (halation).
private val DarkScheme = darkColorScheme(
    primary = Tokens.Color.actionPrimary,
    onPrimary = Tokens.Color.onActionPrimary,
    secondary = Tokens.Color.actionPrimaryActive,
    onSecondary = Tokens.Color.onActionPrimary,
    background = Tokens.Color.backgroundSurface,
    onBackground = Tokens.Color.textPrimary,
    surface = Tokens.Color.backgroundSurface,
    onSurface = Tokens.Color.textPrimary,
    surfaceVariant = Tokens.Palette.fieldNear,
    onSurfaceVariant = Tokens.Color.textSecondary,
    outline = Tokens.Color.borderStrong,
    error = Tokens.Palette.signalDanger,
)

// The paper theme (owner's design mocks, 2026-07, now the default): warm
// near-white newsprint, near-black ink, grey bylines. Ink itself is the accent
// — the mocks spend colour only on the river's streams and provenance dots.
private val PaperScheme = lightColorScheme(
    primary = Tokens.Palette.paperInk,
    onPrimary = Tokens.Palette.paperField,
    secondary = Tokens.Palette.paperInkDim,
    onSecondary = Tokens.Palette.paperField,
    background = Tokens.Palette.paperField,
    onBackground = Tokens.Palette.paperInk,
    surface = Tokens.Palette.paperField,
    onSurface = Tokens.Palette.paperInk,
    surfaceVariant = Tokens.Palette.paperRaised,
    onSurfaceVariant = Tokens.Palette.paperInkDim,
    outline = Tokens.Palette.paperHairlineStrong,
    outlineVariant = Tokens.Palette.paperHairline,
    error = Tokens.Palette.paperSignalDanger,
)

@Composable
fun RiverTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    readerFont: ReaderFont = ReaderFont.SANS,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtended else PaperExtended) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else PaperScheme,
            typography = riverTypography(readerFont),
            content = content,
        )
    }
}
