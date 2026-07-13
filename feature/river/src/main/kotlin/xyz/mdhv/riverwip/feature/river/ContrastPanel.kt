package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.Topic
import kotlin.math.roundToInt

/** How the contrast rows are ordered — the reader picks what the view is "about" (owner: configurable). */
private enum class ContrastSort(val label: String) {
    FLOWED("Flowed"),
    READ("Read"),
    GAP("Gap"),
}

private data class ContrastRow(
    val topic: Topic,
    val flowed: Int,
    val read: Int,
    val flowedShare: Float,
    val readShare: Float,
) {
    /** Positive = a blind spot (more flowed than you read); negative = a fixation (read more than flowed). */
    val gap: Float get() = flowedShare - readShare
}

/**
 * The omission dashboard (owner's contrast idea, phase 1): the loom's stark
 * counterpart. Where the woven canvas is atmospheric, this is a blunt ledger —
 * for the selected day/range, each topic's share of what **flowed** set against
 * its share of what you actually **read**, so the gap between the two (the
 * omission this whole app is about) is impossible to miss. A sort control lets
 * the reader aim the view at what flowed, what they read, or the widest gap.
 * Supply is never filtered here — omission is the subject, not something to
 * hide (same principle the loom holds to).
 */
@Composable
fun ContrastPanel(
    streamByTopic: Map<String, Int>,
    readByTopic: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val totalFlowed = streamByTopic.values.sum()
    val totalRead = readByTopic.values.sum()

    val rows = remember(streamByTopic, readByTopic) {
        val keys = streamByTopic.keys + readByTopic.keys
        keys.map { key ->
            val flowed = streamByTopic[key] ?: 0
            val read = readByTopic[key] ?: 0
            ContrastRow(
                topic = Topic.fromKey(key),
                flowed = flowed,
                read = read,
                flowedShare = if (totalFlowed == 0) 0f else flowed.toFloat() / totalFlowed,
                readShare = if (totalRead == 0) 0f else read.toFloat() / totalRead,
            )
        }
    }

    var sort by remember { mutableStateOf(ContrastSort.FLOWED) }
    val ordered = remember(rows, sort) {
        when (sort) {
            ContrastSort.FLOWED -> rows.sortedByDescending { it.flowed }
            ContrastSort.READ -> rows.sortedByDescending { it.read }
            ContrastSort.GAP -> rows.sortedByDescending { it.gap }
        }
    }
    // Both bars share one scale so their lengths are directly comparable — the
    // longest single share in the whole set fills the row.
    val maxShare = remember(rows) {
        rows.maxOfOrNull { maxOf(it.flowedShare, it.readShare) }?.takeIf { it > 0f } ?: 1f
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
        // The headline: the honest ratio, in plain words.
        val pct = if (totalFlowed == 0) 0 else (100f * totalRead / totalFlowed).roundToInt()
        Text(
            "You read $totalRead of $totalFlowed stories that flowed — $pct%.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // The biggest blind spot: most flowed, least read (only when you did
        // read *something*, else the "gap" is just everything).
        val blindSpot = ordered.maxByOrNull { it.gap }
        if (totalRead > 0 && blindSpot != null && blindSpot.gap > 0.08f) {
            Text(
                "Widest gap: ${blindSpot.topic.placeholderLabel} — ${(blindSpot.flowedShare * 100).roundToInt()}% of the stream, ${(blindSpot.readShare * 100).roundToInt()}% of your reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Sort control — the small lever for "what this view is about".
        Row(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
            for (option in ContrastSort.entries) {
                val chosen = sort == option
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    color = if (chosen) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { sort = option })
                        .padding(vertical = Tokens.Spacing.xxs),
                )
            }
        }

        if (totalFlowed == 0) {
            Text(
                "Nothing flowed this day. The contrast appears once your sources do.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.lg),
            )
        } else {
            for (row in ordered) {
                ContrastRowView(row = row, maxShare = maxShare)
            }
        }
    }
}

@Composable
private fun ContrastRowView(row: ContrastRow, maxShare: Float) {
    val color = row.topic.toComposeColor()
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                row.topic.placeholderLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Tokens.Spacing.xs),
            )
            Text(
                "saw ${(row.flowedShare * 100).roundToInt()}%  ·  read ${(row.readShare * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Two bars on one scale: the faint one is everything that flowed (the
        // potential); the solid one is what you actually read. Length apart =
        // the omission.
        ShareBar(fraction = row.flowedShare / maxShare, color = color.copy(alpha = 0.28f))
        ShareBar(fraction = row.readShare / maxShare, color = color)
    }
}

@Composable
private fun ShareBar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(color),
        )
    }
}
