package xyz.mdhv.riverwip.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        provenanceNative = Tokens.Color.provenanceNative,
        provenanceCloud = Tokens.Color.provenanceCloud,
        hairline = Tokens.Color.borderHairline,
        hairlineStrong = Tokens.Color.borderStrong,
        textFaint = Tokens.Color.textFaint,
    )
}

/** Access extended tokens: `AppTheme.extended.provenanceNative`. */
object AppTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

// Dark-first. The scheme is built entirely from tokens; note `background` is the
// raised surface (#121212-class), never pure black — pure black (fieldVoid) is
// reserved for the river's art canvas.
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

// A light scheme exists for completeness; the app ships dark-first.
private val LightScheme = lightColorScheme(
    primary = Tokens.Color.actionPrimaryActive,
    background = Tokens.Palette.inkPure,
    surface = Tokens.Palette.inkPure,
    onBackground = Tokens.Palette.fieldNear,
    onSurface = Tokens.Palette.fieldNear,
)

@Composable
fun RiverTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalExtendedColors provides LocalExtendedColors.current) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
