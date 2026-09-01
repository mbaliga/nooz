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
    const val TOTAL = 117

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 117,
    "as" to 117,
    "bn" to 117,
    "de" to 117,
    "en" to 117,
    "es" to 117,
    "fa" to 117,
    "fr" to 117,
    "gu" to 117,
    "hi" to 117,
    "id" to 117,
    "it" to 117,
    "ja" to 117,
    "kn" to 117,
    "ko" to 117,
    "ks" to 32,
    "mai" to 117,
    "ml" to 117,
    "mr" to 117,
    "or" to 117,
    "pa" to 117,
    "pt" to 117,
    "ru" to 117,
    "sw" to 117,
    "ta" to 117,
    "te" to 117,
    "tr" to 117,
    "ur" to 117,
    "vi" to 117,
    "zh-Hans" to 117,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
