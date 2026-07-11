package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.DayLoomLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * The loom surface (owner's Viz flow): a single fixed screen — no scroll. The
 * wordmark and the day's facts sit across the top ("2 Sources · Global · 12
 * July 2026" — the date opens the picker), and the day loom fills the rest,
 * every stream fading into the page top and bottom. Reached by pulling the
 * Stand's day bar down; dismissed by dragging back down past a threshold (the
 * reverse of the open), with a grabber at the top as the accessible equivalent.
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

    var drag by remember { mutableFloatStateOf(0f) }
    val closeThresholdPx = with(LocalDensity.current) { 140.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, drag.roundToInt().coerceAtLeast(0)) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag > closeThresholdPx) onClose()
                        drag = 0f
                    },
                    onVerticalDrag = { _, dy -> drag = (drag + dy).coerceAtLeast(0f) },
                )
            }
            .padding(top = Tokens.Spacing.xs),
    ) {
        // Grabber: the drag-to-dismiss made visible, and tappable as its a11y equivalent.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
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
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
            NoozWordmark(fontSize = 34.sp)
            Text(
                "$enabledCount Sources",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                filter.region.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                aggregate?.let { DAY_FORMAT.format(Instant.ofEpochMilli(it.weekStart).atZone(ZoneId.systemDefault())) } ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable(onClickLabel = "Open the date picker") { vm.setDatePickerVisible(true) }
                    .semantics { role = Role.Button }
                    .minimumInteractiveComponentSize()
                    .padding(vertical = Tokens.Spacing.xxs),
            )
        }

        if (aggregate == null || loom.totalFlowed == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing flowed this day. The loom weaves once your sources do.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Tokens.Spacing.xl),
                )
            }
        } else {
            DayLoomCanvas(
                loom = loom,
                enabledSourceCount = enabledCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = Tokens.Spacing.sm),
            )
        }
    }
}
