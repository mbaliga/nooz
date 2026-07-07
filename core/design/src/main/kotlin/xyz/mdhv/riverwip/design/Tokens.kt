package xyz.mdhv.riverwip.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The design token contract.
 *
 * Every surface specifies against these named tokens — never hardcoded literals.
 * Behaviour must not depend on the token *values*: surfaces are re-skinned by
 * changing values here alone.
 *
 * Values are sourced from the **Hyle Design System** (`tokens/*.json`, the
 * ecosystem's cross-platform source of truth). Dark-first; the single north-star
 * accent is violet `#8E7BFF`. The provenance convention is fixed and never
 * inverted: warm radium (`#C7EF9E`) = on-device, cold cyan (`#35E0FF`) = cloud
 * — and per the colour-vision constraint, provenance always pairs with a
 * non-colour channel (icon/label), never colour alone.
 */
object Tokens {

    /** Raw palette (ARGB), mirrored from Hyle `color.palette.*`. */
    object Palette {
        // Field — canvas / backdrops. `void` (pure black) is art-only.
        val fieldVoid = Color(0xFF000000)
        val fieldNear = Color(0xFF0A0809)
        val fieldRaised = Color(0xFF121212) // UI surfaces sit here, never pure black (halation).
        val fieldDeep = Color(0xFF050009)

        // Ink — text, warm off-white at descending opacity.
        val inkPure = Color(0xFFECE8E4)
        val inkFull = Color(0xEBECE8E4)
        val inkDim = Color(0x6BECE8E4)
        val inkFaint = Color(0x2EECE8E4)

        // Accent — spend scarce light here.
        val accentViolet = Color(0xFF8E7BFF)
        val accentVioletBright = Color(0xFFA593FF)
        val accentVioletDeep = Color(0xFF7867E6)

        // Provenance — ecosystem convention. Never invert.
        val provenanceNative = Color(0xFFC7EF9E) // radium yellow-green = on-device
        val provenanceCloud = Color(0xFF35E0FF)  // cold cyan = cloud

        val hairlineDefault = Color(0x14FFFFFF)
        val hairlineStrong = Color(0x24FFFFFF)
        val glassPane = Color(0x850A0809)

        // Feedback. NOTE (colour-vision constraint, §2): the primary user is
        // red–green colourblind. No meaning may ride on danger-vs-success colour
        // alone — these are always paired with icon + text. Kept for surfaces
        // that already carry a non-colour signal.
        val signalDanger = Color(0xFFE5564B)
        val signalWarning = Color(0xFFE0941A)
        val signalSuccess = Color(0xFF5BBF7A)
    }

    /** Semantic colour roles (mirrored from Hyle `color.{text,background,...}`). */
    object Color {
        val textPrimary = Palette.inkFull
        val textSecondary = Palette.inkDim
        val textFaint = Palette.inkFaint
        val textInverse = Palette.fieldNear
        val textAccent = Palette.accentViolet

        val backgroundField = Palette.fieldVoid   // art / river canvas only
        val backgroundSurface = Palette.fieldRaised // all readable UI
        val backgroundGlass = Palette.glassPane

        val borderHairline = Palette.hairlineDefault
        val borderStrong = Palette.hairlineStrong
        val borderFocus = Palette.accentViolet

        val actionPrimary = Palette.accentViolet
        val actionPrimaryHover = Palette.accentVioletBright
        val actionPrimaryActive = Palette.accentVioletDeep
        val onActionPrimary = Palette.fieldNear

        val provenanceNative = Palette.provenanceNative
        val provenanceCloud = Palette.provenanceCloud
    }

    /** Spacing scale (dp), mirrored from Hyle `spacing.*`. */
    object Spacing {
        val none = 0.dp
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 20.dp
        val xl = 24.dp
        val xxl = 32.dp
        val xxxl = 40.dp
        val huge = 48.dp
        val giant = 64.dp
    }

    /** Corner radii (dp), mirrored from Hyle `radius.*`. */
    object Radius {
        val none = 0.dp
        val sm = 4.dp
        val md = 8.dp
        val lg = 12.dp
        val xl = 16.dp
        val full = 9999.dp
    }

    /** Border widths (dp). */
    object Border {
        val thin = 1.dp
        val thick = 2.dp
    }

    /** Motion durations (ms) and easing, mirrored from Hyle `motion.*`. */
    object Motion {
        const val instantMs = 120
        const val calmMs = 300 // default state change — weight, not bounce
        const val paneMs = 420
    }
}
