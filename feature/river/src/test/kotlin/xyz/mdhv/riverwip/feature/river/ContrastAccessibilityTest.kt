package xyz.mdhv.riverwip.feature.river

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.riverwip.model.Region

/**
 * The read-by-region map, asserted rather than asserted-about.
 *
 * `RegionHeatStrip` was a bare `Canvas`. A Canvas publishes nothing to the
 * accessibility tree, so the entire answer to "where in the world have I been
 * reading?" — the question the Contrast ledger exists to put in front of you —
 * was carried by nothing but the relative darkness of eight rectangles. Silent
 * to a screen reader, and unreadable to anyone who cannot separate two close
 * greys, which is the same information loss by a different route.
 *
 * As with [DayLoomAccessibilityTest], this proves the tree carries the numbers.
 * It does not prove what TalkBack utters, and no device here can.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContrastAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    /** Descriptions declared anywhere in the tree, in tree order. */
    private fun descriptions(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.let { out += it.joinToString(" ") }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return out
    }

    private fun heatStripDescription(): String =
        descriptions().firstOrNull { it.startsWith("Reading by region") }
            ?: error("the region map publishes nothing; descriptions were: ${descriptions()}")

    private fun setStrip(reads: Map<Region, Int>, selected: Region = Region.GLOBAL) {
        compose.setContent {
            RegionHeatStrip(reads = reads, selected = selected, ink = androidx.compose.ui.graphics.Color.Black)
        }
    }

    @Test fun everyRegionWithAReadIsNamedWithItsCount() {
        setStrip(
            mapOf(
                Region.SOUTH_ASIA to 14,
                Region.EUROPE_AFRICA to 3,
                Region.AMERICAS to 9,
            ),
        )
        val spoken = heatStripDescription()

        assertTrue("names the densest region and its count: $spoken", spoken.contains("14"))
        assertTrue("names a quieter one too: $spoken", spoken.contains("3"))
        assertTrue("gives the total: $spoken", spoken.contains("26"))
    }

    @Test fun theSelectionIsSaidOutLoudRatherThanOnlyHighlighted() {
        setStrip(mapOf(Region.EUROPE_AFRICA to 2), selected = Region.EUROPE_AFRICA)
        val spoken = heatStripDescription()

        assertTrue(
            "the current sector is spoken, not left to a highlight: $spoken",
            spoken.contains(Region.EUROPE_AFRICA.label),
        )
    }

    @Test fun anEmptyMapStillSaysSomethingTrue() {
        setStrip(emptyMap())
        val spoken = heatStripDescription()

        assertTrue("says there is nothing yet: $spoken", spoken.contains("Nothing read yet"))
        // And does not claim a total it does not have.
        assertTrue("no invented count: $spoken", !spoken.contains(" 0 "))
    }

    @Test fun regionsWithNoReadsAreNotEnumerated() {
        setStrip(mapOf(Region.SOUTH_ASIA to 5))
        val spoken = heatStripDescription()

        // Reading eight zeroes aloud before the one number that matters buries
        // the answer; the description names only what actually happened.
        assertTrue("only the region with reads is named: $spoken", spoken.contains("South Asia"))
        assertTrue(
            "a region with nothing read is left out: $spoken",
            !spoken.contains("${Region.EUROPE_AFRICA.label} 0"),
        )
    }
}
