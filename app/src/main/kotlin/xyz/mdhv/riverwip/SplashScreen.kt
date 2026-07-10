package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.DisplayFontFamily
import xyz.mdhv.riverwip.design.Tokens

// Greeked newsprint for the backdrop — deliberately meaningless text (the
// mock's own lorem), never real headlines: the splash must not editorialize.
private const val GREEK =
    "Pellentesque est eu facilisis mollis purus rhoncus pretium vehicula quam nulla " +
        "venenatis vivamus ac turpis hendrerit lectus magna et scelerisque diam justo " +
        "ultricies enim non congue quis suspendisse aliquam mollis purus etiam nibh " +
        "tortor rhoncus pretium lectus id cursus vehicula quam nulla vulputate ultricies " +
        "venenatis vivamus purus leo interdum ac turpis hendrerit lacinia eleifend lectus"

/**
 * The splash (owner's Splash mock, 2026-07): the wordmark set into a faded
 * page of oversized newsprint. Drawn, not shipped as an image — the backdrop
 * is the greeked text above at low alpha, so it adapts to both themes and
 * costs no asset.
 */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = GREEK,
            fontFamily = DisplayFontFamily,
            fontSize = 64.sp,
            lineHeight = 78.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (-24).dp)
                .padding(vertical = Tokens.Spacing.xl),
            softWrap = true,
        )
        Text(
            // The owner's decided name (design mocks, 2026-07).
            "Nooz",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 44.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
