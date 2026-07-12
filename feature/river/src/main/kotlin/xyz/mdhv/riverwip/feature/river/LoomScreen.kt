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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.DayLoomLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * The loom surface (owner's Viz flow): a single fixed screen — no scroll. The
 * wordmark and the day's facts sit across the top ("2 Sources · Global · 12
 * July 2026" — the date opens the picker), and the day loom fills the rest,
 * every stream fading into the page top and bottom. Reached by pulling the
 * Stand's day bar down; dismissed by **swiping back up** — the reverse of the
 * open — where it lifts, shrinks, and fades as it recedes toward the bar (a
 * continuous morph), past a threshold. The grabber is the accessible equivalent.
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
            selectedRangeEnd = vm.selectedRangeEnd,
            onPick = { vm.selectDay(it) },
            onPickRange = { start, end -> vm.selectRange(start, end) },
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

    // Swipe up to recede (the reverse of the Stand's pull-down open): the loom
    // lifts, shrinks, and fades toward the bar it came from — a continuous morph.
    var lift by remember { mutableFloatStateOf(0f) }
    val closeThresholdPx = with(LocalDensity.current) { 150.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = (lift / closeThresholdPx).coerceIn(0f, 1f)
                translationY = -lift
                alpha = 1f - p * 0.55f
                val s = 1f - p * 0.10f
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (lift > closeThresholdPx) onClose()
                        lift = 0f
                    },
                    // dy is negative when dragging up; accumulate the upward lift.
                    onVerticalDrag = { _, dy -> lift = (lift - dy).coerceAtLeast(0f) },
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
        }

        // Date navigation (owner's #8): previous/next chevrons flanking the
        // date, which still opens the full month picker on its own tap — the
        // old tap-only date text didn't read as navigable at all.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = { vm.stepDay(-1, days) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
            }
            val rangeEnd = vm.selectedRangeEnd
            val dateLabel = aggregate?.let { agg ->
                val startLabel = DAY_FORMAT.format(Instant.ofEpochMilli(agg.weekStart).atZone(ZoneId.systemDefault()))
                if (rangeEnd == null) {
                    startLabel
                } else {
                    val endLabel = DAY_FORMAT.format(Instant.ofEpochMilli(rangeEnd).atZone(ZoneId.systemDefault()))
                    "$startLabel – $endLabel"
                }
            } ?: "—"
            Text(
                dateLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable(onClickLabel = "Open the date picker") { vm.setDatePickerVisible(true) }
                    .semantics { role = Role.Button }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = Tokens.Spacing.sm, vertical = Tokens.Spacing.xxs),
            )
            IconButton(onClick = { vm.stepDay(1, days) }, enabled = vm.canStepForward(days)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
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
