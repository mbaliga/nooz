package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Region
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
 * The omission dashboard (owner's contrast idea) — the loom's stark counterpart,
 * redrawn minimal (owner's references): one muted ink, thin marks, wide
 * whitespace, numbers doing the talking. Two contrasts, top to bottom:
 *
 *  - **Reach**: a single nested bar funnelling everything that **flowed** →
 *    what your **filter** let through → what you **read**, each a deeper shade
 *    of the one ink, so the two omissions (the one you chose, the one your
 *    attention made) are the shrinking of the bar.
 *  - **By topic**: a dumbbell per topic — a faint dot for its share of the
 *    stream, a solid dot for its share of your reading, a hairline between
 *    them. The length of that line is the gap.
 *
 * Supply is never filtered away here — omission is the subject.
 */
@Composable
fun ContrastPanel(
    streamByTopic: Map<String, Int>,
    readByTopic: Map<String, Int>,
    sourceCounts: Map<String, Int>,
    filter: ReaderFilter,
    enabledSourceCount: Int,
    readsByRegion: Map<Region, Int>,
    onSetRegion: (Region) -> Unit,
    onToggleTopic: (String) -> Unit,
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

    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xl),
    ) {
        // ---- Regions: the globe opened up as a read-heatmap + the filter ----
        RegionsSection(
            reads = readsByRegion,
            filter = filter,
            onSetRegion = onSetRegion,
            onToggleTopic = onToggleTopic,
            ink = ink,
            muted = muted,
        )

        if (totalFlowed == 0) {
            Text(
                "Nothing flowed this day. The contrast appears once your sources do.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(top = Tokens.Spacing.md),
            )
            return@Column
        }

        // ---- Reach: one nested funnel bar + a spare legend ----
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            SectionHeading("Reach")
            NestedFunnel(
                filterFraction = admitted.toFloat() / totalFlowed,
                readFraction = totalRead.toFloat() / totalFlowed,
                ink = ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.lg)) {
                FunnelLegend("Flowed", totalFlowed, ink.copy(alpha = 0.22f), ink)
                FunnelLegend("Filter", admitted, ink.copy(alpha = 0.5f), ink)
                FunnelLegend("Read", totalRead, ink, ink)
            }
            val sentences = buildList {
                if (setAside > 0) add("Your filter set aside $setAside ${plural(setAside, "story", "stories")} before they reached you.")
                if (admitted > 0) add("You read $totalRead of the $admitted it let through.")
                add(
                    buildString {
                        append("$enabledSourceCount ${plural(enabledSourceCount, "source", "sources")} on · $delivered delivered")
                        if (silent > 0) append(" · $silent quiet")
                    },
                )
            }
            for (line in sentences) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }

        // ---- By topic: dumbbells + a spare sort lever ----
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("By topic", modifier = Modifier.weight(1f))
                Row(
                    Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                ) {
                    for (option in ContrastSort.entries) {
                        val chosen = sort == option
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                            color = if (chosen) ink else muted,
                            modifier = Modifier
                                .selectable(selected = chosen, role = Role.RadioButton, onClick = { sort = option })
                                .padding(vertical = Tokens.Spacing.xxs),
                        )
                    }
                }
            }
            val blindSpot = ordered.maxByOrNull { it.gap }
            if (totalRead > 0 && blindSpot != null && blindSpot.gap > 0.08f) {
                Text(
                    "Widest gap: ${blindSpot.topic.placeholderLabel} — ${(blindSpot.flowedShare * 100).roundToInt()}% flowed, ${(blindSpot.readShare * 100).roundToInt()}% read.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
            for (row in ordered) {
                DumbbellRow(row = row, maxShare = maxShare, ink = ink, muted = muted)
            }
            // A whisper of a legend for the two dots.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Tokens.Spacing.xs),
            ) {
                DotKey(filled = false, label = "flowed", ink = ink, muted = muted)
                DotKey(filled = true, label = "read", ink = ink, muted = muted)
            }
        }
    }
}

/**
 * The globe opened up (owner #6b): the world as a flat longitude strip, each
 * region shaded by how much of your reading came from it — darkest is where you
 * read most, faint where least. Below it, the region and topic filter, moved
 * here from Edit (owner #3): tap a region to aim there, tap topics to narrow.
 */
@Composable
private fun RegionsSection(
    reads: Map<Region, Int>,
    filter: ReaderFilter,
    onSetRegion: (Region) -> Unit,
    onToggleTopic: (String) -> Unit,
    ink: Color,
    muted: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
        SectionHeading("Regions")
        RegionHeatStrip(reads = reads, selected = filter.region, ink = ink)
        RegionChips(selected = filter.region, onSet = onSetRegion, ink = ink, muted = muted)
        TopicChips(topicKeys = filter.topicKeys, onToggle = onToggleTopic, ink = ink, muted = muted)
    }
}

/** The read-by-region heatmap: the earth unrolled to a longitude bar, each sector shaded by read volume. */
@Composable
private fun RegionHeatStrip(reads: Map<Region, Int>, selected: Region, ink: Color) {
    val maxV = reads.entries.filter { it.key != Region.GLOBAL }.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(Tokens.Radius.sm)),
    ) {
        val w = size.width
        val h = size.height
        fun x(lon: Double) = (((lon + 180.0) / 360.0) * w).toFloat()
        drawRect(ink.copy(alpha = 0.06f), size = size)
        for (r in Region.entries) {
            if (r == Region.GLOBAL) continue
            val left = x(r.fromLon)
            val right = x(r.toLon)
            if (right <= left) continue
            val v = reads[r] ?: 0
            val a = if (v == 0) 0.06f else 0.16f + 0.6f * (v.toFloat() / maxV)
            drawRect(ink.copy(alpha = a), topLeft = Offset(left, 0f), size = Size(right - left, h))
            drawLine(ink.copy(alpha = 0.12f), Offset(right, 0f), Offset(right, h), strokeWidth = 1.dp.toPx())
        }
        // Outline the aimed sector (the whole strip when Global).
        if (selected == Region.GLOBAL) {
            drawRect(ink.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
        } else {
            val left = x(selected.fromLon)
            val right = x(selected.toLon)
            drawRect(ink.copy(alpha = 0.7f), topLeft = Offset(left, 0f), size = Size((right - left).coerceAtLeast(2f), h), style = Stroke(1.5.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionChips(selected: Region, onSet: (Region) -> Unit, ink: Color, muted: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        for (region in Region.entries) {
            val chosen = region == selected
            Text(
                region.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                color = if (chosen) ink else muted,
                modifier = Modifier
                    .clickable { onSet(region) }
                    .padding(vertical = Tokens.Spacing.xxs),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicChips(topicKeys: Set<String>, onToggle: (String) -> Unit, ink: Color, muted: Color) {
    val allTopics = topicKeys.isEmpty()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        for (topic in Topic.entries) {
            // With no explicit selection every topic is in play, so show them
            // all lit; once you pick any, only the picked ones read as chosen.
            val chosen = allTopics || topic.key in topicKeys
            Text(
                topic.placeholderLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (topic.key in topicKeys) FontWeight.Bold else FontWeight.Normal,
                color = if (chosen) ink else muted,
                modifier = Modifier
                    .clickable { onToggle(topic.key) }
                    .padding(vertical = Tokens.Spacing.xxs),
            )
        }
    }
}

/**
 * The funnel as one bar: the whole track is what flowed (faintest), the filter
 * sits over it (mid), the read sits over that (solid) — nested left-aligned, so
 * the bar simply *shrinks* through the two omissions.
 */
@Composable
private fun NestedFunnel(filterFraction: Float, readFraction: Float, ink: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(ink.copy(alpha = 0.22f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(filterFraction.coerceIn(0f, 1f))
                .height(14.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(ink.copy(alpha = 0.5f)),
        )
        Box(
            Modifier
                .fillMaxWidth(readFraction.coerceIn(0f, 1f))
                .height(14.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(ink),
        )
    }
}

@Composable
private fun FunnelLegend(label: String, count: Int, swatch: Color, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(swatch))
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.titleMedium, color = ink)
        }
    }
}

/**
 * One topic as a dumbbell: a faint hollow dot at its share of the stream, a
 * solid dot at its share of your reading, a hairline connecting them. The gap
 * between the dots is the omission, read at a glance.
 */
@Composable
private fun DumbbellRow(row: ContrastRow, maxShare: Float, ink: Color, muted: Color) {
    val topicColor = row.topic.toComposeColor()
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(topicColor))
            Text(
                row.topic.placeholderLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = ink,
                modifier = Modifier.weight(1f).padding(start = Tokens.Spacing.xs),
            )
            Text(
                "${(row.flowedShare * 100).roundToInt()}% / ${(row.readShare * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
        val flowedX = (row.flowedShare / maxShare).coerceIn(0f, 1f)
        val readX = (row.readShare / maxShare).coerceIn(0f, 1f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp),
        ) {
            val y = size.height / 2f
            val pad = 5.dp.toPx()
            val usable = size.width - pad * 2
            val fx = pad + flowedX * usable
            val rx = pad + readX * usable
            // The baseline track.
            drawLine(muted.copy(alpha = 0.25f), Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.dp.toPx())
            // The connector = the gap.
            drawLine(topicColor.copy(alpha = 0.5f), Offset(fx, y), Offset(rx, y), strokeWidth = 2.dp.toPx())
            // Flowed: a hollow ring (the potential).
            drawCircle(topicColor.copy(alpha = 0.55f), radius = 4.dp.toPx(), center = Offset(fx, y), style = Stroke(width = 1.5.dp.toPx()))
            // Read: a solid dot (the actual).
            drawCircle(topicColor, radius = 4.5.dp.toPx(), center = Offset(rx, y))
        }
    }
}

@Composable
private fun DotKey(filled: Boolean, label: String, ink: Color, muted: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Canvas(Modifier.size(10.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            if (filled) {
                drawCircle(ink, radius = 4.dp.toPx(), center = c)
            } else {
                drawCircle(ink, radius = 3.5.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
    }
}
