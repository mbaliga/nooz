package xyz.mdhv.riverwip.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.model.AppSettings
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.TextScale
import xyz.mdhv.riverwip.model.ThemeMode

private val Context.settingsDataStore by preferencesDataStore(name = "display_settings")

/**
 * Display preferences (theme tint / reader font / reading time / text size) and
 * the standing reader filter (region + topics from the globe). Local DataStore
 * only — never synced or transmitted. Defaults live on [AppSettings]/[ReaderFilter].
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("reader_font")
        val READING_TIME = booleanPreferencesKey("show_reading_time")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val REGION = stringPreferencesKey("filter_region")
        val TOPICS = stringSetPreferencesKey("filter_topics")
    }

    fun observeSettings(): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME]),
            readerFont = ReaderFont.fromKey(prefs[Keys.FONT]),
            showReadingTime = prefs[Keys.READING_TIME] ?: true,
            textScale = TextScale.fromKey(prefs[Keys.TEXT_SCALE]),
        )
    }

    fun observeFilter(): Flow<ReaderFilter> = context.settingsDataStore.data.map { prefs ->
        ReaderFilter(
            region = Region.fromKey(prefs[Keys.REGION]),
            topicKeys = prefs[Keys.TOPICS] ?: emptySet(),
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

    suspend fun setTextScale(scale: TextScale) {
        context.settingsDataStore.edit { it[Keys.TEXT_SCALE] = scale.key }
    }

    suspend fun setFilter(filter: ReaderFilter) {
        context.settingsDataStore.edit {
            it[Keys.REGION] = filter.region.key
            it[Keys.TOPICS] = filter.topicKeys
        }
    }
}
