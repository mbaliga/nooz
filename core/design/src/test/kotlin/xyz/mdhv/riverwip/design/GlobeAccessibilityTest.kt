package xyz.mdhv.riverwip.design

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.riverwip.model.Topic

/**
 * The region globe, asserted rather than asserted-about.
 *
 * It told a screen-reader user to *"Drag to spin, pinch to widen the band"* —
 * two gestures, neither of which a screen reader can make — and offered no
 * action of any kind in their place. The sector chips beneath it cover picking
 * a region, so that half had a door; the band width had none at all, and the
 * topic-mix ring, which is the only place the aimed region's real counts are
 * drawn, existed nowhere in speech.
 *
 * Like the Loom's tests, these prove the semantics tree carries the data and
 * the actions. They do not prove what TalkBack utters; no device here can.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobeAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private var spins = mutableListOf<Double>()
    private var zooms = mutableListOf<Double>()

    private fun setGlobe(
        yaw: Double = 78.0,
        bandHalf: Double = 20.0,
        ringMix: Map<Topic, Int> = mapOf(
            Topic.POLITICS to 30,
            Topic.BUSINESS to 8,
            Topic.SPORT to 2,
        ),
    ) {
        compose.setContent {
            GlobeCanvas(
                yaw = yaw,
                pitch = 0.0,
                bandHalf = bandHalf,
                ringMix = ringMix,
                onSpin = { dYaw, _ -> spins += dYaw },
                onZoomBand = { zooms += it },
            )
        }
    }

    /**
     * GlobeCanvas puts its description and actions on a
     * `semantics(mergeDescendants = true)` Box below the host's own root node,
     * so this walks to the first node that declares one rather than hard-coding
     * the tree's shape.
     */
    private fun globeNode(): SemanticsNode {
        fun walk(node: SemanticsNode): SemanticsNode? {
            if (node.config.contains(SemanticsProperties.ContentDescription)) return node
            for (child in node.children) walk(child)?.let { return it }
            return null
        }
        return walk(compose.onRoot().fetchSemanticsNode())
            ?: error("the globe publishes no contentDescription at all")
    }

    private fun spoken(): String =
        globeNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")

    private fun actions() = globeNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()

    @Test fun bothGesturesHaveANonGestureRoute() {
        setGlobe()
        val labels = actions().map { it.label }

        assertTrue("spinning is reachable: $labels", labels.any { it.contains("Spin") })
        assertTrue("the band is reachable: $labels", labels.any { it.contains("band") })
    }

    @Test fun theActionsActuallyMoveTheGlobe() {
        setGlobe()
        // A label that fires nothing is the defect this whole change is about,
        // so invoke them rather than trusting the list.
        actions().first { it.label == "Spin east" }.action?.invoke()
        actions().first { it.label == "Widen the band" }.action?.invoke()

        assertEquals("spun east by one step", 1, spins.size)
        assertTrue("spun in the positive direction: $spins", spins.single() > 0)
        assertEquals("zoomed once", 1, zooms.size)
        assertTrue("widened rather than narrowed: $zooms", zooms.single() > 1.0)
    }

    @Test fun theRingsCountsExistInSpeech() {
        setGlobe()
        val description = spoken()

        // The ring is the only place these numbers are drawn.
        assertTrue("gives the total: $description", description.contains("40"))
        assertTrue("names the biggest topic with its count: $description", description.contains("Politics 30"))
    }

    @Test fun itNoLongerInstructsGesturesItCannotAccept() {
        setGlobe()
        val description = spoken()

        assertTrue("must not say drag: $description", !description.contains("Drag to spin"))
        assertTrue("must not say pinch: $description", !description.contains("pinch"))
    }

    @Test fun anEmptyRegionStillSaysSomethingTrue() {
        setGlobe(ringMix = emptyMap())
        val description = spoken()

        assertTrue("says nothing flowed: $description", description.contains("Nothing has flowed"))
        assertTrue("still names where it is aimed: $description", description.contains("aimed at"))
    }
}
