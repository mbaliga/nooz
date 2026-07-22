package xyz.mdhv.riverwip.inference.local.kokoro

/**
 * The safety net for words [KokoroLexicon] genuinely has no entry for —
 * misaki's own reference pipeline falls back to espeak-ng here (owner docs),
 * which this app can't bundle (a native C library with no published
 * Android/Maven artifact, unlike onnxruntime-android). This is a real,
 * from-scratch letter-to-sound approximation instead: common digraphs first,
 * then a default phoneme per letter. It is expected to sound approximate on
 * genuinely unusual words (rare given [KokoroLexicon]'s ~173k-word coverage
 * already handles ordinary running text) — never silence, never a crash, but
 * not misaki/espeak-ng parity either. Also spells out unrecognised all-caps
 * runs letter by letter (e.g. an acronym the dictionary has no entry for),
 * reusing the dictionary's own real letter-name entries rather than a
 * separate guessed table.
 */
object KokoroLetterToSound {

    /** Reads an all-caps word letter by letter using the dictionary's own letter-name entries (e.g. "N" → "ˈɛn"). Null if any letter is missing one (never mixes real and guessed data). */
    fun spellAcronym(word: String, lexicon: KokoroLexicon): String? {
        val letters = word.filter { it.isLetter() }
        if (letters.isEmpty()) return null
        val names = letters.map { lexicon.letterName(it) ?: return null }
        // Primary stress lands on the final letter's own name (already
        // carries it, e.g. "N" -> "ˈɛn"); every earlier letter's own primary
        // stress is demoted to secondary rather than stripped outright, so
        // an acronym reads as one word with one primary stress instead of
        // several unstressed syllables run together (e.g. "N-A-S-A").
        return names.dropLast(1).joinToString("") { it.replace('ˈ', 'ˌ') } + names.last()
    }

    private val DIGRAPHS = listOf(
        "tion" to "ʃən", "sion" to "ʒən", "ough" to "ʌf", "augh" to "ɔ",
        "ch" to "ʧ", "sh" to "ʃ", "th" to "θ", "ph" to "f", "wh" to "w", "ng" to "ŋ", "ck" to "k", "qu" to "kw",
        "igh" to "aɪ", "ee" to "i", "ea" to "i", "ai" to "eɪ", "ay" to "eɪ", "oa" to "oʊ", "ow" to "aʊ",
        "ou" to "aʊ", "oy" to "ɔɪ", "oi" to "ɔɪ", "oo" to "u",
    )

    private val LETTER_DEFAULT = mapOf(
        'b' to "b", 'c' to "k", 'd' to "d", 'f' to "f", 'g' to "ɡ", 'h' to "h", 'j' to "ʤ", 'k' to "k",
        'l' to "l", 'm' to "m", 'n' to "n", 'p' to "p", 'q' to "k", 'r' to "ɹ", 's' to "s", 't' to "t",
        'v' to "v", 'w' to "w", 'x' to "ks", 'y' to "j", 'z' to "z",
        'a' to "æ", 'e' to "ɛ", 'i' to "ɪ", 'o' to "ɑ", 'u' to "ʌ",
    )
    private val VOWELS = "aeiouAEIOU".toSet()

    /** A best-effort phoneme string for a word not found anywhere in [KokoroLexicon]. */
    fun approximate(word: String): String {
        val w = word.lowercase().filter { it.isLetter() }
        if (w.isEmpty()) return ""
        val silentE = w.length > 2 && w.endsWith("e") && w[w.length - 2] !in VOWELS
        val body = if (silentE) w.dropLast(1) else w

        val sb = StringBuilder()
        var i = 0
        var stressed = false
        while (i < body.length) {
            val digraph = DIGRAPHS.firstOrNull { (g, _) -> body.startsWith(g, i) }
            val (phoneme, consumed) = if (digraph != null) {
                digraph.second to digraph.first.length
            } else {
                (LETTER_DEFAULT[body[i]] ?: "") to 1
            }
            // Primary stress on the first vowel sound (a plain, common-case default — real dictionary entries already carry misaki's actual stress, this only ever runs for words neither lexicon has).
            if (!stressed && phoneme.isNotEmpty() && phoneme.any { it in "æɛɪɑʌaeiouɔʊ" }) {
                sb.append('ˈ')
                stressed = true
            }
            sb.append(phoneme)
            i += consumed
        }
        return sb.toString()
    }
}
