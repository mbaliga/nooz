package xyz.mdhv.riverwip.model

/**
 * Pure geometry for the river's hourglass flow (owner's Viz mock, 2026-07):
 * per-source supply streams fan in across the top, converge to a thin waist,
 * and what was actually read fans back out across the bottom, by topic. The
 * waist *is* the argument — the whole supply narrows to the sliver that got
 * read — so its width is the honest ratio `totalRead / totalStream` of the top
 * band (floored to a hairline so it never vanishes entirely).
 *
 * All output is normalized to `[0, 1]` in both axes; the Compose layer maps
 * fractions to pixels and draws the curves. No layout math lives in Compose,
 * so this is unit-tested without Android (same rule as [RiverLayout]).
 */
object RiverFlowLayout {

    /** One supply stream at the top: a source's share of everything that flowed. */
    data class SourceStream(val sourceId: String, val xCenter: Double, val width: Double, val count: Int)

    /** One consumption stream at the bottom: a topic's share of everything read. */
    data class TopicStream(val topic: Topic, val xCenter: Double, val width: Double, val count: Int)

    data class Flow(
        val sources: List<SourceStream>,
        val topics: List<TopicStream>,
        val totalStream: Int,
        val totalRead: Int,
        /** Waist width as a fraction of the full canvas width. */
        val waistWidth: Double,
    )

    /** Top/bottom stream bands span this fraction of the canvas width, centred. */
    const val BAND_WIDTH = 0.86
    /** The waist never collapses below this, or the thread of what-was-read disappears. */
    const val MIN_WAIST = 0.004

    fun layout(aggregate: WeeklyAggregate): Flow {
        val totalStream = aggregate.streamCountsByTopic.values.sum()
        val totalRead = aggregate.readCountsByTopic.values.sum().coerceAtMost(totalStream)

        val sourceCounts = aggregate.sourceCounts.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
        val sources = spread(sourceCounts) { sourceId, count, x, w -> SourceStream(sourceId, x, w, count) }

        val topicCounts = Topic.entries.mapNotNull { topic ->
            val read = aggregate.readCountsByTopic[topic.key] ?: 0
            if (read > 0) topic to read else null
        }
        val topics = spread(topicCounts) { topic, count, x, w -> TopicStream(topic, x, w, count) }

        val waist = if (totalStream == 0) {
            0.0
        } else {
            (BAND_WIDTH * totalRead / totalStream).coerceAtLeast(if (totalRead > 0) MIN_WAIST else 0.0)
        }
        return Flow(sources, topics, totalStream, totalRead, waist)
    }

    /**
     * Distribute weighted items across the band: each stream's width is its
     * proportional share of [BAND_WIDTH], and streams sit flush left-to-right
     * (the gaps in the mock come from the curves diverging, not band spacing).
     */
    private fun <T, R> spread(items: List<Pair<T, Int>>, build: (T, Int, Double, Double) -> R): List<R> {
        val total = items.sumOf { it.second }
        if (total <= 0) return emptyList()
        val left = (1.0 - BAND_WIDTH) / 2.0
        var cursor = left
        return items.map { (item, count) ->
            val width = BAND_WIDTH * count / total
            val stream = build(item, count, cursor + width / 2.0, width)
            cursor += width
            stream
        }
    }
}

/** Compact count label for the flow's totals: 950 → "950", 10_000 → "10k", 12_345 → "12.3k". */
fun formatCompactCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> {
        val tenths = count / 100 // count in units of 0.1k
        if (tenths % 10 == 0) "${tenths / 10}k" else "${tenths / 10}.${tenths % 10}k"
    }
    else -> {
        val tenths = count / 100_000
        if (tenths % 10 == 0) "${tenths / 10}M" else "${tenths / 10}.${tenths % 10}M"
    }
}
