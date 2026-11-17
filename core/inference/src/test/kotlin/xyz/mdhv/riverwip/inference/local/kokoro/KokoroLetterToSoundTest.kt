package xyz.mdhv.riverwip.inference.local.kokoro

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CHARACTERIZATION (current behaviour, not a reference-Kokoro oracle) of
 * [KokoroLetterToSound.approximate]. The values pin an approximation, not correct
 * pronunciation. spellAcronym needs a KokoroLexicon (Android) and is not covered here.
 */
class KokoroLetterToSoundTest {

    private fun check(expected: String, word: String) = assertEquals(word, expected, KokoroLetterToSound.approximate(word))

    @Test fun emptyAndNonLetters() {
        check("", "")
        check("", "123")
    }

    @Test fun plainLettersGetFirstVowelStress() {
        check("ˈæb", "ab")
        check("kˈæt", "cat")
        check("bˈɛd", "bed")
    }

    @Test fun digraphsAndSilentE() {
        check("ʃˈɪp", "ship")
        check("θˈɪn", "thin")
        check("tˈi", "tee")
        check("mˈæk", "make") // trailing silent e is dropped, no long-vowel rule
    }

    @Test fun caseIsIgnored() {
        assertEquals(KokoroLetterToSound.approximate("cat"), KokoroLetterToSound.approximate("CAT"))
    }
}
