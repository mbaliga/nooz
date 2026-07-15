package xyz.mdhv.riverwip.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The one true "Nooz" wordmark. Every masthead — the Stand, the loom, the Edit
 * header, the splash — renders through this so the mark is identical everywhere
 * (owner's note: "use the same logo, not ad-hoc text"). Set in Playfair Display
 * Black, the high-contrast Didone face matching the owner's splash mock (not
 * Hyle Print — see [WordmarkFontFamily]); callers pass only size and ink so it
 * inherits the surrounding theme.
 */
@Composable
fun NoozWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = "Nooz",
        style = TextStyle(
            fontFamily = WordmarkFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            lineHeight = fontSize * 1.02f,
            letterSpacing = (-0.5).sp,
        ),
        color = color,
        modifier = modifier,
    )
}
