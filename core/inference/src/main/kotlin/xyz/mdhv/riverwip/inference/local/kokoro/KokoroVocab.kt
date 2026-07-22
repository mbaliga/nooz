package xyz.mdhv.riverwip.inference.local.kokoro

/**
 * Kokoro's own character-level vocabulary — extracted verbatim from
 * `tokenizer.json`'s `model.vocab` in the onnx-community/Kokoro-82M-v1.0-ONNX
 * repo (the exact model this build downloads). Every phoneme string this
 * package produces must map through this table, or the model receives
 * nonsense token ids; nothing here is guessed. Unmapped characters are
 * dropped rather than erroring, mirroring the tokenizer's own `normalizer`
 * (a `Replace` rule that already strips anything outside this set before
 * tokenization even runs — this table's fallback behaviour matches the real
 * model's, not an invented one).
 */
object KokoroVocab {
    /** Pad/unknown token — also the sequence start/end marker (owner docs: "leave room for the pad token 0 at the start & end"). */
    const val PAD: Int = 0

    /** Model context length in tokens (owner docs); phoneme content must leave room for the two pad tokens. */
    const val MAX_CONTEXT: Int = 512

    // One token shy of the full 510-token budget the pad tokens leave, not
    // the full 510: a voice .bin file's own row count (e.g. af_heart.bin,
    // 510 rows, indices 0-509) means an exactly-510-token chunk would need a
    // style row that doesn't exist — see LocalKokoroTtsProvider's coerceIn
    // guard, which this margin makes moot in the ordinary case rather than
    // relying on that clamp picking the nearest available row instead of
    // the mathematically "correct" one.
    const val MAX_PHONEME_CHARS: Int = MAX_CONTEXT - 3

    private val CHAR_TO_ID: Map<Char, Int> = mapOf(
        '$' to 0,
        ';' to 1,
        ':' to 2,
        ',' to 3,
        '.' to 4,
        '!' to 5,
        '?' to 6,
        '—' to 9,
        '…' to 10,
        '"' to 11,
        '(' to 12,
        ')' to 13,
        '“' to 14,
        '”' to 15,
        ' ' to 16,
        '̃' to 17,
        'ʣ' to 18,
        'ʥ' to 19,
        'ʦ' to 20,
        'ʨ' to 21,
        'ᵝ' to 22,
        'ꭧ' to 23,
        'A' to 24,
        'I' to 25,
        'O' to 31,
        'Q' to 33,
        'S' to 35,
        'T' to 36,
        'W' to 39,
        'Y' to 41,
        'ᵊ' to 42,
        'a' to 43,
        'b' to 44,
        'c' to 45,
        'd' to 46,
        'e' to 47,
        'f' to 48,
        'h' to 50,
        'i' to 51,
        'j' to 52,
        'k' to 53,
        'l' to 54,
        'm' to 55,
        'n' to 56,
        'o' to 57,
        'p' to 58,
        'q' to 59,
        'r' to 60,
        's' to 61,
        't' to 62,
        'u' to 63,
        'v' to 64,
        'w' to 65,
        'x' to 66,
        'y' to 67,
        'z' to 68,
        'ɑ' to 69,
        'ɐ' to 70,
        'ɒ' to 71,
        'æ' to 72,
        'β' to 75,
        'ɔ' to 76,
        'ɕ' to 77,
        'ç' to 78,
        'ɖ' to 80,
        'ð' to 81,
        'ʤ' to 82,
        'ə' to 83,
        'ɚ' to 85,
        'ɛ' to 86,
        'ɜ' to 87,
        'ɟ' to 90,
        'ɡ' to 92,
        'ɥ' to 99,
        'ɨ' to 101,
        'ɪ' to 102,
        'ʝ' to 103,
        'ɯ' to 110,
        'ɰ' to 111,
        'ŋ' to 112,
        'ɳ' to 113,
        'ɲ' to 114,
        'ɴ' to 115,
        'ø' to 116,
        'ɸ' to 118,
        'θ' to 119,
        'œ' to 120,
        'ɹ' to 123,
        'ɾ' to 125,
        'ɻ' to 126,
        'ʁ' to 128,
        'ɽ' to 129,
        'ʂ' to 130,
        'ʃ' to 131,
        'ʈ' to 132,
        'ʧ' to 133,
        'ʊ' to 135,
        'ʋ' to 136,
        'ʌ' to 138,
        'ɣ' to 139,
        'ɤ' to 140,
        'χ' to 142,
        'ʎ' to 143,
        'ʒ' to 147,
        'ʔ' to 148,
        'ˈ' to 156,
        'ˌ' to 157,
        'ː' to 158,
        'ʰ' to 162,
        'ʲ' to 164,
        '↓' to 169,
        '→' to 171,
        '↗' to 172,
        '↘' to 173,
        'ᵻ' to 177,
    )

    /** True if [c] is one of the phoneme/punctuation characters Kokoro actually understands. */
    fun supports(c: Char): Boolean = CHAR_TO_ID.containsKey(c)

    /** Maps a phoneme string to token ids, silently dropping unsupported characters (matches the reference tokenizer's own normalizer). */
    fun tokenize(phonemes: String): List<Int> = phonemes.mapNotNull { CHAR_TO_ID[it] }
}
