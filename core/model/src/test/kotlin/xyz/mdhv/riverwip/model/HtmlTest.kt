package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Html.stripTrailingBoilerplate] mirrors `web/js/articleBoilerplate.js`'s test
 * matrix (both implementations must agree) -- every case is either "this exact
 * junk gets removed" or "this real sentence survives untouched", and the
 * negative cases are what actually prove the end-anchoring works: a real
 * sentence that merely *contains* a trigger word must pass through unchanged,
 * because nothing about it reaches the end of the string.
 */
class HtmlTest {

    @Test fun stripsAWordpressSyndicationFooter() {
        val s = "The city council voted 5-2 to approve the budget. The post City council approves budget appeared first on The Daily Record."
        assertEquals("The city council voted 5-2 to approve the budget.", Html.stripTrailingBoilerplate(s))
    }

    @Test fun stripsABareContinueReadingLabelWithATrailingArrow() {
        assertEquals(
            "The storm is expected to clear by Tuesday.",
            Html.stripTrailingBoilerplate("The storm is expected to clear by Tuesday. Continue reading →"),
        )
        assertEquals(
            "The storm is expected to clear by Tuesday.",
            Html.stripTrailingBoilerplate("The storm is expected to clear by Tuesday. Read More »"),
        )
        assertEquals(
            "The storm is expected to clear by Tuesday.",
            Html.stripTrailingBoilerplate("The storm is expected to clear by Tuesday. Read the full story..."),
        )
    }

    @Test fun stripsAStandaloneAdNetworkLabel() {
        assertEquals(
            "Some genuine excerpt text here that is long enough.",
            Html.stripTrailingBoilerplate("Some genuine excerpt text here that is long enough. Advertisement"),
        )
        assertEquals(
            "Some genuine excerpt text here that is long enough.",
            Html.stripTrailingBoilerplate("Some genuine excerpt text here that is long enough. Sponsored Content"),
        )
    }

    @Test fun stripsATrailingWordpressExcerptTruncationMarker() {
        assertEquals(
            "The mayor spoke for twenty minutes about the new budget",
            Html.stripTrailingBoilerplate("The mayor spoke for twenty minutes about the new budget […]"),
        )
    }

    @Test fun stripsABareTrailingTrackingLink() {
        assertEquals(
            "Full details are available now.",
            Html.stripTrailingBoilerplate("Full details are available now. https://example.com/track?id=abc123"),
        )
    }

    @Test fun stripsMoreThanOneChainedFragment() {
        val s = "Local officials confirmed the closure will last through the weekend. […] Continue reading →"
        assertEquals("Local officials confirmed the closure will last through the weekend.", Html.stripTrailingBoilerplate(s))
    }

    @Test fun leavesARealSentenceContainingTheSameWordsAlone() {
        assertEquals(
            "The reporter continued reading the transcript for another hour before filing the story.",
            Html.stripTrailingBoilerplate("The reporter continued reading the transcript for another hour before filing the story."),
        )
        assertEquals(
            "Read more about the case at the courthouse tomorrow, officials said.",
            Html.stripTrailingBoilerplate("Read more about the case at the courthouse tomorrow, officials said."),
        )
        assertEquals(
            "Sponsored data plans are becoming more common in emerging markets, the report found.",
            Html.stripTrailingBoilerplate("Sponsored data plans are becoming more common in emerging markets, the report found."),
        )
    }

    @Test fun leavesAPlainNoBoilerplateSentenceCompletelyUntouched() {
        val s = "City council approved the new budget on a 5-2 vote after a lengthy debate."
        assertEquals(s, Html.stripTrailingBoilerplate(s))
    }

    @Test fun doesNotEatTheRealSentencesOwnTerminalPeriod() {
        // Regression: the trim charset used to include '.', which ate the real
        // sentence's own terminal punctuation along with the boilerplate cut.
        val s = "The city council voted 5-2 to approve the budget. The post City council approves budget appeared first on The Daily Record."
        assertTrue(Html.stripTrailingBoilerplate(s).endsWith("budget."))
    }

    @Test fun anEntirelyBoilerplateStringReducesToBlank() {
        assertEquals("", Html.stripTrailingBoilerplate("The post Foo Bar appeared first on Example News."))
        assertEquals("", Html.stripTrailingBoilerplate("  Advertisement  "))
    }

    // Honest limit, not a bug: a genuine citation that happens to end a
    // paragraph with a bare URL and no label reads identically to a tracking
    // link with no label, and this -- deliberately -- cannot tell them apart.
    @Test fun honestLimitABareTrailingUrlCitationIsRemovedToo() {
        assertEquals(
            "The full text of the ruling is available at",
            Html.stripTrailingBoilerplate("The full text of the ruling is available at https://legit-court.gov/opinions/2026-441.pdf"),
        )
    }
}
