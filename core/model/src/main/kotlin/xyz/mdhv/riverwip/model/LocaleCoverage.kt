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
    const val TOTAL = 157

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 157,
    "as" to 157,
    "bn" to 157,
    "de" to 157,
    "en" to 157,
    "es" to 157,
    "fa" to 157,
    "fr" to 157,
    "gu" to 157,
    "hi" to 157,
    "id" to 157,
    "it" to 157,
    "ja" to 157,
    "kn" to 157,
    "ko" to 157,
    "ks" to 37,
    "mai" to 157,
    "ml" to 157,
    "mr" to 157,
    "or" to 157,
    "pa" to 157,
    "pt" to 157,
    "ru" to 157,
    "sw" to 157,
    "ta" to 157,
    "te" to 157,
    "tr" to 157,
    "ur" to 157,
    "vi" to 157,
    "zh-Hans" to 157,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
