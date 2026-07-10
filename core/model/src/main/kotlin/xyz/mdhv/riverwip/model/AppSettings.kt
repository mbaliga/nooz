package xyz.mdhv.riverwip.model

/**
 * App-level display preferences (owner's Settings mock, 2026-07: Theme, Font,
 * Show Reading Time). Pure domain types — persisted by `:core:data`'s
 * SettingsRepository, consumed by `:core:design`'s theme.
 *
 * The mock's fourth row, "Show Progress", was struck through in the owner's own
 * artwork — read as a decision *against* the feature, so it has no setting here
 * and no implementation.
 */
enum class ThemeMode(val key: String) {
    LIGHT("light"), SYSTEM("system"), DARK("dark");

    companion object {
        private val byKey = entries.associateBy(ThemeMode::key)
        fun fromKey(key: String?): ThemeMode = byKey[key] ?: LIGHT
    }
}

enum class ReaderFont(val key: String) {
    SERIF("serif"), SANS("sans"), SYSTEM("system");

    companion object {
        private val byKey = entries.associateBy(ReaderFont::key)
        fun fromKey(key: String?): ReaderFont = byKey[key] ?: SANS
    }
}

data class AppSettings(
    /** Every screen in the owner's mock set is paper-light — light is the default, not system. */
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val readerFont: ReaderFont = ReaderFont.SANS,
    val showReadingTime: Boolean = true,
)
