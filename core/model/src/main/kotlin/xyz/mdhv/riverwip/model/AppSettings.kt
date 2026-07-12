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

/**
 * Three reading voices — Hyle's own three internal families the owner named
 * (2026-07): the two sans, **Hyle Grotesk Classic** and **Hyle Grotesk Plus**,
 * and the one serif, **Hyle Print**. Verified against the Hyle Design System's
 * `fonts/` source-of-truth and bundled as OFL TTFs. Hyle Deco Pro is
 * deliberately excluded — "don't use Hyle Deco," per the owner.
 */
enum class ReaderFont(val key: String, val label: String) {
    GROTESK_CLASSIC("grotesk-classic", "Grotesk Classic"),
    GROTESK_PLUS("grotesk-plus", "Grotesk Plus"),
    PRINT("print", "Print");

    companion object {
        private val byKey = entries.associateBy(ReaderFont::key)
        fun fromKey(key: String?): ReaderFont = when (key) {
            // Legacy keys from the pre-Hyle-families iterations.
            "serif" -> PRINT
            "sans", "system" -> GROTESK_CLASSIC
            "mono" -> GROTESK_PLUS
            else -> byKey[key] ?: GROTESK_CLASSIC
        }
    }
}

/**
 * Three-step reading size (owner's Settings mock). The names are the owner's
 * own — Rice, Peanut, Almond — real objects in ascending relative size.
 * Multiplies the type scale.
 */
enum class TextScale(val key: String, val multiplier: Float, val label: String) {
    RICE("rice", 0.9f, "Rice"),
    PEANUT("peanut", 1.0f, "Peanut"),
    ALMOND("almond", 1.15f, "Almond");

    companion object {
        private val byKey = entries.associateBy(TextScale::key)
        fun fromKey(key: String?): TextScale = when (key) {
            // Legacy keys from the plain-label iteration.
            "small" -> RICE
            "standard" -> PEANUT
            "large" -> ALMOND
            else -> byKey[key] ?: PEANUT
        }
    }
}

/**
 * View density for the Stand and Clippings (owner's #1): a horizontal slider
 * across four steps, detail to compact. Order matters — [ordinal] is the
 * slider's position.
 */
enum class ListDensity(val key: String, val label: String) {
    DETAIL("detail", "Detail"),
    LIST("list", "List"),
    SMALL_TILES("small-tiles", "Small tiles"),
    BIG_TILES("big-tiles", "Big tiles");

    companion object {
        private val byKey = entries.associateBy(ListDensity::key)
        fun fromKey(key: String?): ListDensity = byKey[key] ?: DETAIL
    }
}

data class AppSettings(
    /** The middle tint carries the check in the owner's mock — Paper is the default. */
    val themeMode: ThemeMode = ThemeMode.PAPER,
    val readerFont: ReaderFont = ReaderFont.GROTESK_CLASSIC,
    val showReadingTime: Boolean = true,
    val textScale: TextScale = TextScale.PEANUT,
    /**
     * The lens's loaded-language highlighting (owner: the reader's eye toggle
     * was confusing and off-mock). Off by default; a single Settings switch
     * turns it on. Detection is real; the rewrite stays honestly stubbed.
     */
    val highlightLoadedLanguage: Boolean = false,
    /**
     * Reader chrome (owner: the back control must be obvious by default, with an
     * immersive mode as the opt-in). `false` shows the visible back affordance
     * and utility bar; `true` hides them for a gestures-only page. Introduced in
     * onboarding; toggleable in Settings.
     */
    val immersiveReader: Boolean = false,
    /**
     * Configurable two-finger reader gestures (owner's #11: defaults are fine,
     * but they must be optional — second-level). Both default on; a reader for
     * whom they misfire can switch either off in Settings › Gestures.
     */
    val twoFingerBrightness: Boolean = true,
    val twoFingerThemeFlick: Boolean = true,
    /** First-run onboarding has been completed or skipped (owner's #19). */
    val onboarded: Boolean = false,
    /**
     * Nooz Flash (owner's #6): today's flowed headlines compressed to a line of
     * 10 words or fewer, with a "go deeper" expansion. Off by default, like the
     * rest of the reader-intelligence tools — it's a standing generation over
     * everything that flowed, not a one-off the reader asked for.
     */
    val noozFlashEnabled: Boolean = false,
    /** The Stand's and Clippings' shared view density (owner's #1). Detail by default — nothing changes until the reader moves the slider. */
    val listDensity: ListDensity = ListDensity.DETAIL,
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

        /**
         * Every sector a band centred on [centerLonDeg] with half-width
         * [bandHalfDeg] touches, widest-first-hit order (owner's #8: widening
         * the globe's pinch band used to have no effect at all short of the
         * full-global snap, so the picker only ever showed one sector or
         * "Global" — nothing in between). Built by sampling [forLongitude]
         * across the band rather than re-deriving sector-boundary/wraparound
         * math a second time, so it can never disagree with the single-point
         * lookup every other caller already relies on.
         */
        fun forBand(centerLonDeg: Double, bandHalfDeg: Double): List<Region> {
            if (bandHalfDeg >= GlobeModel.GLOBAL_BAND_THRESHOLD) return listOf(GLOBAL)
            val hit = LinkedHashSet<Region>()
            val step = 2.0
            var d = -bandHalfDeg
            while (d <= bandHalfDeg) {
                hit.add(forLongitude(centerLonDeg + d))
                d += step
            }
            return hit.toList()
        }
    }
}
