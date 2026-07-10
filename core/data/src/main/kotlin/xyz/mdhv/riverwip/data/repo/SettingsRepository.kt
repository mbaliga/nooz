package xyz.mdhv.riverwip.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.model.AppSettings
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.ThemeMode

private val Context.settingsDataStore by preferencesDataStore(name = "display_settings")

/**
 * Display preferences (owner's Settings mock, 2026-07): theme, reader font,
 * reading-time visibility. Local DataStore only — like everything else here,
 * never synced or transmitted. Defaults live on [AppSettings].
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("reader_font")
        val READING_TIME = booleanPreferencesKey("show_reading_time")
    }

    fun observeSettings(): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = prefs[Keys.THEME]?.let(ThemeMode::fromKey) ?: defaults.themeMode,
            readerFont = prefs[Keys.FONT]?.let(ReaderFont::fromKey) ?: defaults.readerFont,
            showReadingTime = prefs[Keys.READING_TIME] ?: defaults.showReadingTime,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.key }
    }

    suspend fun setReaderFont(font: ReaderFont) {
        context.settingsDataStore.edit { it[Keys.FONT] = font.key }
    }

    suspend fun setShowReadingTime(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.READING_TIME] = show }
    }
}
