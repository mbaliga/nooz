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
    const val TOTAL = 249

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 249,
    "as" to 249,
    "bn" to 249,
    "de" to 249,
    "en" to 249,
    "es" to 249,
    "fa" to 249,
    "fr" to 249,
    "gu" to 249,
    "hi" to 249,
    "id" to 249,
    "it" to 249,
    "ja" to 249,
    "kn" to 249,
    "ko" to 249,
    "ks" to 76,
    "mai" to 249,
    "ml" to 249,
    "mr" to 249,
    "or" to 249,
    "pa" to 249,
    "pt" to 249,
    "ru" to 249,
    "sw" to 249,
    "ta" to 249,
    "te" to 249,
    "tr" to 249,
    "ur" to 249,
    "vi" to 249,
    "zh-Hans" to 249,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
