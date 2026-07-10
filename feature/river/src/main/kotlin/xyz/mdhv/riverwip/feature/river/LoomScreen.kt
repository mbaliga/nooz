package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.DayLoomLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * The loom surface (owner's Viz flow): wordmark, the day's facts across the
 * top ("2 Sources · Global · 12 July 2026" — the date opens the picker), and
 * the day loom beneath. Reached by pulling the Stand's day bar down, or
 * tapping any day bar.
 */
@Composable
fun LoomScreen(vm: LoomViewModel) {
    val days by vm.days.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    if (vm.showDatePicker) {
        LoomDatePicker(
            days = days,
            selectedDayStart = vm.selectedDayStart ?: days.lastOrNull()?.weekStart,
            onPick = { vm.selectDay(it) },
        )
        return
    }

    val aggregate = vm.aggregateFor(days)
    val loom = remember(aggregate) {
        if (aggregate == null) {
            DayLoomLayout.Loom(emptyList(), 0, 0)
        } else {
            DayLoomLayout.layout(aggregate.streamCountsByTopic, aggregate.readCountsByTopic)
        }
    }

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
            Text("Nooz", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "$enabledCount Sources · ${filter.region.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "  ·  " + (aggregate?.let { DAY_FORMAT.format(Instant.ofEpochMilli(it.weekStart).atZone(ZoneId.systemDefault())) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable { vm.setDatePickerVisible(true) }
                    .semantics { role = Role.Button }
                    .padding(vertical = Tokens.Spacing.xxs),
            )
        }

        if (aggregate == null || loom.totalFlowed == 0) {
            Text(
                "Nothing flowed this day. The loom weaves once your sources do.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Tokens.Spacing.xl),
            )
        } else {
            DayLoomCanvas(
                loom = loom,
                enabledSourceCount = enabledCount,
                modifier = Modifier.padding(top = Tokens.Spacing.sm),
            )
        }
    }
}
