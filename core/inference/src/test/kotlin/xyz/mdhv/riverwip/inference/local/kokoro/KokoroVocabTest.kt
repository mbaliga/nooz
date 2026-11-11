package xyz.mdhv.riverwip.inference.local.kokoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHARACTERIZATION (current behaviour, not a reference-Kokoro oracle): the expected
 * values below were recorded from the code as it stands, so they pin it against
 * accidental change. They do not prove the ids match the real tokenizer.json.
 */
class KokoroVocabTest {

    @Test fun constantsAreRecorded() {
        assertEquals(0, KokoroVocab.PAD)
        assertEquals(512, KokoroVocab.MAX_CONTEXT)
        assertEquals(509, KokoroVocab.MAX_PHONEME_CHARS)
    }

    @Test fun tokenizeMapsKnownCharacters() {
        assertEquals(listOf(50, 83, 54, 57, 16, 65, 60, 54, 46), KokoroVocab.tokenize("həlo wrld"))
        assertEquals(listOf(156, 83, 4, 5, 6), KokoroVocab.tokenize("ˈə.!?"))
    }

    @Test fun tokenizeDropsUnsupportedCharacters() {
        // Digits, '-' and uppercase letters outside the table are dropped, not mapped.
        assertEquals(listOf(43), KokoroVocab.tokenize("1-a2"))
        assertEquals(emptyList<Int>(), KokoroVocab.tokenize(""))
        assertEquals(emptyList<Int>(), KokoroVocab.tokenize("0123456789"))
    }

    @Test fun supportsMatchesTable() {
        assertTrue(KokoroVocab.supports('a'))
        assertTrue(KokoroVocab.supports(' '))
        assertTrue(KokoroVocab.supports('ˈ'))
        assertFalse(KokoroVocab.supports('1'))
        assertFalse(KokoroVocab.supports('-'))
        assertFalse(KokoroVocab.supports('B'))
    }

    @Test fun dollarMapsToPadId() {
        // '$' is id 0, the same value as PAD; recorded as-is.
        assertEquals(listOf(0), KokoroVocab.tokenize("$"))
    }
}
