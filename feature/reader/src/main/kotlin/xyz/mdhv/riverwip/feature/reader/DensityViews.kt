package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.pinchDensityDelta
import xyz.mdhv.riverwip.model.ListDensity

/**
 * One compact, single-line row (owner's #1 "List" step, and Clippings' "in"):
 * headline only, no byline paragraph — as many items on screen as will fit.
 */
@Composable
fun CompactRow(title: String, topicColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp, end = Tokens.Spacing.sm)
                .size(8.dp)
                .clip(CircleShape)
                .background(topicColor),
        )
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One tile — a topic-coloured top edge, then the headline; [big] gives it more room and a subtitle line. */
@Composable
fun TileCard(title: String, subtitle: String?, topicColor: Color, big: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (big) 1.3f else 1f)
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(topicColor))
        Column(Modifier.padding(Tokens.Spacing.sm)) {
            Text(
                title,
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                maxLines = if (big) 4 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (big && subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Tokens.Spacing.xxs),
                )
            }
        }
    }
}

/** How many columns a density's tile grid uses. Detail/List aren't grids at all — callers only ask for the two tile steps. */
fun ListDensity.gridColumns(): Int = when (this) {
    ListDensity.BIG_TILES -> 2
    else -> 3
}

/** A grid of [TileCard]s at the given density. */
fun <T> LazyGridScope.densityTiles(
    items: List<T>,
    density: ListDensity,
    key: (T) -> Any,
    title: (T) -> String,
    subtitle: (T) -> String?,
    topicColor: (T) -> Color,
    onClick: (T) -> Unit,
) {
    items(items, key = key) { entry ->
        TileCard(
            title = title(entry),
            subtitle = subtitle(entry),
            topicColor = topicColor(entry),
            big = density == ListDensity.BIG_TILES,
            onClick = { onClick(entry) },
        )
    }
}

/** Standard grid shell shared by both screens' tile modes. */
@Composable
fun DensityGridShell(density: ListDensity, modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(density.gridColumns()),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Tokens.Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        content = content,
    )
}

/**
 * The immersive-mode replacement for the density slider (owner's #1): a
 * pinch anywhere on the list steps density one notch once the accumulated
 * gesture is decisive. [enabled] false (chrome visible) returns a no-op
 * modifier so callers can apply this unconditionally.
 */
@Composable
fun densityPinchModifier(density: ListDensity, onDensityChange: (ListDensity) -> Unit, enabled: Boolean): Modifier {
    var accum by remember { mutableFloatStateOf(1f) }
    if (!enabled) return Modifier
    return Modifier.pointerInput(density) {
        detectTransformGestures { _, _, zoom, _ ->
            accum *= zoom
            val delta = pinchDensityDelta(accum)
            if (delta != 0) {
                val next = (density.ordinal + delta).coerceIn(0, ListDensity.entries.lastIndex)
                onDensityChange(ListDensity.entries[next])
                accum = 1f
            }
        }
    }
}
