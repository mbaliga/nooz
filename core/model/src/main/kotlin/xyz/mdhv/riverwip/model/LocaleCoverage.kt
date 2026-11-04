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
    const val TOTAL = 395

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 374,
    "as" to 374,
    "bn" to 374,
    "de" to 374,
    "en" to 395,
    "es" to 374,
    "fa" to 374,
    "fr" to 374,
    "gu" to 374,
    "hi" to 374,
    "id" to 374,
    "it" to 374,
    "ja" to 374,
    "kn" to 374,
    "ko" to 374,
    "ks" to 121,
    "mai" to 374,
    "ml" to 374,
    "mr" to 374,
    "or" to 374,
    "pa" to 374,
    "pt" to 374,
    "ru" to 374,
    "sw" to 374,
    "ta" to 374,
    "te" to 374,
    "tr" to 374,
    "ur" to 374,
    "vi" to 374,
    "zh-Hans" to 374,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
