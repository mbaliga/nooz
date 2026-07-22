package xyz.mdhv.riverwip.inference.local.kokoro

/**
 * Spells digits out as English words. Kokoro's own vocabulary (see
 * [KokoroVocab]) has no digit characters at all — the reference tokenizer's
 * normalizer strips them silently — so any number reaching the model
 * unexpanded wouldn't just sound wrong, it would vanish from the narration
 * entirely without a trace. Handles the shapes news text actually uses
 * (cardinals, decimals, percents, dollar amounts, day-of-month ordinals);
 * anything stranger falls back to spelling each digit individually rather
 * than dropping it.
 */
object KokoroNumberExpander {
    private val ONES = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val SCALES = arrayOf("", " thousand", " million", " billion", " trillion")
    private val ORDINAL_IRREGULAR = mapOf(
        "one" to "first", "two" to "second", "three" to "third", "five" to "fifth",
        "eight" to "eighth", "nine" to "ninth", "twelve" to "twelfth",
    )

    /** A run of digits (and at most one leading '-', one leading '$', one '.', one trailing '%', thousands commas) — the shapes [KokoroPhonemizer]'s tokenizer hands over. */
    fun expand(token: String): String {
        // Thousands separators are only ever in the integer part (the regex
        // that produced this token never puts a comma after the decimal
        // point), so stripping them up front is always safe and keeps
        // cardinal()'s digit-grouping arithmetic operating on pure digits.
        var t = token.replace(",", "")
        val negative = t.startsWith("-")
        if (negative) t = t.substring(1)
        val isDollar = t.startsWith("$")
        if (isDollar) t = t.substring(1)
        val percent = !isDollar && t.endsWith("%")
        if (percent) t = t.dropLast(1)
        val ordinalSuffix = !isDollar && t.length > 2 &&
            (t.endsWith("st") || t.endsWith("nd") || t.endsWith("rd") || t.endsWith("th")) &&
            t.dropLast(2).all { it.isDigit() }
        if (ordinalSuffix) t = t.dropLast(2)

        val words = if (isDollar) {
            dollars(t)
        } else {
            val dot = t.indexOf('.')
            if (dot >= 0) {
                val whole = t.substring(0, dot).ifEmpty { "0" }
                val frac = t.substring(dot + 1)
                "${cardinal(whole)} point ${frac.map { d -> ONES.getOrElse(d - '0') { "" } }.joinToString(" ")}"
            } else {
                cardinal(t.ifEmpty { "0" })
            }
        }

        var result = if (negative) "minus $words" else words
        if (ordinalSuffix) result = toOrdinal(result)
        if (percent) result += " percent"
        return result
    }

    /** "5" -> "five dollars", "5.50" -> "five dollars and fifty cents", "1.00"/"1" -> "one dollar" — the shape news copy actually writes money in. */
    private fun dollars(t: String): String {
        val dot = t.indexOf('.')
        val wholeStr = if (dot >= 0) t.substring(0, dot) else t
        val whole = wholeStr.ifEmpty { "0" }
        val dollarsWord = if (whole.trimStart('0') == "1") "one dollar" else "${cardinal(whole)} dollars"
        if (dot < 0) return dollarsWord
        // Right-padded/truncated to exactly two digits so a single fractional
        // digit reads as tenths-of-a-dollar ("$5.5" -> fifty cents, not five).
        val centsStr = (t.substring(dot + 1) + "00").take(2)
        val cents = centsStr.toIntOrNull() ?: 0
        if (cents == 0) return dollarsWord
        val centsWord = if (cents == 1) "one cent" else "${cardinal(centsStr)} cents"
        return "$dollarsWord and $centsWord"
    }

    /** A plain run of digits read one at a time — [cardinal]'s own fallback for absurdly long runs rather than grouping them into (meaningless) trillions. */
    fun spellDigits(digits: String): String = digits.map { d -> ONES.getOrElse(d - '0') { "" } }.joinToString(" ")

    private fun cardinal(digits: String): String {
        val n = digits.trimStart('0')
        if (n.isEmpty()) return ONES[0]
        if (n.length > 15) return spellDigits(digits) // absurdly large — never silently drop, just read the digits
        val groups = ArrayDeque<String>()
        var rest = n
        while (rest.isNotEmpty()) {
            val take = ((rest.length - 1) % 3) + 1
            groups.addLast(rest.substring(0, take))
            rest = rest.substring(take)
        }
        val parts = mutableListOf<String>()
        for ((i, group) in groups.withIndex()) {
            val scaleIndex = groups.size - 1 - i
            val g = group.toInt()
            if (g == 0) continue
            parts += threeDigits(g) + SCALES.getOrElse(scaleIndex) { "" }
        }
        return parts.joinToString(" ").trim().ifEmpty { ONES[0] }
    }

    private fun threeDigits(n: Int): String {
        val hundreds = n / 100
        val rem = n % 100
        val sb = StringBuilder()
        if (hundreds > 0) sb.append(ONES[hundreds]).append(" hundred")
        if (rem > 0) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(twoDigits(rem))
        }
        return sb.toString()
    }

    private fun twoDigits(n: Int): String = when {
        n < 20 -> ONES[n]
        n % 10 == 0 -> TENS[n / 10]
        else -> "${TENS[n / 10]}-${ONES[n % 10]}"
    }

    private fun toOrdinal(cardinalWords: String): String {
        // Only the final word takes the ordinal suffix ("one hundred one" ->
        // "one hundred first"), but a two-digit tail like "twenty-one" is
        // hyphenated, not space-separated (see twoDigits()) — splitting on
        // space alone would land on "one" and strand "twenty-" unaffected,
        // giving "twenty-oneth" instead of "twenty-first".
        val i = cardinalWords.indexOfLast { it == ' ' || it == '-' }
        val head = if (i >= 0) cardinalWords.substring(0, i + 1) else ""
        val last = if (i >= 0) cardinalWords.substring(i + 1) else cardinalWords
        val ordinalLast = ORDINAL_IRREGULAR[last] ?: when {
            last.endsWith("y") -> last.dropLast(1) + "ieth"
            else -> last + "th"
        }
        return head + ordinalLast
    }
}
