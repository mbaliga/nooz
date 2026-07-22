package xyz.mdhv.riverwip.model

/**
 * The classification lexicon (brief §2b): keyword/phrase rules per topic, plus a
 * feed-category → topic mapping (§2a). These are the *rules*, not RESERVED
 * display labels — every match is recorded as [TopicEvidence] so a tap can reveal
 * exactly which term fired (brief §3 total inspectability).
 *
 * v1 is deliberately a transparent lexicon, not an opaque classifier; an
 * on-device embedding upgrade is a logged open question (STATE.md §10).
 */
object TopicLexicon {

    /** Keyword/phrase terms per topic. Matched on word boundaries, case-insensitive. */
    val terms: Map<Topic, List<String>> = mapOf(
        Topic.POLITICS to listOf(
            "election", "parliament", "congress", "senate", "president", "prime minister",
            "government", "policy", "minister", "lawmaker", "legislation", "vote", "voter",
            "campaign", "democrat", "republican", "bjp", "coalition", "referendum", "cabinet",
            "diplomacy", "sanction", "supreme court", "governor", "impeachment", "ballot",
        ),
        Topic.CONFLICT to listOf(
            "war", "airstrike", "missile", "troops", "ceasefire", "militant", "insurgent",
            "gunfire", "shelling", "offensive", "casualties", "hostage", "terror", "militia",
            "occupation", "invasion", "border clash", "rebel", "drone strike", "genocide",
            "armed forces", "front line",
        ),
        Topic.BUSINESS to listOf(
            "market", "stocks", "shares", "economy", "inflation", "interest rate", "gdp",
            "revenue", "profit", "earnings", "merger", "acquisition", "ipo", "startup",
            "trade", "tariff", "central bank", "recession", "unemployment", "currency",
            "investor", "nasdaq", "sensex", "rupee", "quarterly",
        ),
        Topic.TECH to listOf(
            "software", "app", "smartphone", "chip", "semiconductor", "artificial intelligence",
            "machine learning", "algorithm", "startup", "cybersecurity", "data breach", "cloud",
            "gadget", "processor", "open source", "encryption", "social media", "silicon",
            "robot", "quantum computing",
        ),
        Topic.SCIENCE to listOf(
            "research", "study", "scientist", "physics", "astronomy", "space", "nasa", "isro",
            "galaxy", "particle", "genome", "biology", "chemistry", "experiment", "telescope",
            "satellite", "spacecraft", "fossil", "evolution", "laboratory",
        ),
        Topic.CLIMATE to listOf(
            "climate", "climate change", "global warming", "emissions", "carbon", "renewable",
            "solar", "wind power", "drought", "flood", "wildfire", "heatwave", "biodiversity",
            "deforestation", "greenhouse", "monsoon", "pollution", "net zero", "glacier",
            "cyclone",
        ),
        Topic.HEALTH to listOf(
            "health", "hospital", "disease", "virus", "vaccine", "outbreak", "pandemic",
            "cancer", "mental health", "medicine", "clinical", "who", "epidemic", "infection",
            "diabetes", "surgery", "drug", "patients", "public health",
        ),
        Topic.CULTURE to listOf(
            "film", "movie", "music", "album", "festival", "art", "artist", "book", "author",
            "theatre", "celebrity", "actor", "director", "museum", "fashion", "cinema",
            "streaming", "box office", "concert", "award",
        ),
        Topic.SPORT to listOf(
            "match", "tournament", "cricket", "football", "soccer", "goal", "world cup",
            "olympics", "medal", "championship", "league", "player", "coach", "wicket",
            "innings", "striker", "tennis", "grand slam", "ipl", "fifa",
        ),
    )

    /** Feed-declared categories mapped to the taxonomy (case-insensitive, exact/substring). */
    val categoryMap: Map<String, Topic> = mapOf(
        "politics" to Topic.POLITICS, "world" to Topic.POLITICS, "nation" to Topic.POLITICS,
        "national" to Topic.POLITICS, "election" to Topic.POLITICS, "government" to Topic.POLITICS,
        "war" to Topic.CONFLICT, "conflict" to Topic.CONFLICT, "military" to Topic.CONFLICT,
        "defence" to Topic.CONFLICT, "defense" to Topic.CONFLICT,
        "business" to Topic.BUSINESS, "economy" to Topic.BUSINESS, "markets" to Topic.BUSINESS,
        "finance" to Topic.BUSINESS, "money" to Topic.BUSINESS,
        "technology" to Topic.TECH, "tech" to Topic.TECH, "gadgets" to Topic.TECH,
        "science" to Topic.SCIENCE, "space" to Topic.SCIENCE,
        "climate" to Topic.CLIMATE, "environment" to Topic.CLIMATE, "weather" to Topic.CLIMATE,
        "health" to Topic.HEALTH, "wellness" to Topic.HEALTH, "medicine" to Topic.HEALTH,
        "culture" to Topic.CULTURE, "entertainment" to Topic.CULTURE, "arts" to Topic.CULTURE,
        "lifestyle" to Topic.CULTURE, "film" to Topic.CULTURE, "music" to Topic.CULTURE,
        "sport" to Topic.SPORT, "sports" to Topic.SPORT, "cricket" to Topic.SPORT,
        "football" to Topic.SPORT,
    )

    /** Precompiled word-boundary matchers per term, per topic. */
    val matchers: Map<Topic, List<Pair<String, Regex>>> by lazy {
        terms.mapValues { (_, list) ->
            list.map { term -> term to Regex("\\b" + Regex.escape(term) + "\\b", RegexOption.IGNORE_CASE) }
        }
    }
}
