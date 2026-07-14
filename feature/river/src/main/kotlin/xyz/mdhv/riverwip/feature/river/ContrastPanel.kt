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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.ReaderFilter
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

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

/**
 * The omission dashboard (owner's contrast idea, phases 1–2): the loom's stark
 * counterpart. Where the woven canvas is atmospheric, this is a blunt ledger.
 *
 * Two contrasts, top to bottom:
 *  - **Reach** (phase 2, filter vs reality): a funnel from everything that
 *    **flowed** from your sources, down through what your **filter** let past,
 *    down to what you actually **read** — the two omissions (the one you chose
 *    with your filter, and the one your attention made) named in plain counts.
 *  - **By topic** (phase 1): each topic's share of the stream set against its
 *    share of your reading, on one scale, so the gap is impossible to miss.
 *
 * Supply is never filtered away here — omission is the subject, same principle
 * the loom holds to.
 */
@Composable
fun ContrastPanel(
    streamByTopic: Map<String, Int>,
    readByTopic: Map<String, Int>,
    sourceCounts: Map<String, Int>,
    filter: ReaderFilter,
    enabledSourceCount: Int,
    modifier: Modifier = Modifier,
) {
    val totalFlowed = streamByTopic.values.sum()
    val totalRead = readByTopic.values.sum()
    val admitted = if (filter.allTopics) {
        totalFlowed
    } else {
        streamByTopic.entries.filter { filter.matchesTopic(Topic.fromKey(it.key)) }.sumOf { it.value }
    }
    val setAside = (totalFlowed - admitted).coerceAtLeast(0)
    val delivered = sourceCounts.count { it.value > 0 }
    val silent = (enabledSourceCount - delivered).coerceAtLeast(0)

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
    val maxShare = remember(rows) {
        rows.maxOfOrNull { maxOf(it.flowedShare, it.readShare) }?.takeIf { it > 0f } ?: 1f
    }

    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
        if (totalFlowed == 0) {
            Text(
                "Nothing flowed this day. The contrast appears once your sources do.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(top = Tokens.Spacing.lg),
            )
            return@Column
        }

        // ---- Reach funnel (phase 2) ----
        SectionHeading("Reach")
        Text(
            "Your filter: ${filter.summary()}",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        FunnelRow("Flowed", totalFlowed, 1f, onBg.copy(alpha = 0.30f))
        FunnelRow("Your filter let through", admitted, admitted.toFloat() / totalFlowed, onBg.copy(alpha = 0.60f))
        FunnelRow("You read", totalRead, totalRead.toFloat() / totalFlowed, onBg)

        val sentences = buildList {
            if (setAside > 0) {
                add("Your filter set aside $setAside ${plural(setAside, "story", "stories")} before they reached you.")
            }
            if (admitted > 0) {
                add("You read $totalRead of the $admitted it let through.")
            }
            val sourceLine = buildString {
                append("$enabledSourceCount ${plural(enabledSourceCount, "source", "sources")} enabled · $delivered delivered")
                if (silent > 0) append(" · $silent quiet")
            }
            add(sourceLine)
        }
        for (line in sentences) {
            Text(line, style = MaterialTheme.typography.bodyMedium, color = muted)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ---- By topic (phase 1) ----
        SectionHeading("By topic")
        val blindSpot = ordered.maxByOrNull { it.gap }
        if (totalRead > 0 && blindSpot != null && blindSpot.gap > 0.08f) {
            Text(
                "Widest gap: ${blindSpot.topic.placeholderLabel} — ${(blindSpot.flowedShare * 100).roundToInt()}% of the stream, ${(blindSpot.readShare * 100).roundToInt()}% of your reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
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
                    color = if (chosen) onBg else muted,
                    modifier = Modifier
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { sort = option })
                        .padding(vertical = Tokens.Spacing.xxs),
                )
            }
        }
        for (row in ordered) {
            ContrastRowView(row = row, maxShare = maxShare)
        }
    }
}

/** One funnel stage — a labelled bar on the shared "everything flowed = full width" scale. */
@Composable
private fun FunnelRow(label: String, count: Int, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        ShareBar(fraction = fraction, color = color)
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
private fun ShareBar(fraction: Float, color: Color) {
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
