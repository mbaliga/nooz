package xyz.mdhv.riverwip.model

/**
 * GENERATED FILE — do not edit. Generator: tools/i18n/generate.py
 *
 * How many of the interface's strings each locale has, counted from the
 * catalogues in i18n/strings/. The language picker shows this so nobody
 * chooses a language and is then surprised by a half-English screen.
 */
object LocaleCoverage {
    /** Strings in the base catalogue — the denominator. */
    const val TOTAL = 185

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 185,
    "as" to 185,
    "bn" to 185,
    "de" to 185,
    "en" to 185,
    "es" to 185,
    "fa" to 185,
    "fr" to 185,
    "gu" to 185,
    "hi" to 185,
    "id" to 185,
    "it" to 185,
    "ja" to 185,
    "kn" to 185,
    "ko" to 185,
    "ks" to 40,
    "mai" to 185,
    "ml" to 185,
    "mr" to 185,
    "or" to 185,
    "pa" to 185,
    "pt" to 185,
    "ru" to 185,
    "sw" to 185,
    "ta" to 185,
    "te" to 185,
    "tr" to 185,
    "ur" to 185,
    "vi" to 185,
    "zh-Hans" to 185,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
