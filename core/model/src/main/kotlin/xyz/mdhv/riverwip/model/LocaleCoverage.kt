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
    const val TOTAL = 37

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 37,
    "as" to 37,
    "bn" to 37,
    "de" to 37,
    "en" to 37,
    "es" to 37,
    "fa" to 37,
    "fr" to 37,
    "gu" to 37,
    "hi" to 37,
    "id" to 37,
    "it" to 37,
    "ja" to 37,
    "kn" to 37,
    "ko" to 37,
    "ks" to 15,
    "mai" to 37,
    "ml" to 37,
    "mr" to 37,
    "or" to 37,
    "pa" to 37,
    "pt" to 37,
    "ru" to 37,
    "sw" to 37,
    "ta" to 37,
    "te" to 37,
    "tr" to 37,
    "ur" to 37,
    "vi" to 37,
    "zh-Hans" to 37,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
