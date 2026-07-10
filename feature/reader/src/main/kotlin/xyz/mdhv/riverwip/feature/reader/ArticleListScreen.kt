package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.Topic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")

/** Honest empty-state copy for the exact situation — never a silent blank (brief §3). */
private fun emptyBody(enabledCount: Int, isRefreshing: Boolean, last: RefreshResult?): String = when {
    enabledCount == 0 ->
        "This reader only ever shows ${Copy.fromSources(0)}, never all the news. " +
            "Add a feed on the Sources tab to begin."
    isRefreshing -> "Fetching stories ${Copy.fromSources(enabledCount)}…"
    last?.allFailed == true ->
        "Couldn't reach your sources${last.error?.let { " ($it)" } ?: ""}. " +
            "Check your connection and tap Fetch now to try again."
    last != null && last.newItems == 0 ->
        "Your sources returned nothing new. Tap Fetch now to check again."
    else -> "Tap Fetch now to pull the latest stories ${Copy.fromSources(enabledCount)}."
}

/**
 * The newsstand (owner's Stand mock, 2026-07): big serif titles over quiet
 * "Author | Source" bylines, hairline dividers, and a topic filter in the
 * header with an EDIT affordance. The denominator line stays on the surface
 * (brief §1: every stream-total names its source-set), as do the refresh
 * affordances, the honest empty states, and the end-of-feed marker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    vm: ReaderViewModel,
    onOpenItem: (Item) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val allItems by vm.items.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()

    // Topic filter (the Stand mock's "Global | Politics" section header). Held
    // as the topic key so rotation survives; null = all topics.
    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingFilter by rememberSaveable { mutableStateOf(false) }
    val filterTopic = filterKey?.let { Topic.fromKey(it) }
    val items = remember(allItems, filterTopic) {
        if (filterTopic == null) allItems
        else allItems.filter { Classifier.dominantTopic(it.topics) == filterTopic }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            filterTopic?.placeholderLabel ?: "All stories",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            // Denominator honesty on every stream-total surface (brief §1/§7).
                            "${items.size} stories ${Copy.fromSources(enabledCount)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { editingFilter = !editingFilter }) {
                        Text(if (editingFilter) "DONE" else "EDIT", style = MaterialTheme.typography.labelLarge)
                    }
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = Tokens.Spacing.md)
                                .size(20.dp)
                                .semantics { contentDescription = "Fetching your sources" },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { vm.refresh() }, enabled = enabledCount > 0) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Fetch your sources now")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (editingFilter) {
                TopicFilterRow(
                    available = remember(allItems) {
                        allItems.map { Classifier.dominantTopic(it.topics) }.distinct()
                    },
                    selected = filterTopic,
                    onSelect = { filterKey = it?.key },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (items.isEmpty()) {
                if (filterTopic != null && allItems.isNotEmpty()) {
                    EmptyState(
                        title = "Nothing under ${filterTopic.placeholderLabel}",
                        body = "None of the ${allItems.size} stories ${Copy.fromSources(enabledCount)} " +
                            "carry this topic right now.",
                        action = { Button(onClick = { filterKey = null }) { Text("Show all stories") } },
                    )
                } else {
                    EmptyState(
                        title = if (enabledCount == 0) "No sources yet" else "Nothing here yet",
                        body = emptyBody(enabledCount, isRefreshing, lastRefresh),
                        action = if (enabledCount > 0 && !isRefreshing) {
                            { Button(onClick = { vm.refresh() }) { Text("Fetch now") } }
                        } else {
                            null
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun TopicFilterRow(available: List<Topic>, selected: Topic?, onSelect: (Topic?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        for (topic in available) {
            FilterChip(
                selected = selected == topic,
                onClick = { onSelect(topic) },
                label = { Text(topic.placeholderLabel) },
            )
        }
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
    val time = TIME_FORMAT.format(Instant.ofEpochMilli(item.publishedAt).atZone(ZoneId.systemDefault()))
    val names = listOfNotNull(item.author?.takeIf { it.isNotBlank() }, sourceTitle).joinToString(" | ")
    return if (names.isEmpty()) time else "$names · $time"
}
