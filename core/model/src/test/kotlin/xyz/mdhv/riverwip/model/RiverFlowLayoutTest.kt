package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiverFlowLayoutTest {

    private fun agg(
        stream: Map<Topic, Int>,
        read: Map<Topic, Int>,
        sources: Map<String, Int>,
    ) = WeeklyAggregate(
        weekStart = 0L,
        streamCountsByTopic = stream.mapKeys { it.key.key },
        readCountsByTopic = read.mapKeys { it.key.key },
        sourceCounts = sources,
    )

    @Test fun emptyAggregateYieldsEmptyFlow() {
        val flow = RiverFlowLayout.layout(agg(emptyMap(), emptyMap(), emptyMap()))
        assertTrue(flow.sources.isEmpty())
        assertTrue(flow.topics.isEmpty())
        assertEquals(0.0, flow.waistWidth, 1e-12)
    }

    @Test fun sourceWidthsAreProportionalAndSumToBand() {
        val flow = RiverFlowLayout.layout(
            agg(
                stream = mapOf(Topic.POLITICS to 300),
                read = emptyMap(),
                sources = mapOf("a" to 100, "b" to 200),
            ),
        )
        assertEquals(2, flow.sources.size)
        // Sorted by count descending: b first.
        assertEquals("b", flow.sources[0].sourceId)
        assertEquals(flow.sources[0].width, flow.sources[1].width * 2, 1e-9)
        assertEquals(RiverFlowLayout.BAND_WIDTH, flow.sources.sumOf { it.width }, 1e-9)
    }

    @Test fun topicStreamsCoverOnlyReadTopicsAndSumToBand() {
        val flow = RiverFlowLayout.layout(
            agg(
                stream = mapOf(Topic.POLITICS to 50, Topic.SPORT to 50, Topic.TECH to 50),
                read = mapOf(Topic.POLITICS to 3, Topic.SPORT to 1),
                sources = mapOf("a" to 150),
            ),
        )
        assertEquals(listOf(Topic.POLITICS, Topic.SPORT), flow.topics.map { it.topic })
        assertEquals(RiverFlowLayout.BAND_WIDTH, flow.topics.sumOf { it.width }, 1e-9)
        assertEquals(flow.topics[0].width, flow.topics[1].width * 3, 1e-9)
    }

    @Test fun waistIsTheHonestReadRatio() {
        val flow = RiverFlowLayout.layout(
            agg(
                stream = mapOf(Topic.POLITICS to 1000),
                read = mapOf(Topic.POLITICS to 100),
                sources = mapOf("a" to 1000),
            ),
        )
        assertEquals(RiverFlowLayout.BAND_WIDTH * 0.1, flow.waistWidth, 1e-9)
    }

    @Test fun tinyReadCountStillLeavesAVisibleWaist() {
        val flow = RiverFlowLayout.layout(
            agg(
                stream = mapOf(Topic.POLITICS to 100_000),
                read = mapOf(Topic.POLITICS to 1),
                sources = mapOf("a" to 100_000),
            ),
        )
        assertTrue(flow.waistWidth >= RiverFlowLayout.MIN_WAIST)
    }

    @Test fun zeroReadsMeansZeroWaistAndNoTopicStreams() {
        val flow = RiverFlowLayout.layout(
            agg(
                stream = mapOf(Topic.POLITICS to 500),
                read = emptyMap(),
                sources = mapOf("a" to 500),
            ),
        )
        assertEquals(0.0, flow.waistWidth, 1e-12)
        assertTrue(flow.topics.isEmpty())
        assertEquals(500, flow.totalStream)
        assertEquals(0, flow.totalRead)
    }

    @Test fun streamsStayInsideTheUnitCanvas() {
        val flow = RiverFlowLayout.layout(
            agg(
                stream = mapOf(Topic.POLITICS to 60, Topic.TECH to 40),
                read = mapOf(Topic.POLITICS to 5, Topic.TECH to 5),
                sources = mapOf("a" to 10, "b" to 30, "c" to 60),
            ),
        )
        for (s in flow.sources) {
            assertTrue(s.xCenter - s.width / 2 >= 0.0 - 1e-9)
            assertTrue(s.xCenter + s.width / 2 <= 1.0 + 1e-9)
        }
        for (t in flow.topics) {
            assertTrue(t.xCenter - t.width / 2 >= 0.0 - 1e-9)
            assertTrue(t.xCenter + t.width / 2 <= 1.0 + 1e-9)
        }
    }

    @Test fun layoutIsDeterministicForTiedCounts() {
        val sources = mapOf("zeta" to 10, "alpha" to 10, "mid" to 10)
        val a = RiverFlowLayout.layout(agg(mapOf(Topic.OTHER to 30), emptyMap(), sources))
        val b = RiverFlowLayout.layout(agg(mapOf(Topic.OTHER to 30), emptyMap(), sources))
        assertEquals(a.sources.map { it.sourceId }, b.sources.map { it.sourceId })
        assertEquals(listOf("alpha", "mid", "zeta"), a.sources.map { it.sourceId }) // ties break by id
    }

    @Test fun compactCountFormatting() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("950", formatCompactCount(950))
        assertEquals("1k", formatCompactCount(1_000))
        assertEquals("1.5k", formatCompactCount(1_500))
        assertEquals("10k", formatCompactCount(10_000))
        assertEquals("12.3k", formatCompactCount(12_345))
        assertEquals("999.9k", formatCompactCount(999_999))
        assertEquals("1M", formatCompactCount(1_000_000))
        assertEquals("2.5M", formatCompactCount(2_500_000))
    }
}
