package xyz.mdhv.riverwip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.DisplayFontFamily
import xyz.mdhv.riverwip.design.Tokens

// Greeked newsprint for the backdrop — deliberately meaningless text (the
// mock's own lorem), never real headlines: the splash must not editorialize.
private const val GREEK_TOP = "lentesque est eu facilisis 4563 ac vehicula quam"
private const val GREEK_DISPLAY =
    "mollis purus. rhoncus pretium vehicula quam. Nulla venenatis. Vivamus " +
        "ac turpis hendrerit lectus. magna et scelerisque et diam justo. ultricies enim, non augue quis."
private const val GREEK_SMALL =
    "Suspendisse aliquam mollis purus. Etiam nibh tortor, rhoncus pretium lectus id, cursus " +
        "vehicula quam. Nulla vulputate ultricies venenatis. Vivamus purus leo, interdum ac " +
        "turpis hendrerit, lacinia eleifend lectus. Nullam sit amet urna vitae."

/**
 * The splash (owner's Splash mock, 2026-07): the wordmark set into a page of
 * oversized newsprint — a top rule and a partial line, big serif display text
 * bleeding off the left, and a block of fine print below, all greeked. Drawn,
 * not shipped as an image, so it adapts to both themes and costs no asset. The
 * newsprint sits at a legible grey; the "Nooz" wordmark rides over a soft
 * highlight so it reads cleanly.
 */
@Composable
fun SplashScreen() {
    val paperGrey = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.24f)
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Column(Modifier.fillMaxSize().padding(vertical = Tokens.Spacing.lg)) {
            Text(
                text = GREEK_TOP,
                fontFamily = DisplayFontFamily,
                fontSize = 22.sp,
                color = paperGrey,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().offset(x = (-16).dp),
            )
            Spacer(Modifier.height(Tokens.Spacing.xs))
            HorizontalDivider(color = paperGrey)
            Text(
                text = GREEK_DISPLAY,
                fontFamily = DisplayFontFamily,
                fontSize = 58.sp,
                lineHeight = 66.sp,
                color = paperGrey,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(x = (-20).dp)
                    .padding(top = Tokens.Spacing.md),
            )
            Text(
                text = GREEK_SMALL,
                fontFamily = DisplayFontFamily,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = paperGrey,
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.Spacing.sm),
            )
        }
        // The owner's decided name (design mocks, 2026-07), over a soft highlight.
        Text(
            "Nooz",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 46.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    RoundedCornerShape(Tokens.Radius.sm),
                )
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xxs),
        )
    }
}
