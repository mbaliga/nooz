package xyz.mdhv.riverwip.inference.local.kokoro

import java.text.Normalizer

/** One inference-ready piece of a longer text, already in Kokoro's phoneme alphabet (unpadded — [LocalKokoroTtsProvider] adds the pad tokens). */
data class PhonemeChunk(val phonemes: String)

/**
 * Text in, Kokoro phonemes out — the piece the owner's ask was actually
 * missing. Dictionary-first (misaki's own real ~173k-word lexicon, see
 * [KokoroLexicon]), falling back to [KokoroLetterToSound] only for words
 * neither lexicon has (misaki's own fallback there is espeak-ng, which this
 * app can't bundle — see that class's own doc comment for what that trade-off
 * means). Also splits long articles into chunks that each fit Kokoro's
 * phoneme-character context window (owner docs), breaking at sentence ends
 * where possible so a chunk boundary never lands mid-thought if it can be
 * helped, and never mid-word or over the model's own hard limit.
 */
class KokoroPhonemizer(private val lexicon: KokoroLexicon) {

    fun phonemize(text: String): List<PhonemeChunk> {
        // Extracted article text is real prose, not the ASCII this class's
        // regex was written against — a typographic apostrophe (’, U+2019)
        // in "don't" would otherwise split it into two unrecognisable
        // fragments ("don" + a silently-dropped character + "t"), since
        // neither TOKEN_PATTERN's word class nor the bundled lexicon's keys
        // use anything but the plain ASCII '. Curly double quotes are left
        // alone — Kokoro's own vocabulary has both straight and curly quote
        // tokens, so those don't need folding.
        //
        // Same problem, worse failure mode, for accented Latin letters: a
        // name like "café" or "Zürich" has a character TOKEN_PATTERN's word
        // class doesn't recognise at all, so it isn't dropped cleanly -- the
        // word itself splits into two separate, unrelated fragments around
        // the accented letter ("café" -> "caf", with the "é" vanishing
        // silently). NFKD-decomposing first turns "é" into "e" plus a
        // separate combining accent mark, which the second replace then
        // strips, landing on the plain-ASCII "cafe" -- a real dictionary
        // word (or at worst a clean letter-to-sound guess), not a truncated
        // fragment.
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(DIACRITIC_MARK, "")
            .replace('’', '\'')
            .replace('‘', '\'')

        val chunks = mutableListOf<PhonemeChunk>()
        val current = StringBuilder()
        // Index into `current` right after the most recent sentence-ending
        // punctuation buffered so far, or -1 if none yet — the preferred
        // split point when a chunk has to break, so a break lands between
        // sentences rather than mid-thought whenever the buffer holds one.
        var lastSentenceBreak = -1

        fun emit(piece: String) {
            val trimmed = piece.trim(' ')
            if (trimmed.isNotEmpty()) chunks += PhonemeChunk(trimmed)
        }

        fun fits(fragment: String): Boolean {
            val needsSeparator = current.isNotEmpty() && current.last() != ' ' && fragment.first() != ' '
            return current.length + (if (needsSeparator) 1 else 0) + fragment.length <= KokoroVocab.MAX_PHONEME_CHARS
        }

        fun append(fragment: String, isSentenceEnd: Boolean = false, noLeadingSpace: Boolean = false) {
            if (fragment.isEmpty()) return
            if (!fits(fragment)) {
                if (lastSentenceBreak > 0) {
                    emit(current.substring(0, lastSentenceBreak))
                    val remainder = current.substring(lastSentenceBreak)
                    current.setLength(0)
                    current.append(remainder)
                    lastSentenceBreak = -1
                }
                // Re-check rather than assume the sentence-boundary split was
                // enough: the remainder just kept (an already-open, still
                // unterminated sentence) can itself already be near the cap,
                // and if there was no sentence break at all this is the only
                // remediation — either way, if it still doesn't fit, this is
                // a hard flush so the fragment always starts a clean chunk.
                if (!fits(fragment)) {
                    emit(current.toString())
                    current.setLength(0)
                }
            }
            if (!noLeadingSpace && current.isNotEmpty() && current.last() != ' ' && fragment.first() != ' ') current.append(' ')
            current.append(fragment)
            if (isSentenceEnd) lastSentenceBreak = current.length
        }

        for (token in TOKEN_PATTERN.findAll(normalized)) {
            when {
                token.groups["space"] != null -> append(" ")
                token.groups["number"] != null -> {
                    val words = KokoroNumberExpander.expand(token.value).split(' ', '-').filter { it.isNotEmpty() }
                    for ((i, w) in words.withIndex()) {
                        if (i > 0) append(" ")
                        append(phonemesForWord(w))
                    }
                }
                token.groups["word"] != null -> append(phonemesForWord(token.value))
                token.groups["punct"] != null -> {
                    val c = token.value[0]
                    if (KokoroVocab.supports(c)) {
                        // Ordinary orthography has no space before closing
                        // punctuation ("dog.", not "dog ."); opening marks
                        // (an opening paren/curly-quote) keep the default
                        // separator since they lead into what follows them,
                        // not trail what came before.
                        append(c.toString(), isSentenceEnd = c in SENTENCE_END, noLeadingSpace = c in NO_LEADING_SPACE)
                    }
                }
            }
        }
        emit(current.toString())
        return chunks
    }

    private fun phonemesForWord(word: String): String {
        lexicon.phonemesFor(word)?.let { return it }
        val isAllCaps = word.length > 1 && word == word.uppercase() && word.any { it.isLetter() }
        if (isAllCaps) {
            KokoroLetterToSound.spellAcronym(word, lexicon)?.let { return it }
        }
        return KokoroLetterToSound.approximate(word)
    }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '…')
        val NO_LEADING_SPACE = setOf('.', ',', '!', '?', ';', ':', ')', '”', '…')
        // Combining marks NFKD decomposition splits an accented letter into
        // (base letter, this) -- stripped so "é" folds to plain "e" rather
        // than vanishing as an unrecognised character (see `normalized` above).
        val DIACRITIC_MARK = Regex("\\p{Mn}+")
        val TOKEN_PATTERN = Regex(
            // The lookbehind keeps a '-' from being absorbed as a minus sign
            // when it's actually a dash between two digit runs -- "2020-2021"
            // or "COVID-19" read correctly instead of gaining a spurious
            // "minus" (a real negative number's '-' is preceded by
            // whitespace/start-of-text/punctuation, never a digit or letter,
            // so it's unaffected). The now-unclaimed dash still matches
            // `punct` below and is handled the same honest way as any other
            // punctuation the vocabulary doesn't support.
            "(?<number>(?<![0-9A-Za-z])-?\\$?[0-9][0-9,]*(?:\\.[0-9]+)?%?(?:st|nd|rd|th)?)" +
                "|(?<word>[A-Za-z]+(?:'[A-Za-z]+)*)" +
                "|(?<space>\\s+)" +
                "|(?<punct>[.,!?;:\"()—…“”-])",
        )
    }
}
