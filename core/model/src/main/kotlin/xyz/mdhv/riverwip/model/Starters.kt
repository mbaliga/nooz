package xyz.mdhv.riverwip.model

/**
 * Built-in starter registry (brief §P1). Expressed in the [Catalogue] schema so
 * P6 can swap in the remote `catalogue.json` with no migration.
 *
 * **Every concrete feed here was verified live at build time** (brief §0). The
 * verification run on 2026-07-07 fetched each URL and confirmed a parseable feed
 * with items; entries that only rotted or blocked were dropped. Excluded after
 * verification: CNN edition RSS (HTTP 503 — retired), Wikinews Special:NewsFeed
 * (HTTP 404), The Wire & The Print (served HTML, no feed items), Deccan Herald
 * (no resolving RSS path found). See STATE.md verification log.
 *
 * `verifiedAt` stamps carry the check date; the P6 CI sentry keeps them fresh.
 */
object Starters {

    private const val V = "2026-07-07"

    private fun rss(
        id: String, title: String, url: String, region: String,
    ) = ServiceDef(
        id = id, kind = "rss", title = title, tier = "A", region = region,
        url = url, verifiedAt = V,
    )

    /** Concrete, verified RSS feeds — regionally balanced (global + India). */
    val verifiedFeeds: List<ServiceDef> = listOf(
        // --- Global ---
        rss("bbc-top", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "global"),
        rss("bbc-world", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "global"),
        rss("npr-news", "NPR News", "https://feeds.npr.org/1001/rss.xml", "global"),
        rss("guardian-world", "The Guardian — World", "https://www.theguardian.com/world/rss", "global"),
        rss("guardian-intl", "The Guardian — International", "https://www.theguardian.com/international/rss", "global"),
        rss("aljazeera-all", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "global"),
        rss("dw-en", "Deutsche Welle (EN)", "https://rss.dw.com/rdf/rss-en-all", "global"),
        rss("france24-en", "France 24 (EN)", "https://www.france24.com/en/rss", "global"),
        rss("nyt-world", "The New York Times — World", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "global"),
        rss("nyt-top", "The New York Times — Top Stories", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "global"),
        rss("propublica", "ProPublica", "https://www.propublica.org/feeds/propublica/main", "global"),
        // --- India ---
        rss("thehindu-home", "The Hindu", "https://www.thehindu.com/feeder/default.rss", "india"),
        rss("thehindu-national", "The Hindu — National", "https://www.thehindu.com/news/national/feeder/default.rss", "india"),
        rss("ndtv-top", "NDTV — Top Stories", "https://feeds.feedburner.com/ndtvnews-top-stories", "india"),
        rss("toi-top", "The Times of India — Top", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms", "india"),
        rss("indian-express", "The Indian Express", "https://indianexpress.com/feed/", "india"),
        rss("livemint", "Livemint", "https://www.livemint.com/rss/news", "india"),
        rss("hindustan-times", "Hindustan Times — India", "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml", "india"),
        rss("business-standard", "Business Standard", "https://www.business-standard.com/rss/home_page_top_stories.rss", "india"),
        rss("scroll-in", "Scroll.in", "https://feeds.feedburner.com/ScrollinArticles", "india"),
    )

    /**
     * Builder / keyed kinds. These have no single concrete `url`; they carry a
     * template + a verified example so the builder UI can preview exactly what it
     * will fetch. Liveness of the *service* was confirmed at build time where
     * possible (notes record caveats).
     */
    val builders: List<ServiceDef> = listOf(
        ServiceDef(
            id = "google-news", kind = "googlenews", title = "Google News", tier = "A", region = "global",
            homepage = "https://news.google.com/",
            urlTemplate = "https://news.google.com/rss/search?q={query}&hl={hl}&gl={gl}&ceid={ceid}",
            example = "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en",
            notes = "Builder: top-stories, section topics, or keyword search per locale (hl/gl/ceid).",
            verifiedAt = V,
        ),
        ServiceDef(
            id = "gdelt-doc", kind = "gdelt", title = "GDELT DOC 2.0", tier = "A", region = "global",
            homepage = "https://www.gdeltproject.org/",
            docsUrl = "https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/",
            urlTemplate = "https://api.gdeltproject.org/api/v2/doc/doc?query={query}&mode=artlist&format=json&maxrecords={n}&timespan={h}h",
            example = "https://api.gdeltproject.org/api/v2/doc/doc?query=climate&mode=artlist&format=json&maxrecords=75&timespan=24h",
            notes = "No key. Public API; rate-limited (may return HTTP 429/503 under load — retry with backoff).",
            freeTier = FreeTier(rateLimitPerSecond = 1.0, notes = "Soft public rate limit; be gentle."),
        ),
        ServiceDef(
            id = "mastodon", kind = "mastodon", title = "Mastodon", tier = "A", region = "global",
            urlTemplate = "https://{instance}/api/v1/timelines/tag/{tag}?limit={n}",
            example = "https://mastodon.social/api/v1/timelines/tag/news?limit=40",
            notes = "No auth for hashtag timelines (verified). The public timeline may require auth on some instances (mastodon.social returns HTTP 422).",
            verifiedAt = V,
        ),
        ServiceDef(
            id = "guardian-open-platform", kind = "guardian", title = "The Guardian — Open Platform", tier = "B",
            region = "global",
            homepage = "https://open-platform.theguardian.com/",
            keySignupUrl = "https://open-platform.theguardian.com/access/",
            requiresKey = true,
            urlTemplate = "https://content.guardianapis.com/search?api-key={key}&show-fields=headline,trailText",
            notes = "Free key required — labeled honestly. Key stored in Android Keystore, never in the repo.",
            freeTier = FreeTier(requestsPerDay = 5000, rateLimitPerSecond = 12.0, notes = "Developer tier."),
        ),
    )

    /**
     * Tier B reference (brief §P1): the generic keyed `api` kind + key-storage
     * path. One is provided as reference; the rest arrive via `catalogue.json`.
     */
    val tierBReference: List<ServiceDef> = listOf(
        ServiceDef(
            id = "gnews", kind = "api", title = "GNews", tier = "B", region = "global",
            homepage = "https://gnews.io/",
            keySignupUrl = "https://gnews.io/register",
            requiresKey = true,
            urlTemplate = "https://gnews.io/api/v4/top-headlines?category={category}&lang={lang}&apikey={key}",
            example = "https://gnews.io/api/v4/top-headlines?category=general&lang=en&apikey={key}",
            notes = "Reference Tier-B keyed provider. Key in Keystore. Others come from the catalogue.",
            freeTier = FreeTier(requestsPerDay = 100, notes = "Free tier."),
        ),
    )

    /** The full seed catalogue (what ships in the APK before any remote refresh). */
    val seed: Catalogue
        get() = Catalogue(
            version = 1,
            generatedAt = V,
            services = verifiedFeeds + builders + tierBReference,
        )

    /** One-click concrete feeds, grouped by region for the starters UI. */
    fun feedsByRegion(): Map<String, List<ServiceDef>> =
        verifiedFeeds.groupBy { it.region ?: "other" }
}
