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
    const val TOTAL = 68

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 68,
    "as" to 68,
    "bn" to 68,
    "de" to 68,
    "en" to 68,
    "es" to 68,
    "fa" to 68,
    "fr" to 68,
    "gu" to 68,
    "hi" to 68,
    "id" to 68,
    "it" to 68,
    "ja" to 68,
    "kn" to 68,
    "ko" to 68,
    "ks" to 24,
    "mai" to 68,
    "ml" to 68,
    "mr" to 68,
    "or" to 68,
    "pa" to 68,
    "pt" to 68,
    "ru" to 68,
    "sw" to 68,
    "ta" to 68,
    "te" to 68,
    "tr" to 68,
    "ur" to 68,
    "vi" to 68,
    "zh-Hans" to 68,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
