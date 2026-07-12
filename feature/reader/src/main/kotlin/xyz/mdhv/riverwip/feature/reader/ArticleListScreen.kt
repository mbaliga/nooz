package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.CandyCaneBar
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.DayMixBar
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.Item
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAND_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")

/** Honest empty-state copy for the exact situation — never a silent blank (brief §3). */
private fun emptyBody(enabledCount: Int, isRefreshing: Boolean, last: RefreshResult?, filtered: Boolean): String = when {
    enabledCount == 0 -> "Click here to add sources\nand read the Nooz"
    isRefreshing -> "Fetching stories ${Copy.fromSources(enabledCount)}…"
    filtered -> "Nothing under this region and topic mix right now. EDIT widens the filter."
    last?.allFailed == true ->
        "Couldn't reach your sources${last.error?.let { " ($it)" } ?: ""}. Tap to try again."
    last != null && last.newItems == 0 -> "Your sources returned nothing new. Tap to check again."
    else -> "Tap to pull the latest stories ${Copy.fromSources(enabledCount)}."
}

/**
 * The Nooz Stand (owner's mocks + flow map, 2026-07): wordmark and date over
 * the day bar — the loom folded flat; candy-cane while empty or fetching —
 * then the region|topics line with EDIT, then the stand itself. Pulling down
 * past the top stretches the bar and lets go into the full day loom; tapping
 * the bar does the same without the theatre.
 */
@Composable
fun ArticleListScreen(
    vm: ReaderViewModel,
    noozFlashEnabled: Boolean,
    onOpenItem: (Item) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenLoom: () -> Unit,
    onOpenClippings: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    // The Stand's top bar shows what was actually read today, not what merely
    // flowed (owner's #5) — the reader's own bottom utility bar keeps the
    // supply-based mix as ambient context while reading.
    val todayReadMix by vm.todayReadMix.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var pull by remember { mutableFloatStateOf(0f) }
    val openThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 140.dp.toPx() }

    // Pull-down-to-loom: overscroll at the top stretches the bar; releasing
    // past the threshold opens the loom.
    val pullConnection = remember(openThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    pull = (pull + available.y).coerceAtMost(openThresholdPx * 1.4f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pull > openThresholdPx) onOpenLoom()
                pull = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header: wordmark + today's date.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoozWordmark(fontSize = 30.sp)
            Spacer(Modifier.weight(1f))
            Text(
                STAND_DATE.format(LocalDate.now(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The bar: today's mix, or the candy cane while there's nothing to show.
        // The pull stretches it toward the loom it stands for.
        val barHeight = 6.dp + with(androidx.compose.ui.platform.LocalDensity.current) {
            (pull / 8f).toDp().coerceAtMost(18.dp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize() // >=48dp tap target; the thin bar centres inside
                .padding(horizontal = Tokens.Spacing.md)
                .height(barHeight)
                .clickable(onClickLabel = "Open the day loom") { onOpenLoom() },
        ) {
            if (todayReadMix.isEmpty() || isRefreshing) {
                CandyCaneBar(Modifier.fillMaxSize())
            } else {
                DayMixBar(todayReadMix, Modifier.fillMaxSize())
            }
        }

        // Region | Topics line with EDIT (and a quiet refresh — utility the mock leaves implicit).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                filter.summary(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenClippings) {
                Icon(Icons.Filled.Bookmarks, contentDescription = "Open your clippings")
            }
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = Tokens.Spacing.sm)
                        .size(18.dp)
                        .semantics { contentDescription = "Fetching your sources" },
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = { vm.refresh() }, enabled = enabledCount > 0) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Fetch your sources now")
                }
            }
            TextButton(onClick = onOpenEdit) {
                Text("EDIT", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (noozFlashEnabled) {
            FlashCard(vm = vm, modifier = Modifier.padding(horizontal = Tokens.Spacing.md))
        }

        if (items.isEmpty()) {
            EmptyStand(
                enabledCount = enabledCount,
                isRefreshing = isRefreshing,
                lastRefresh = lastRefresh,
                anyUnfiltered = enabledCount > 0 && lastRefresh != null && !filter.allTopics,
                onAdd = onOpenEdit,
                onRetry = { vm.refresh() },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullConnection),
                contentPadding = PaddingValues(vertical = Tokens.Spacing.xs),
            ) {
                items(items, key = { it.id }) { item ->
                    ItemRow(item, sourceTitles[item.sourceId], onClick = { onOpenItem(item) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    // The river has banks (brief §3): an explicit end, never infinite backfill.
                    Box(Modifier.fillMaxWidth().padding(Tokens.Spacing.lg), contentAlignment = Alignment.Center) {
                        Text(
                            Copy.END_OF_FEED,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The empty stand (owner's mock): the centred plus button and its invitation.
 * With sources but no stories, the same shape retries the fetch instead.
 */
@Composable
private fun EmptyStand(
    enabledCount: Int,
    isRefreshing: Boolean,
    lastRefresh: RefreshResult?,
    anyUnfiltered: Boolean,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    val addMode = enabledCount == 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Tokens.Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                .clickable(
                    enabled = !isRefreshing,
                    onClickLabel = if (addMode) "Add sources" else "Fetch now",
                ) { if (addMode) onAdd() else onRetry() }
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.background,
                )
            } else {
                Icon(
                    if (addMode) Icons.Filled.Add else Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            emptyBody(enabledCount, isRefreshing, lastRefresh, anyUnfiltered),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Spacing.lg),
        )
    }
}

@Composable
private fun ItemRow(item: Item, sourceTitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        // Serif title — the Stand mock's voice (headlineSmall is serif by role).
        Text(item.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3)
        Text(
            byline(item, sourceTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** "Author | Source · Jul 8, 14:02" — the mock's byline, plus recency (kept: honest and useful). */
private fun byline(item: Item, sourceTitle: String?): String {
    val time = TIME_FORMAT.format(java.time.Instant.ofEpochMilli(item.publishedAt).atZone(ZoneId.systemDefault()))
    val names = listOfNotNull(item.author?.takeIf { it.isNotBlank() }, sourceTitle).joinToString(" | ")
    return if (names.isEmpty()) time else "$names · $time"
}
