package xyz.mdhv.riverwip.feature.river

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
import xyz.mdhv.riverwip.model.DayLoomLayout
import xyz.mdhv.riverwip.model.Topic

/**
 * The Loom, asserted rather than asserted-about.
 *
 * This app's centrepiece was a single silent node. A semantics dump showed its
 * entire action list as `[SetTextSubstitution, ShowTextSubstitution,
 * ClearTextSubstitution, GetTextLayoutResult]` — no click, nothing custom —
 * while its own spoken description ended "Tap a stream for its counts." It
 * instructed a gesture it would not accept, and the per-stream numbers existed
 * nowhere else in speech.
 *
 * These tests exist because "we made it accessible" is a claim, and a claim
 * about a screen reader that nobody can run is worth very little. They do not
 * prove what TalkBack *says* — no device here can — but they do prove the
 * semantics tree carries the data and the actions, which is the part that was
 * actually missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayLoomAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    /** A day where politics flooded the feed and went almost entirely unread. */
    private fun loom(): DayLoomLayout.Loom = DayLoomLayout.layout(
        streamByTopic = mapOf(
            Topic.POLITICS.key to 40,
            Topic.CONFLICT.key to 12,
            Topic.BUSINESS.key to 6,
        ),
        readByTopic = mapOf(
            Topic.POLITICS.key to 2,
            Topic.BUSINESS.key to 3,
        ),
    )

    private fun setLoom() {
        compose.setContent { DayLoomCanvas(loom = loom(), enabledSourceCount = 4) }
    }

    /**
     * The node the Loom actually publishes.
     *
     * `onRoot()` is the host's own node and carries nothing; DayLoomCanvas puts
     * its description and actions on a `semantics(mergeDescendants = true)` Box
     * further down. Walking to the first node that declares a
     * ContentDescription finds it without hard-coding the tree's shape.
     */
    private fun loomNode(): SemanticsNode {
        fun walk(node: SemanticsNode): SemanticsNode? {
            if (node.config.contains(SemanticsProperties.ContentDescription)) return node
            for (child in node.children) walk(child)?.let { return it }
            return null
        }
        return requireNonNull(walk(compose.onRoot().fetchSemanticsNode()))
    }

    private fun requireNonNull(node: SemanticsNode?): SemanticsNode =
        node ?: error("no node in the tree declares a contentDescription — the loom publishes nothing")

    private fun spokenDescription(): String =
        loomNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")

    @Test fun everyStreamIsReachableAsANamedAction() {
        setLoom()
        val actions = loomNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()

        assertTrue("the loom exposes custom actions at all", actions.isNotEmpty())
        val labels = actions.map { it.label }
        // Named with their numbers, not positional: the custom-action menu is
        // read aloud in sequence, so "stream three" would be unusable.
        assertTrue(
            "politics carries its counts in the label: $labels",
            labels.any { it.contains("Politics") && it.contains("40") && it.contains("2") },
        )
        assertTrue(
            "a topic that flowed but was never read is still reachable: $labels",
            labels.any { it.contains("Conflict") && it.contains("12") },
        )
    }

    @Test fun theSpokenSummaryNamesTheSupplySideNotJustWhatWasRead() {
        setLoom()
        val description = spokenDescription()

        // The regression this guards: the summary used to enumerate only
        // `bands.filter { it.consumed }`, so a topic that flooded the feed and
        // was never opened was never named — which is precisely the omission
        // the whole screen exists to show.
        assertTrue("names the unread flood: $description", description.contains("Conflict"))
        assertTrue("gives the supply total: $description", description.contains("58"))
        assertTrue("gives what was read: $description", description.contains("You read 5"))
    }

    @Test fun itNoLongerInstructsAGestureItWillNotAccept() {
        setLoom()
        val description = spokenDescription()

        assertTrue(
            "must not tell a screen-reader user to tap: $description",
            !description.contains("Tap a stream"),
        )
    }

    @Test fun anEmptyDayStillSaysSomethingTrue() {
        compose.setContent {
            DayLoomCanvas(
                loom = DayLoomLayout.layout(streamByTopic = emptyMap(), readByTopic = emptyMap()),
                enabledSourceCount = 3,
            )
        }
        assertEquals("Nothing flowed from your 3 sources this day.", spokenDescription())
    }
}
