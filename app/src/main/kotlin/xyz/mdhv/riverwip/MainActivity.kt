package xyz.mdhv.riverwip

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import xyz.mdhv.riverwip.design.RiverTheme
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.feature.reader.ClippingsScreen
import xyz.mdhv.riverwip.feature.reader.ClippingsViewModel
import xyz.mdhv.riverwip.feature.reader.ReaderScreen
import xyz.mdhv.riverwip.feature.reader.ReaderViewModel
import xyz.mdhv.riverwip.feature.river.LoomScreen
import xyz.mdhv.riverwip.feature.river.LoomViewModel
import xyz.mdhv.riverwip.feature.sources.EditScreen
import xyz.mdhv.riverwip.feature.sources.SourcesViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RiverApp() }
    }
}

private enum class Screen { STAND, EDIT, SETTINGS, LOOM, CLIPPINGS }

private const val SPLASH_MILLIS = 1_100L

/**
 * The app shell, per the owner's flow map (2026-07): splash → the Nooz Stand.
 * No bottom navigation — the Stand's plus/EDIT opens the Edit flow (Sources /
 * Region & Topics), pulling down (or tapping the day bar) opens the loom, and
 * the reading room swipes right back to the Stand and left into Settings.
 * Theme tint, reading font, and text size follow the persisted Settings; a
 * two-finger flick in the reading room steps the tint, and a two-finger drag
 * slides in-app window brightness (no permission needed — window-level only).
 */
@Composable
fun RiverApp() {
    val context = LocalContext.current
    val container = (context.applicationContext as RiverApplication).container
    val settingsVm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container.settingsRepository,
            container.dictionaryRepository,
            container.dataExporter,
            container.byokConfigStore,
            container.modelCatalogueRepository,
        ),
    )
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    val readerVm: ReaderViewModel = viewModel(
        factory = ReaderViewModel.Factory(
            itemRepository = container.itemRepository,
            sourceRepository = container.sourceRepository,
            articleRepository = container.articleRepository,
            readEventRepository = container.readEventRepository,
            weeklyAggregateRepository = container.weeklyAggregateRepository,
            clippingRepository = container.clippingRepository,
            settingsRepository = container.settingsRepository,
            flashRouter = container.flashRouter,
        ),
    )
    val lensVm: LensViewModel = viewModel(
        factory = LensViewModel.Factory(container.inferenceRouter, container.dictionaryRepository),
    )
    val sourcesVm: SourcesViewModel = viewModel(
        factory = SourcesViewModel.Factory(
            repo = container.sourceRepository,
            catalogueRepo = container.catalogueRepository,
            settingsRepo = container.settingsRepository,
            itemRepository = container.itemRepository,
            weeklyAggregateRepository = container.weeklyAggregateRepository,
        ),
    )
    val loomVm: LoomViewModel = viewModel(
        factory = LoomViewModel.Factory(
            weeklyAggregateRepository = container.weeklyAggregateRepository,
            sourceRepository = container.sourceRepository,
            settingsRepository = container.settingsRepository,
        ),
    )
    val clippingsVm: ClippingsViewModel = viewModel(
        factory = ClippingsViewModel.Factory(container.clippingRepository),
    )

    // In-app window brightness: per-window, no permission, resets with the app.
    val activity = context as? Activity
    val adjustBrightness: (Float) -> Unit = { delta ->
        activity?.window?.let { w ->
            val attrs = w.attributes
            val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
            attrs.screenBrightness = (current + delta).coerceIn(0.05f, 1f)
            w.attributes = attrs
        }
    }

    RiverTheme(themeMode = settings.themeMode, readerFont = settings.readerFont, textScale = settings.textScale) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            // Splash once per launch; rememberSaveable keeps rotation from replaying it.
            var splashDone by rememberSaveable { mutableStateOf(false) }
            if (!splashDone) {
                LaunchedEffect(Unit) {
                    delay(SPLASH_MILLIS)
                    splashDone = true
                }
                SplashScreen()
                return@Box
            }

            // First-run onboarding (owner's #19). The splash delay covers the
            // DataStore load, so `onboarded` is settled by the time we're here.
            if (!settings.onboarded) {
                val modelCatalogue by settingsVm.modelCatalogue.collectAsStateWithLifecycle()
                OnboardingScreen(
                    byokConfig = settingsVm.byokConfig,
                    download = ModelDownloadUi(
                        models = settingsVm.downloadableModels(modelCatalogue),
                        downloadStates = settingsVm.modelDownloadStates,
                        isDownloaded = { settingsVm.isModelDownloaded(it) },
                        onDownload = { settingsVm.downloadModel(it) },
                        onDelete = { settingsVm.deleteModel(it) },
                        onRefresh = { settingsVm.refreshModelCatalogue() },
                        refreshing = settingsVm.modelCatalogueRefreshing,
                        error = settingsVm.modelCatalogueError,
                    ),
                    onFinish = { settingsVm.completeOnboarding() },
                    onSaveByok = { url, key, model -> settingsVm.saveByok(url, key, model) },
                    onClearByok = { settingsVm.clearByok() },
                    onSetImmersive = { settingsVm.setImmersiveReader(it) },
                )
                return@Box
            }

            var screen by rememberSaveable { mutableStateOf(Screen.STAND) }

            // The native back gesture must navigate, never exit mid-flow. The
            // immersive reader is a sub-state of the Stand (a selected item), so
            // back has to close the article too — otherwise the system pops the
            // single activity and the app quits (the reported bug).
            val readingArticle = readerVm.selectedItem != null
            BackHandler(enabled = screen != Screen.STAND || readingArticle) {
                when {
                    screen == Screen.LOOM && loomVm.showDatePicker -> loomVm.setDatePickerVisible(false)
                    screen != Screen.STAND -> screen = Screen.STAND
                    readingArticle -> readerVm.closeItem()
                }
            }

            when (screen) {
                Screen.STAND -> ReaderScreen(
                    vm = readerVm,
                    lensVm = lensVm,
                    showReadingTime = settings.showReadingTime,
                    highlightLoadedLanguage = settings.highlightLoadedLanguage,
                    immersiveReader = settings.immersiveReader,
                    noozFlashEnabled = settings.noozFlashEnabled,
                    onToggleLens = { settingsVm.setHighlightLoadedLanguage(!settings.highlightLoadedLanguage) },
                    onOpenEdit = { screen = Screen.EDIT },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenLoom = {
                        loomVm.reload()
                        screen = Screen.LOOM
                    },
                    onOpenDatePicker = {
                        loomVm.reload()
                        loomVm.setDatePickerVisible(true)
                        screen = Screen.LOOM
                    },
                    onOpenClippings = { screen = Screen.CLIPPINGS },
                    onBrightnessDelta = if (settings.twoFingerBrightness) adjustBrightness else { _ -> },
                    onThemeFlick = if (settings.twoFingerThemeFlick) {
                        { settingsVm.setTheme(settings.themeMode.next()) }
                    } else {
                        {}
                    },
                )
                Screen.EDIT -> EditScreen(
                    vm = sourcesVm,
                    onDone = {
                        screen = Screen.STAND
                        // New sources should show up without waiting on the
                        // background cadence; conditional GETs keep this cheap.
                        readerVm.refresh()
                    },
                    onOpenSettings = { screen = Screen.SETTINGS },
                )
                Screen.SETTINGS -> SettingsScreen(vm = settingsVm, onBack = { screen = Screen.STAND })
                Screen.LOOM -> LoomScreen(vm = loomVm, onClose = { screen = Screen.STAND })
                Screen.CLIPPINGS -> ClippingsScreen(vm = clippingsVm, onBack = { screen = Screen.STAND })
            }
        }
    }
}
