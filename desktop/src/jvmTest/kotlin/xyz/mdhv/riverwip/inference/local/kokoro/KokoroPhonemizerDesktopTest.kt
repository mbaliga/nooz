package xyz.mdhv.riverwip.inference.local.kokoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHARACTERIZATION (current behaviour, not a reference-Kokoro oracle) of
 * [KokoroPhonemizer] and [KokoroLetterToSound.spellAcronym], against a tiny map-backed
 * lexicon test double (see KokoroLexiconTestDouble.kt), not the bundled dictionary.
 */
class KokoroPhonemizerDesktopTest {
    private val lexicon = KokoroLexicon(
        mapOf("hello" to "həlˈO", "world" to "wˈɜɹld", "N" to "ˈɛn", "A" to "ˈA", "S" to "ˈɛs"),
    )
    private val phonemizer = KokoroPhonemizer(lexicon)

    @Test fun lexiconWordsPunctuationAndSpacing() {
        assertEquals(listOf(PhonemeChunk("həlˈO, wˈɜɹld!")), phonemizer.phonemize("hello, world!"))
    }

    @Test fun emptyInputGivesNoChunks() {
        assertEquals(emptyList<PhonemeChunk>(), phonemizer.phonemize("   "))
    }

    @Test fun numbersAreExpandedToWordsFirst() {
        val out = phonemizer.phonemize("7").single().phonemes
        assertEquals(KokoroLetterToSound.approximate("seven"), out)
    }

    @Test fun curlyApostropheAndAccentsFold() {
        assertEquals(phonemizer.phonemize("don't cafe"), phonemizer.phonemize("don’t café"))
    }

    @Test fun acronymSpelledFromLetterNames() {
        // Earlier letters' primary stress is demoted to secondary; the last keeps it.
        assertEquals("ˌɛnˌAˌɛsˈA", KokoroLetterToSound.spellAcronym("NASA", lexicon))
        assertNull(KokoroLetterToSound.spellAcronym("NAZ", lexicon))
        assertNull(KokoroLetterToSound.spellAcronym("123", lexicon))
    }

    @Test fun longTextSplitsIntoChunksWithinTheLimit() {
        val text = (1..200).joinToString(" ") { "hello world." }
        val chunks = phonemizer.phonemize(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.phonemes.length <= KokoroVocab.MAX_PHONEME_CHARS })
        // Chunks break at sentence ends, so each one ends with the full stop.
        assertTrue(chunks.all { it.phonemes.endsWith(".") })
    }
}
