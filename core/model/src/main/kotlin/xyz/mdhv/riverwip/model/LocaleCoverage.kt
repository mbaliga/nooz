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
    const val TOTAL = 370

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 370,
    "as" to 370,
    "bn" to 370,
    "de" to 370,
    "en" to 370,
    "es" to 370,
    "fa" to 370,
    "fr" to 370,
    "gu" to 370,
    "hi" to 370,
    "id" to 370,
    "it" to 370,
    "ja" to 370,
    "kn" to 370,
    "ko" to 370,
    "ks" to 119,
    "mai" to 370,
    "ml" to 370,
    "mr" to 370,
    "or" to 370,
    "pa" to 370,
    "pt" to 370,
    "ru" to 370,
    "sw" to 370,
    "ta" to 370,
    "te" to 370,
    "tr" to 370,
    "ur" to 370,
    "vi" to 370,
    "zh-Hans" to 370,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
