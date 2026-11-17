package xyz.mdhv.riverwip.inference.local.kokoro

/**
 * Desktop-test double for the Android-only KokoroLexicon (which needs a Context to read
 * its bundled asset). Same package, name and member signatures as the two members the
 * pure Kokoro files call; exact-match lookup only. It is NOT the real lexicon's case
 * folding, so tests using it characterize the callers, not the dictionary.
 */
class KokoroLexicon(private val entries: Map<String, String>) {
    fun phonemesFor(word: String): String? = entries[word]
    fun letterName(letter: Char): String? = entries[letter.uppercaseChar().toString()]
}
