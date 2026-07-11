package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.Tokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM")
private val DAY_DESC = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val DOW = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/**
 * The loom's date picker (owner's mock): a vertically scrolling run of months
 * — Sunday-first grid, the selected day a filled circle, today outlined.
 * Months span the Item retention window, so every pickable day can actually
 * have data.
 */
@Composable
fun LoomDatePicker(
    days: List<xyz.mdhv.riverwip.model.WeeklyAggregate>,
    selectedDayStart: Long?,
    onPick: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val selected = selectedDayStart?.let { localDate(it, zone) }
    val firstMonth = remember(days) {
        val earliest = days.firstOrNull()?.weekStart?.let { localDate(it, zone) } ?: today.minusDays(60)
        YearMonth.from(minOf(earliest, today.minusDays(60)))
    }
    val months = remember(firstMonth, today) {
        generateSequence(firstMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(today)) }
            .toList()
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (months.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoozWordmark(fontSize = 30.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "Pick a day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Spacing.md)) {
            for (d in DOW) {
                Text(
                    d,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(months.size) { i ->
                MonthGrid(
                    month = months[i],
                    today = today,
                    selected = selected,
                    onPick = { date ->
                        onPick(date.atStartOfDay(zone).toInstant().toEpochMilli())
                    },
                )
            }
        }
    }
}

private fun localDate(millis: Long, zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, selected: LocalDate?, onPick: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Spacing.md)) {
        Text(
            MONTH_FORMAT.format(month.atDay(1)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Tokens.Spacing.sm),
        )
        val firstDow = month.atDay(1).dayOfWeek.value % 7 // Sunday-first column index
        val daysInMonth = month.lengthOfMonth()
        var day = 1 - firstDow
        while (day <= daysInMonth) {
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    val d = day + it
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (d in 1..daysInMonth) {
                            val date = month.atDay(d)
                            val isSelected = date == selected
                            val isToday = date == today
                            val isFuture = date.isAfter(today)
                            val dayLabel = DAY_DESC.format(date) +
                                when { isSelected -> ", selected"; isFuture -> ", not yet available"; else -> "" }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Tokens.Spacing.xxs)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(Tokens.Border.thin, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .selectable(
                                        selected = isSelected,
                                        enabled = !isFuture,
                                        role = Role.Button,
                                        onClick = { onPick(date) },
                                    )
                                    .semantics { contentDescription = dayLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.background
                                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.onBackground
                                    },
                                )
                            }
                        }
                    }
                }
            }
            day += 7
        }
    }
}
