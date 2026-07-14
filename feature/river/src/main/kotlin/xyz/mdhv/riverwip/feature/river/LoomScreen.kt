package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
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
import xyz.mdhv.riverwip.model.Item
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")
private const val MILLIS_PER_DAY = 86_400_000L

/** The three ways to read a day: the woven loom, the stark contrast ledger, the framing comparison. */
private enum class LoomMode { LOOM, CONTRAST, FRAMINGS }

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
fun LoomScreen(vm: LoomViewModel, onClose: () -> Unit, onOpenItem: (Item) -> Unit) {
    val days by vm.days.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val recentItems by vm.recentItems.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    // Three ways to read the same day (owner's contrast idea): the woven Loom,
    // the stark Contrast ledger, and the Framings comparison.
    var mode by remember { mutableStateOf(LoomMode.LOOM) }

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
            .padding(top = Tokens.Spacing.sm),
    ) {
        // No grabber handle (owner: "immersive, immersive, immersive"). Swipe up
        // to dismiss — the whole surface takes the gesture — and the system back
        // control returns to the Stand as the accessible equivalent.

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

        // The mode toggle: the same day, woven, laid bare, or set side by side.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Tokens.Spacing.xxs),
            horizontalArrangement = Arrangement.Center,
        ) {
            ModeTab("Loom", mode == LoomMode.LOOM) { mode = LoomMode.LOOM }
            ModeTab("Contrast", mode == LoomMode.CONTRAST) { mode = LoomMode.CONTRAST }
            ModeTab("Framings", mode == LoomMode.FRAMINGS) { mode = LoomMode.FRAMINGS }
        }

        when (mode) {
            LoomMode.CONTRAST -> ContrastPanel(
                streamByTopic = aggregate?.streamCountsByTopic ?: emptyMap(),
                readByTopic = aggregate?.readCountsByTopic ?: emptyMap(),
                sourceCounts = aggregate?.sourceCounts ?: emptyMap(),
                filter = filter,
                enabledSourceCount = enabledCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = Tokens.Spacing.sm),
            )
            LoomMode.FRAMINGS -> {
                val windowStart = aggregate?.weekStart ?: 0L
                val windowEndExclusive = (vm.selectedRangeEnd ?: windowStart) + MILLIS_PER_DAY
                FramingsPanel(
                    items = recentItems,
                    windowStart = windowStart,
                    windowEndExclusive = windowEndExclusive,
                    sourceTitles = sourceTitles,
                    onOpenItem = onOpenItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = Tokens.Spacing.sm),
                )
            }
            LoomMode.LOOM -> if (aggregate == null || loom.totalFlowed == 0) {
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
}

/** One of the two loom modes — a plain, inked-when-active text tab. */
@Composable
private fun ModeTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .clickable(onClickLabel = "Show the $label view") { onClick() }
            .semantics { role = Role.Tab }
            .minimumInteractiveComponentSize()
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xxs),
    )
}
