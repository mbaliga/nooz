package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.RiverLayout
import xyz.mdhv.riverwip.model.Topic

/**
 * The river: the app's analytical centerpiece (brief §P4). Weekly abstraction,
 * topic-stacked flow with the read-thread woven through it, scrub-to-slice for
 * a cross-section. Needs at least two periods of history before it says
 * anything (a single week has nothing to compare against for the
 * supply-vs-drift decomposition) — until then, an honest empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiverScreen(vm: RiverViewModel) {
    val rawAggregates by vm.aggregates.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()

    // Sorted once and reused for BOTH the canvas columns and the cross-section
    // lookup, so `selectedWeekIndex` means the same thing in both places.
    val aggregates = remember(rawAggregates) { rawAggregates.sortedBy { it.weekStart } }
    val columns = remember(aggregates) { RiverLayout.layout(aggregates) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("River", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "What flowed ${Copy.fromSources(enabledCount)}, and what you read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
    ) { padding ->
        if (aggregates.size < 2) {
            EmptyState(
                title = "The river needs history",
                body = "Once a couple of periods of stories ${Copy.fromSources(enabledCount)} have flowed — and you've read some — the shape appears here.",
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                RiverCanvas(
                    columns = columns,
                    selectedIndex = vm.selectedWeekIndex,
                    onSelectWeek = { vm.selectWeek(it) },
                    modifier = Modifier.padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.sm),
                )
                RiverLegend(modifier = Modifier.padding(horizontal = Tokens.Spacing.md))
                HorizontalDivider(modifier = Modifier.padding(vertical = Tokens.Spacing.md))

                val selected = vm.selectedWeekIndex
                if (selected != null && selected in aggregates.indices) {
                    CrossSectionPanel(aggregates, selected)
                } else {
                    Text(
                        "Tap a week above to slice it open.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Tokens.Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun RiverLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        for (topic in Topic.entries) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(topic.toComposeColor()),
                )
                Text(
                    topic.placeholderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
