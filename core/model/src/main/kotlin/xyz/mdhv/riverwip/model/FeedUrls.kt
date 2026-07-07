package xyz.mdhv.riverwip.model

import java.net.URLEncoder

/**
 * Query builders for the parameterized source kinds (brief §P1). Pure string
 * construction so the sources UI can preview the exact URL it will fetch — total
 * inspectability (brief §3): the builder output IS what gets stored and fetched.
 */
object FeedUrls {

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---- Google News RSS -------------------------------------------------

    /**
     * Google News locale triple. `hl` = UI language (e.g. `en-IN`), `gl` = country
     * (e.g. `IN`), `ceid` = `<country>:<lang>` (e.g. `IN:en`).
     */
    data class GNLocale(val hl: String, val gl: String, val ceid: String) {
        companion object {
            /** Build from a language (`en`) and country (`IN`). */
            fun of(language: String, country: String): GNLocale =
                GNLocale(hl = "$language-$country", gl = country, ceid = "$country:$language")

            val INDIA_EN = of("en", "IN")
            val US_EN = of("en", "US")
            val UK_EN = of("en", "GB")
        }
    }

    private fun gnSuffix(loc: GNLocale): String =
        "hl=${enc(loc.hl)}&gl=${enc(loc.gl)}&ceid=${enc(loc.ceid)}"

    /** Top-stories feed for a locale. */
    fun googleNewsTop(loc: GNLocale): String =
        "https://news.google.com/rss?${gnSuffix(loc)}"

    /** Keyword/search feed. [query] supports Google News operators; it is encoded. */
    fun googleNewsSearch(query: String, loc: GNLocale): String =
        "https://news.google.com/rss/search?q=${enc(query)}&${gnSuffix(loc)}"

    /** Google News built-in section topics. */
    enum class GNTopic { WORLD, NATION, BUSINESS, TECHNOLOGY, ENTERTAINMENT, SPORTS, SCIENCE, HEALTH }

    /** A built-in section feed (e.g. TECHNOLOGY) for a locale. */
    fun googleNewsSection(topic: GNTopic, loc: GNLocale): String =
        "https://news.google.com/rss/headlines/section/topic/${topic.name}?${gnSuffix(loc)}"

    // ---- GDELT DOC 2.0 (no key) -----------------------------------------

    /** GDELT time windows accepted by the `timespan` param. */
    data class GdeltQuery(
        val query: String,
        val maxRecords: Int = 75,
        val timespanHours: Int = 24,
        val sortByDate: Boolean = true,
    )

    /**
     * GDELT DOC 2.0 article-list JSON endpoint. `maxrecords` is clamped to
     * GDELT's [1,250] range; `timespan` is expressed in hours.
     */
    fun gdeltDoc(q: GdeltQuery): String {
        val max = q.maxRecords.coerceIn(1, 250)
        val span = q.timespanHours.coerceIn(1, 24 * 30)
        val sort = if (q.sortByDate) "&sort=datedesc" else ""
        return "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(q.query)}" +
            "&mode=artlist&format=json&maxrecords=$max&timespan=${span}h$sort"
    }

    // ---- Mastodon public timelines (no auth) ----------------------------

    private fun mastodonBase(instance: String): String {
        val host = instance.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        return "https://$host"
    }

    /** Public timeline for an instance. [localOnly] restricts to that instance. */
    fun mastodonPublic(instance: String, limit: Int = 40, localOnly: Boolean = false): String {
        val lim = limit.coerceIn(1, 40)
        val local = if (localOnly) "&local=true" else ""
        return "${mastodonBase(instance)}/api/v1/timelines/public?limit=$lim$local"
    }

    /** Hashtag timeline for an instance. [tag] is used without a leading `#`. */
    fun mastodonTag(instance: String, tag: String, limit: Int = 40): String {
        val lim = limit.coerceIn(1, 40)
        val t = tag.trim().removePrefix("#")
        return "${mastodonBase(instance)}/api/v1/timelines/tag/${enc(t)}?limit=$lim"
    }
}
