package xyz.mdhv.riverwip.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A widget's spoken description is everything it shows, in order, then what
 * a tap does. A clickable container's description replaces its children's
 * text for TalkBack, so nothing shown may be left out of it.
 */
class WidgetSpeechTest {

    private val comma: (CharSequence, CharSequence) -> CharSequence = { a, b -> "$a, $b" }

    @Test fun everyPartIsSpokenInOrderWithTheActionLast() {
        val spoken = WidgetSpeech.join(
            listOf("Coverage and breadth", null, "Coverage, 7%", "Breadth: you, 2.4", "As of 4 Nov, 14:05", "Opens EDIT › Region & Topics"),
            comma,
        )
        assertEquals("Coverage and breadth, Coverage, 7%, Breadth: you, 2.4, As of 4 Nov, 14:05, Opens EDIT › Region & Topics", spoken)
    }

    @Test fun blankPartsAreSkipped() {
        assertEquals("Clippings, Open your clippings", WidgetSpeech.join(listOf("Clippings", "", "  ", null, "Open your clippings"), comma))
        assertEquals("", WidgetSpeech.join(listOf(null, ""), comma))
    }

    @Test fun aSentenceEndStartsTheNextPartAsItsOwnSentence() {
        assertEquals(
            "Week of 2 Nov: 40 flowed, 3 read. 40 flowed · 3 read · 12 sources",
            WidgetSpeech.join(listOf("Week of 2 Nov: 40 flowed, 3 read.", "40 flowed · 3 read · 12 sources"), comma),
        )
        // The Devanagari full stop and the Arabic question mark end sentences too.
        assertEquals("एक। दो", WidgetSpeech.join(listOf("एक।", "दो"), comma))
        assertEquals("واحد؟ اثنان", WidgetSpeech.join(listOf("واحد؟", "اثنان"), comma))
    }

    @Test fun theLanguagesOwnCommaJoinsParts() {
        val arabic: (CharSequence, CharSequence) -> CharSequence = { a, b -> "$a، $b" }
        assertEquals("أ، ب", WidgetSpeech.join(listOf("أ", "ب"), arabic))
    }
}
