package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.StoryClustering

/**
 * The framing lens (owner's contrast idea, phase 3): stories that more than one
 * of your sources covered, with each outlet's headline set side by side — so
 * the difference in how they framed the same event (the "invades" vs "cross
 * into" contrast) is there to read. Clustering is on-device and needs no model;
 * the *automatic* marking of which words are the loaded ones is the lens's job
 * and waits on a real inference provider — until then, the comparison is yours
 * to make, which is the honest version anyway.
 */
@Composable
fun FramingsPanel(
    items: List<Item>,
    windowStart: Long,
    windowEndExclusive: Long,
    sourceTitles: Map<String, String>,
    onOpenItem: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clusters = remember(items, windowStart, windowEndExclusive) {
        val inWindow = items.filter { it.publishedAt in windowStart until windowEndExclusive }
        val byId = inWindow.associateBy { it.id }
        StoryClustering
            .cluster(inWindow.map { StoryClustering.Doc(it.id, it.title, it.sourceId) })
            .map { cluster -> cluster to cluster.members.mapNotNull { byId[it.id] } }
    }

    if (clusters.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(Tokens.Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No story here was covered by more than one of your sources yet. Framings appear once two outlets tell the same story.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
        items(clusters, key = { it.first.members.first().id }) { (cluster, members) ->
            FramingCard(
                keywords = cluster.keywords,
                sourceCount = cluster.sourceCount,
                members = members,
                sourceTitles = sourceTitles,
                onOpenItem = onOpenItem,
            )
        }
    }
}

@Composable
private fun FramingCard(
    keywords: List<String>,
    sourceCount: Int,
    members: List<Item>,
    sourceTitles: Map<String, String>,
    onOpenItem: (Item) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        // The rough subject (the shared words) + how many outlets ran it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                keywords.joinToString(" · ").ifBlank { "Same story" }.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$sourceCount sources",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // One headline per outlet — the framings, verbatim, to compare.
        for (item in members) {
            FramingLine(
                source = sourceTitles[item.sourceId] ?: "",
                title = item.title,
                onClick = { onOpenItem(item) },
            )
        }
    }
}

@Composable
private fun FramingLine(source: String, title: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Tokens.Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        if (source.isNotBlank()) {
            Text(
                source.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
