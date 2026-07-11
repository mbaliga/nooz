package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.DayLoomLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * The loom surface (owner's Viz flow): wordmark, the day's facts across the
 * top ("2 Sources · Global · 12 July 2026" — the date opens the picker), and
 * the day loom beneath. Reached by pulling the Stand's day bar down; and
 * dismissed the same way in reverse — an overscroll pull-down here past the
 * threshold sends it back up to the Stand (so the gesture is symmetric), with
 * a grabber at the top as the visible, accessible equivalent.
 */
@Composable
fun LoomScreen(vm: LoomViewModel, onClose: () -> Unit) {
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

    val scrollState = rememberScrollState()
    var pull by remember { mutableFloatStateOf(0f) }
    val closeThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    // Reverse of the Stand's pull-to-open: overscroll down at the top gives a
    // little, and releasing past the threshold retracts the loom to the Stand.
    val dismissConnection = remember(closeThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && scrollState.value == 0) {
                    pull = (pull + available.y).coerceAtMost(closeThresholdPx * 1.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull > closeThresholdPx) onClose()
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(dismissConnection)
            .verticalScroll(scrollState)
            .offset { IntOffset(0, pull.roundToInt()) }
            .padding(top = Tokens.Spacing.xs),
    ) {
        // Grabber: the pull-to-dismiss made visible, and tappable as its a11y equivalent.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Tokens.Spacing.xs)
                .semantics {
                    contentDescription = "Close the loom"
                    role = Role.Button
                }
                .clickable(onClickLabel = "Close the loom") { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
        }

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
