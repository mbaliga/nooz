package xyz.mdhv.riverwip.model

/**
 * 64-bit SimHash over a text's tokens — the second half of dedup (brief §2:
 * canonical URL + title simhash). Near-duplicate titles produce hashes a small
 * Hamming distance apart, so syndicated/rewritten copies of one story collapse
 * even when their URLs differ.
 *
 * Pure and deterministic (built on [Hashing.fnv1a64]).
 */
object Simhash {

    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    /** Shingle width (characters). Char n-grams are robust for short titles: a
     *  small edit changes only the few shingles that span it, not whole tokens. */
    private const val SHINGLE = 4

    /** Normalize: lowercase, collapse runs of non-alphanumerics to a single space. */
    fun normalize(text: String): String =
        text.lowercase().replace(NON_ALNUM, " ").trim()

    /** Character n-gram shingles over the normalized text. */
    fun shingles(text: String, n: Int = SHINGLE): List<String> {
        val s = normalize(text)
        if (s.isEmpty()) return emptyList()
        if (s.length <= n) return listOf(s)
        return (0..s.length - n).map { s.substring(it, it + n) }
    }

    /** Compute the 64-bit SimHash of [text]. Empty/near-empty text hashes to 0. */
    fun of(text: String): Long {
        val grams = shingles(text)
        if (grams.isEmpty()) return 0L
        val v = IntArray(64)
        // Weight by shingle frequency.
        val freq = HashMap<String, Int>()
        for (g in grams) freq[g] = (freq[g] ?: 0) + 1
        for ((g, w) in freq) {
            val h = Hashing.fnv1a64(g)
            for (bit in 0 until 64) {
                if ((h ushr bit) and 1L == 1L) v[bit] += w else v[bit] -= w
            }
        }
        var result = 0L
        for (bit in 0 until 64) {
            if (v[bit] > 0) result = result or (1L shl bit)
        }
        return result
    }

    /** Hamming distance between two simhashes (0 = identical, 64 = opposite). */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Default near-duplicate threshold (bits). Calibrated on char-4-gram
     * signatures: identical titles = 0, a source-suffix variant ("… - Reuters")
     * ≈ 7, unrelated headlines ≈ 30. A threshold of 8 catches syndication and
     * trivial headline edits while staying far from distinct stories —
     * deliberately conservative, since wrongly collapsing two stories would
     * distort the very omission the app measures.
     */
    const val NEAR_DUP_THRESHOLD = 8

    fun isNearDuplicate(a: Long, b: Long, threshold: Int = NEAR_DUP_THRESHOLD): Boolean =
        a != 0L && b != 0L && distance(a, b) <= threshold
}
