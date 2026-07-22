package xyz.mdhv.riverwip.inference.local.kokoro

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * The real pronouncing dictionary Kokoro's own reference pipeline reads from
 * (owner docs point at [hexgrad/misaki](https://github.com/hexgrad/misaki)):
 * `misaki`'s bundled `us_gold.json`/`us_silver.json` — ~173k American-English
 * words already spelled in Kokoro's own phoneme alphabet, Apache-2.0
 * licensed, gold entries preferred over silver on overlap. Bundled here
 * gzip-compressed (`assets/kokoro_lexicon_us.tsv.gz`, ~1.2MB) as a flat
 * `word\tphonemes` table; misaki's own POS-dependent homograph entries were
 * collapsed to their `DEFAULT` reading at build time (no POS tagger ships in
 * this app) and case-variant entries misaki auto-derives at import time are
 * instead handled here at lookup time (see [phonemesFor]), keeping the
 * bundled table about half the size.
 *
 * Loaded lazily and once: nothing reads this file until Nooz Cast's first
 * real narration request, matching every other "expensive work is opt-in"
 * pattern in this app.
 */
class KokoroLexicon(private val context: Context) {
    private val entries: Map<String, String> by lazy { load() }

    private fun load(): Map<String, String> {
        val map = HashMap<String, String>(200_000)
        context.assets.open(ASSET_NAME).use { raw ->
            GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    map[line.substring(0, tab)] = line.substring(tab + 1)
                }
            }
        }
        return map
    }

    /**
     * The word's phonemes, or null if genuinely absent — tries the word
     * exactly as written first (misaki's own dictionary keeps a handful of
     * case-sensitive entries, e.g. "US" the country vs. "us" the pronoun,
     * where collapsing case would pick the wrong one), then lowercase, then
     * capitalized, mirroring how misaki folds case at lookup time without
     * needing every variant pre-expanded into the bundled table.
     */
    fun phonemesFor(word: String): String? {
        entries[word]?.let { return it }
        val lower = word.lowercase()
        entries[lower]?.let { return it }
        if (word.length > 1) {
            val capitalized = lower.replaceFirstChar { it.uppercase() }
            entries[capitalized]?.let { return it }
        }
        return null
    }

    /** A single letter's spoken name (e.g. "N" → "ɛn") — the building block for spelling out an unrecognised acronym. */
    fun letterName(letter: Char): String? = entries[letter.uppercaseChar().toString()]

    private companion object {
        const val ASSET_NAME = "kokoro_lexicon_us.tsv.gz"
    }
}
