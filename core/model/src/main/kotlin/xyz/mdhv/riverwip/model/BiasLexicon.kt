package xyz.mdhv.riverwip.model

/**
 * Biased-language lexicon (brief §P5), in the Recasens et al. (2013) lineage:
 * loaded verbs, intensifiers, emotive adjectives, and editorializing hedges.
 * These are the *rules* the lens underlines; each detected span carries its
 * evidence so a tap reveals exactly why it was flagged (brief §3).
 */
object BiasLexicon {

    enum class Category(val label: String) {
        LOADED_VERB("loaded verb"),
        INTENSIFIER("intensifier"),
        EMOTIVE_ADJECTIVE("emotive adjective"),
        EDITORIALIZING_HEDGE("editorializing hedge"),
    }

    val terms: Map<Category, List<String>> = mapOf(
        Category.LOADED_VERB to listOf(
            "slammed", "blasted", "bashed", "gushed", "boasted", "bragged", "touted",
            "admitted", "conceded", "claimed", "refused", "lashed out", "hit out",
            "ripped", "railed", "crowed", "vowed", "insisted", "unleashed", "erupted",
        ),
        Category.INTENSIFIER to listOf(
            "very", "extremely", "incredibly", "utterly", "totally", "hugely",
            "massively", "wildly", "absolutely", "completely", "remarkably",
        ),
        Category.EMOTIVE_ADJECTIVE to listOf(
            "shocking", "outrageous", "devastating", "stunning", "horrific", "brutal",
            "disastrous", "catastrophic", "explosive", "damning", "scathing",
            "bombshell", "chaotic", "dramatic", "staggering", "alarming",
        ),
        Category.EDITORIALIZING_HEDGE to listOf(
            "so-called", "allegedly", "reportedly", "apparently", "notoriously",
            "infamously", "supposedly", "purportedly", "arguably", "clearly",
            "obviously", "of course",
        ),
    )

    /** Precompiled word-boundary matchers, longest-first so phrases win over words. */
    val matchers: List<Triple<Category, String, Regex>> by lazy {
        terms.flatMap { (cat, list) -> list.map { term -> Triple(cat, term, term) } }
            .sortedByDescending { it.third.length }
            .map { (cat, term, _) ->
                Triple(cat, term, Regex("\\b" + Regex.escape(term) + "\\b", RegexOption.IGNORE_CASE))
            }
    }
}
