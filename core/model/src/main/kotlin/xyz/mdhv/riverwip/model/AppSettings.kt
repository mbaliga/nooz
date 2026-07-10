package xyz.mdhv.riverwip.model

/**
 * App-level display preferences (owner's Settings mocks, 2026-07). The theme is
 * one of three literal surface tints — White, Paper, Dark — per the owner's
 * three-theme reference image (not light/system/dark). The struck-through
 * "Show Article Progress" row remains deliberately unimplemented.
 */
enum class ThemeMode(val key: String) {
    WHITE("white"), PAPER("paper"), DARK("dark");

    /** One two-finger flick in the reader = the next tint. */
    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): ThemeMode = when (key) {
            "white" -> WHITE
            "dark" -> DARK
            // "light"/"system" are legacy keys from the first theme iteration;
            // both were paper-toned in practice.
            else -> PAPER
        }
    }
}

enum class ReaderFont(val key: String) {
    SERIF("serif"), SANS("sans"), SYSTEM("system");

    companion object {
        private val byKey = entries.associateBy(ReaderFont::key)
        fun fromKey(key: String?): ReaderFont = byKey[key] ?: SANS
    }
}

/**
 * Three-step reading size (owner's Settings mock; its size names were ad-libbed
 * placeholders, so the app uses plain labels). Multiplies the type scale.
 */
enum class TextScale(val key: String, val multiplier: Float, val label: String) {
    SMALL("small", 0.9f, "Small"),
    STANDARD("standard", 1.0f, "Standard"),
    LARGE("large", 1.15f, "Large");

    companion object {
        private val byKey = entries.associateBy(TextScale::key)
        fun fromKey(key: String?): TextScale = byKey[key] ?: STANDARD
    }
}

data class AppSettings(
    /** The middle tint carries the check in the owner's mock — Paper is the default. */
    val themeMode: ThemeMode = ThemeMode.PAPER,
    val readerFont: ReaderFont = ReaderFont.SANS,
    val showReadingTime: Boolean = true,
    val textScale: TextScale = TextScale.STANDARD,
)

/**
 * The reader's standing filter (owner's Region & Topics flow): a longitude
 * sector picked on the globe plus an optional topic subset. An empty
 * [topicKeys] means all topics — the default the nav spec names
 * ("default global, all topics").
 */
data class ReaderFilter(
    val region: Region = Region.GLOBAL,
    val topicKeys: Set<String> = emptySet(),
) {
    val allTopics: Boolean get() = topicKeys.isEmpty()

    fun matchesTopic(topic: Topic): Boolean = allTopics || topic.key in topicKeys

    /**
     * Which sources flow under this region. Globally-tagged sources cover every
     * region (a world feed carries every sector's news); regionally-tagged
     * sources flow only in their own sector.
     */
    fun includesSource(sourceRegionTag: String?): Boolean {
        if (region == Region.GLOBAL) return true
        val tagged = Region.forSourceTag(sourceRegionTag)
        return tagged == Region.GLOBAL || tagged == region
    }

    /** Header summary, e.g. "Global | All" or "South Asia | Politics +2". */
    fun summary(): String {
        val topics = when {
            allTopics -> "All"
            topicKeys.size == 1 -> Topic.fromKey(topicKeys.first()).placeholderLabel
            else -> "${Topic.fromKey(topicKeys.first()).placeholderLabel} +${topicKeys.size - 1}"
        }
        return "${region.label} | $topics"
    }
}

/**
 * The globe's longitude sectors (owner's region-picker reference). Labels are
 * the owner's own sector names from that artifact — geography, not the
 * RESERVED taxonomy.
 */
enum class Region(val key: String, val label: String, val fromLon: Double, val toLon: Double) {
    GLOBAL("global", "Global", -180.0, 180.0),
    AMERICAS("americas", "Americas", -170.0, -30.0),
    EUROPE_AFRICA("europe-africa", "Europe & Africa", -30.0, 45.0),
    MIDEAST_CASIA("mideast-casia", "Middle East & C. Asia", 45.0, 72.0),
    SOUTH_ASIA("south-asia", "South Asia", 72.0, 95.0),
    EAST_SE_ASIA("east-se-asia", "East & SE Asia", 95.0, 148.0),
    AUSTRALIA_PACIFIC("australia-pacific", "Australia & Pacific", 148.0, 180.0);

    companion object {
        private val byKey = entries.associateBy(Region::key)
        fun fromKey(key: String?): Region = byKey[key] ?: GLOBAL

        /** Sector containing a (wrapped) longitude. The Australia/Pacific band wraps the antimeridian. */
        fun forLongitude(lon: Double): Region {
            val l = GlobeModel.wrapLon(lon)
            for (r in entries) {
                if (r == GLOBAL) continue
                if (r.fromLon <= r.toLon) {
                    if (l >= r.fromLon && l < r.toLon) return r
                } else if (l >= r.fromLon || l < r.toLon) {
                    return r
                }
            }
            // Left edge of the Americas band ([-180, -170) wraps into Australia & Pacific).
            return AUSTRALIA_PACIFIC
        }

        /** Map a source's declared region tag (catalogue schema: "global" | "india" | …) to a sector. */
        fun forSourceTag(tag: String?): Region = when (tag?.lowercase()) {
            null, "", "global" -> GLOBAL
            "india" -> SOUTH_ASIA
            else -> byKey[tag.lowercase()] ?: GLOBAL
        }
    }
}
