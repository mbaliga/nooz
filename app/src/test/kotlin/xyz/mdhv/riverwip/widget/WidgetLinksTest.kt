package xyz.mdhv.riverwip.widget

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A widget tap's extras are validated before the app acts on them: an unknown
 * destination, or an article tap without a plausible item id, opens nothing
 * special rather than something guessed.
 */
class WidgetLinksTest {

    @Test fun everyDestinationRoundTripsByName() {
        for (destination in WidgetDestination.entries) {
            val request = WidgetLinks.parse(destination.name, "abc123")
            val expectedId = if (destination == WidgetDestination.ITEM || destination == WidgetDestination.CLIPPING) "abc123" else null
            assertEquals(WidgetRequest(destination, expectedId), request)
        }
    }

    @Test fun unknownOrMissingDestinationsAreIgnored() {
        assertNull(WidgetLinks.parse(null, null))
        assertNull(WidgetLinks.parse("", null))
        assertNull(WidgetLinks.parse("stand", null)) // names are exact
        assertNull(WidgetLinks.parse("DELETE_EVERYTHING", null))
    }

    @Test fun articleTapsNeedAPlausibleItemId() {
        assertNull(WidgetLinks.parse("ITEM", null))
        assertNull(WidgetLinks.parse("ITEM", "   "))
        assertNull(WidgetLinks.parse("CLIPPING", "x".repeat(257)))
        assertEquals(WidgetRequest(WidgetDestination.ITEM, "a1"), WidgetLinks.parse("ITEM", " a1 "))
    }

    @Test fun onlyTapsThroughTheNonExportedAliasAreTrusted() {
        assertTrue(WidgetLinks.isTrusted(WidgetLinks.TAP_ALIAS, 0))
        // Another app starting the exported launcher activity directly.
        assertFalse(WidgetLinks.isTrusted("xyz.mdhv.riverwip.MainActivity", 0))
        assertFalse(WidgetLinks.isTrusted(null, 0))
        // Recents relaunching a task a widget tap once started: the same tap again.
        assertFalse(WidgetLinks.isTrusted(WidgetLinks.TAP_ALIAS, Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }

    @Test fun requestCodesNeverCollideAcrossWidgetsOrSlots() {
        val codes = (1..50).flatMap { id -> (0 until WidgetLinks.SLOTS).map { slot -> WidgetLinks.requestCode(id, slot) } }
        assertEquals(codes.size, codes.toSet().size)
        // An out-of-range slot is clamped rather than spilling into the next widget's codes.
        assertNotEquals(WidgetLinks.requestCode(2, 0), WidgetLinks.requestCode(1, 99))
    }
}
