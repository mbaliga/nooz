package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.RiverFlowLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PERIOD_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * The river (brief §P4), now in the owner's Viz composition (2026-07): wordmark
 * and period date up top, the hourglass supply-vs-consumption flow as the
 * centrepiece, and the cross-section metrics — coverage, over/under, breadth,
 * the supply-vs-drift decomposition, every one tap-explained — below it. Still
 * needs two periods of history before it says anything.
 */
@Composable
fun RiverScreen(vm: RiverViewModel) {
    val rawAggregates by vm.aggregates.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()

    val aggregates = remember(rawAggregates) { rawAggregates.sortedBy { it.weekStart } }

    if (aggregates.size < 2) {
        EmptyState(
            title = "The river needs history",
            body = "Once a couple of periods of stories ${Copy.fromSources(enabledCount)} have flowed — and you've read some — the shape appears here.",
        )
        return
    }

    // Latest period by default; the chevrons walk history. Clamped so a
    // shrinking aggregate list can never leave a stale out-of-range selection.
    val index = (vm.selectedWeekIndex ?: aggregates.lastIndex).coerceIn(0, aggregates.lastIndex)
    val aggregate = aggregates[index]
    val flow = remember(aggregate) { RiverFlowLayout.layout(aggregate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Tokens.Spacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // The wordmark — the owner's decided name (design mocks, 2026-07).
                Text("Nooz", style = MaterialTheme.typography.headlineMedium)
                Text(
                    // Denominator honesty (brief §1): the flow's totals name their source-set.
                    "What flowed ${Copy.fromSources(enabledCount)}, and what you read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.selectWeek(index - 1) }, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier period")
            }
            Text(
                PERIOD_FORMAT.format(Instant.ofEpochMilli(aggregate.weekStart).atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { vm.selectWeek(index + 1) }, enabled = index < aggregates.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later period")
            }
        }

        RiverFlowCanvas(
            flow = flow,
            enabledSourceCount = enabledCount,
            sourceTitles = sourceTitles,
            modifier = Modifier.padding(horizontal = Tokens.Spacing.xs, vertical = Tokens.Spacing.sm),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CrossSectionPanel(aggregates, index)
    }
}
