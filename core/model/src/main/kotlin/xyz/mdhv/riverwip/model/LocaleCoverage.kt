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
    const val TOTAL = 334

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 334,
    "as" to 334,
    "bn" to 334,
    "de" to 334,
    "en" to 334,
    "es" to 334,
    "fa" to 334,
    "fr" to 334,
    "gu" to 334,
    "hi" to 334,
    "id" to 334,
    "it" to 334,
    "ja" to 334,
    "kn" to 334,
    "ko" to 334,
    "ks" to 121,
    "mai" to 334,
    "ml" to 334,
    "mr" to 334,
    "or" to 334,
    "pa" to 334,
    "pt" to 334,
    "ru" to 334,
    "sw" to 334,
    "ta" to 334,
    "te" to 334,
    "tr" to 334,
    "ur" to 334,
    "vi" to 334,
    "zh-Hans" to 334,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
