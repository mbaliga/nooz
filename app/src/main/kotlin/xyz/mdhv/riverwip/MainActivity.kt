package xyz.mdhv.riverwip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import xyz.mdhv.riverwip.design.RiverTheme
import xyz.mdhv.riverwip.design.R as DesignR
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.feature.reader.ClippingsScreen
import xyz.mdhv.riverwip.feature.reader.ClippingsViewModel
import xyz.mdhv.riverwip.feature.reader.ReaderScreen
import xyz.mdhv.riverwip.feature.reader.ReaderScreenTwoPane
import xyz.mdhv.riverwip.feature.reader.ReaderViewModel
import xyz.mdhv.riverwip.feature.reader.TWO_PANE_MIN_WIDTH
import xyz.mdhv.riverwip.feature.river.LoomScreen
import xyz.mdhv.riverwip.feature.river.LoomViewModel
import xyz.mdhv.riverwip.feature.sources.EditScreen
import xyz.mdhv.riverwip.feature.sources.EditTab
import xyz.mdhv.riverwip.feature.sources.SourcesViewModel
import xyz.mdhv.riverwip.widget.WidgetDestination
import xyz.mdhv.riverwip.widget.WidgetLinks
import xyz.mdhv.riverwip.widget.WidgetRequest

class MainActivity : ComponentActivity() {
    // The reader's chosen interface language, applied before any resource is
    // resolved. A no-op on API 33+, where the platform has already done it.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    /** Where a home-screen widget tap asked to go, until the shell has gone there. */
    private var widgetRequest by mutableStateOf<WidgetRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after a rotation the intent is the same one,
        // already acted on. WidgetLinks.parse also ignores a relaunch from
        // Recents, which hands back the tap that first started the task.
        if (savedInstanceState == null) widgetRequest = WidgetLinks.parse(intent)
        setContent {
            RiverApp(widgetRequest = widgetRequest, onWidgetRequestHandled = { widgetRequest = null })
        }
    }

    /** A widget tap while Nooz is already open arrives here (the tap's intent is single-top). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WidgetLinks.parse(intent)?.let { widgetRequest = it }
    }
}

private enum class Screen { STAND, EDIT, SETTINGS, LOOM, CLIPPINGS, LENS_WORDS }

private const val SPLASH_MILLIS = 1_100L

/**
 * The app shell, per the owner's flow map (2026-07). No bottom navigation —
 * the Stand's filter-summary text ("Global | All") opens Edit's Sources tab,
 * the settings cog beside it opens Edit's Settings tab (Edit now carries
 * Sources, Region & Topics, *and* Settings itself — owner: "it needn't be in
 * the settings cog"), pulling down (or tapping the day bar) opens the loom,
 * and the reading room swipes right back to the Stand and left into Settings.
 * Theme tint, reading font, and text size follow the persisted Settings; a
 * two-finger flick in the reading room steps the tint, and a two-finger drag
 * slides in-app window brightness (no permission needed — window-level only).
 */
@Composable
fun RiverApp(widgetRequest: WidgetRequest? = null, onWidgetRequestHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as RiverApplication).container
    val settingsVm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container.settingsRepository,
            container.dictionaryRepository,
            container.translationRepository,
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
            todayInHistoryRepository = container.todayInHistoryRepository,
            flashRouter = container.flashRouter,
            ttsProvider = container.ttsProvider,
        ),
    )
    val lensVm: LensViewModel = viewModel(
        factory = LensViewModel.Factory(
            container.inferenceRouter,
            container.dictionaryRepository,
            container.translationRepository,
        ),
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
            itemRepository = container.itemRepository,
            readEventRepository = container.readEventRepository,
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

    // System-bar icon contrast follows the *app's* resolved tint, not the OS
    // night setting. enableEdgeToEdge()'s default SystemBarStyle.auto answers
    // from Configuration.UI_MODE_NIGHT, which stops being the same question as
    // "what is Nooz painting right now" the moment a reader picks a tint by
    // hand — or, before D34, simply left the app on light Paper while the phone
    // said night. Either way the bars drew light icons onto a light surface,
    // which is the unreadable-in-dark-mode report this fixes. Re-runs whenever
    // the tint or the phone's night setting changes.
    val systemDark = isSystemInDarkTheme()
    val darkSurface = settings.themeMode.isDarkSurface(systemDark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkSurface
                    isAppearanceLightNavigationBars = !darkSurface
                }
            }
        }
    }

    // Rotation lock: pin the current orientation, or hand it back to the sensor.
    // (On Android 16+, screens >= 600dp ignore app orientation requests.)
    LaunchedEffect(settings.rotationLock) {
        activity?.requestedOrientation = if (settings.rotationLock) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    RiverTheme(
        themeMode = settings.themeMode,
        readerFont = settings.readerFont,
        fontScale = settings.fontScale,
        fontWeight = settings.fontWeight,
        ligatures = settings.ligatures,
        textAlign = settings.textAlign,
        pageTurn = settings.readerTransition == xyz.mdhv.riverwip.model.ReaderTransition.PAGE,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            // Tablet/large-screen split (owner: "the list and reading panel
            // visible together" on larger formats) — Material's own list-detail
            // breakpoint (840dp). Only the Stand/reader pair splits; Edit,
            // Settings, the loom, and Clippings stay full-screen at any width —
            // they're occasional utility screens, not the primary reading flow.
            val isExpandedWidth = maxWidth >= TWO_PANE_MIN_WIDTH

            // Splash once per launch; rememberSaveable keeps rotation from replaying it.
            var splashDone by rememberSaveable { mutableStateOf(false) }
            if (!splashDone) {
                LaunchedEffect(Unit) {
                    delay(SPLASH_MILLIS)
                    splashDone = true
                }
                SplashScreen()
                return@BoxWithConstraints
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
                    onQuickSetup = { sourcesVm.quickSetup() },
                    onSaveByok = { url, key, model -> settingsVm.saveByok(url, key, model) },
                    onClearByok = { settingsVm.clearByok() },
                    onSetImmersive = { settingsVm.setImmersiveReader(it) },
                )
                return@BoxWithConstraints
            }

            var screen by rememberSaveable { mutableStateOf(Screen.STAND) }
            // Which Edit tab to land on — the Stand's filter-summary text opens
            // Sources ("the current sources thing"), the settings cog opens
            // Settings; Edit shows both inline now, not behind a separate gear.
            var editStartTab by rememberSaveable { mutableStateOf(EditTab.SOURCES) }
            // Edit keeps its own tab once open, so a widget that asks for a
            // particular tab opens a fresh Edit rather than the one on screen.
            var editEntry by rememberSaveable { mutableIntStateOf(0) }

            // A home-screen widget tap (docs/widgets.md). Handled only here,
            // past the splash and onboarding, so a tap during first run waits
            // for the shell rather than skipping onboarding. A number's tap
            // lands where that number's rule is explained.
            LaunchedEffect(widgetRequest) {
                val request = widgetRequest ?: return@LaunchedEffect
                when (request.destination) {
                    WidgetDestination.STAND -> screen = Screen.STAND
                    WidgetDestination.ITEM, WidgetDestination.CLIPPING -> {
                        val item = request.itemId?.let { container.itemRepository.byId(it) }
                        if (item != null) {
                            readerVm.openItem(item)
                            screen = Screen.STAND
                        } else {
                            // Aged out of retention: a clipping still has its
                            // shelf; a headline has only the Stand.
                            screen = if (request.destination == WidgetDestination.CLIPPING) Screen.CLIPPINGS else Screen.STAND
                        }
                    }
                    WidgetDestination.CROSS_SECTION -> {
                        editStartTab = EditTab.REGION_TOPICS
                        editEntry++
                        screen = Screen.EDIT
                    }
                    WidgetDestination.SOURCES -> {
                        editStartTab = EditTab.SOURCES
                        editEntry++
                        screen = Screen.EDIT
                    }
                    WidgetDestination.SETTINGS -> {
                        editStartTab = EditTab.SETTINGS
                        editEntry++
                        screen = Screen.EDIT
                    }
                    WidgetDestination.CLIPPINGS -> screen = Screen.CLIPPINGS
                }
                onWidgetRequestHandled()
            }

            // The native back gesture must navigate, never exit mid-flow — the
            // single activity would otherwise just pop and quit (the reported
            // bug). Closing an open article on back is ReaderScreen's own job
            // now (it unwinds parked → full Paper → closed one step at a time);
            // this level only ever needs to leave a non-Stand screen.
            BackHandler(enabled = screen != Screen.STAND) {
                if (screen == Screen.LOOM && loomVm.showDatePicker) {
                    loomVm.setDatePickerVisible(false)
                } else {
                    screen = Screen.STAND
                }
            }

            // Shared between the phone single-pane and the tablet two-pane Stand
            // (below) so the two never drift apart on what each control does.
            val onOpenEdit = { editStartTab = EditTab.SOURCES; screen = Screen.EDIT }
            val onOpenEditSettings = { editStartTab = EditTab.SETTINGS; screen = Screen.EDIT }
            val onOpenLoom = {
                loomVm.reload()
                screen = Screen.LOOM
            }
            val onOpenDatePicker = {
                loomVm.reload()
                loomVm.setDatePickerVisible(true)
                screen = Screen.LOOM
            }
            val onOpenClippings = { screen = Screen.CLIPPINGS }

            // Every top-level screen swap happens inside one activity and one
            // composable, so nothing tells TalkBack the screen changed: a
            // reader who opened the Loom got no announcement at all, only a
            // silently different set of nodes under the same window. A
            // `paneTitle` that changes with `screen` is what Compose turns into
            // the platform's window-state-changed event, which is the event
            // TalkBack reads aloud as "Loom".
            val paneTitles = mapOf(
                Screen.STAND to stringResource(DesignR.string.screen_paper),
                Screen.EDIT to stringResource(DesignR.string.screen_sources_and_settings),
                Screen.SETTINGS to stringResource(DesignR.string.screen_settings),
                Screen.LOOM to stringResource(DesignR.string.screen_loom),
                Screen.CLIPPINGS to stringResource(DesignR.string.screen_clippings),
                Screen.LENS_WORDS to stringResource(DesignR.string.screen_lens_words),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { paneTitle = paneTitles.getValue(screen) },
            ) {
            when (screen) {
                Screen.STAND -> if (isExpandedWidth) {
                    // Tablet/large-screen: list and reader are both always on
                    // screen, so there's no "room" to slide into for Settings —
                    // its cog already opens the full Edit/Settings screen either way.
                    ReaderScreenTwoPane(
                        vm = readerVm,
                        lensVm = lensVm,
                        showReadingTime = settings.showReadingTime,
                        highlightLoadedLanguage = settings.highlightLoadedLanguage,
                        immersiveReader = settings.immersiveReader,
                        noozFlashEnabled = settings.noozFlashEnabled,
                        noozCastEnabled = settings.noozCastEnabled,
                        todayInHistoryEnabled = settings.todayInHistoryEnabled,
                        paperGrain = settings.paperGrain,
                        readMarkStyle = settings.readMarkStyle,
                        unreadPinchFilter = settings.unreadPinchFilter,
                        lensDisabledDefaultTerms = settings.lensDisabledDefaultTerms,
                        lensCustomTerms = settings.lensCustomTerms,
                        showFeedImages = settings.showFeedImages,
                        hideNsfwImages = settings.hideNsfwImages,
                        imageStyle = settings.imageStyle,
                        readingAsideStyle = settings.readingAsideStyle,
                        onToggleLens = { settingsVm.setHighlightLoadedLanguage(!settings.highlightLoadedLanguage) },
                        onOpenEdit = onOpenEdit,
                        onOpenEditSettings = onOpenEditSettings,
                        onOpenLoom = onOpenLoom,
                        onOpenDatePicker = onOpenDatePicker,
                        onOpenClippings = onOpenClippings,
                    )
                } else {
                    ReaderScreen(
                        vm = readerVm,
                        lensVm = lensVm,
                        showReadingTime = settings.showReadingTime,
                        highlightLoadedLanguage = settings.highlightLoadedLanguage,
                        immersiveReader = settings.immersiveReader,
                        noozFlashEnabled = settings.noozFlashEnabled,
                        noozCastEnabled = settings.noozCastEnabled,
                        todayInHistoryEnabled = settings.todayInHistoryEnabled,
                        paperGrain = settings.paperGrain,
                        readMarkStyle = settings.readMarkStyle,
                        unreadPinchFilter = settings.unreadPinchFilter,
                        lensDisabledDefaultTerms = settings.lensDisabledDefaultTerms,
                        lensCustomTerms = settings.lensCustomTerms,
                        showFeedImages = settings.showFeedImages,
                        hideNsfwImages = settings.hideNsfwImages,
                        imageStyle = settings.imageStyle,
                        readingAsideStyle = settings.readingAsideStyle,
                        onToggleLens = { settingsVm.setHighlightLoadedLanguage(!settings.highlightLoadedLanguage) },
                        onOpenEdit = onOpenEdit,
                        onOpenEditSettings = onOpenEditSettings,
                        // The reader's right room is the *compact* reading settings
                        // (owner #2); "More settings" opens the Reader tab of
                        // Edit itself now, not the standalone Settings page —
                        // that page was a dead end with no tab bar of its own
                        // (owner: "orphaned"), while Edit's Reader tab is the
                        // exact same content, reachable from everywhere else.
                        settingsRoom = { onBack ->
                            SettingsScreen(
                                vm = settingsVm,
                                onBack = onBack,
                                compact = true,
                                onOpenAll = onOpenEditSettings,
                            )
                        },
                        onOpenLoom = onOpenLoom,
                        onOpenDatePicker = onOpenDatePicker,
                        onOpenClippings = onOpenClippings,
                        onBrightnessDelta = if (settings.twoFingerBrightness) adjustBrightness else { _ -> },
                        onThemeFlick = if (settings.twoFingerThemeFlick) {
                            { settingsVm.setTheme(settings.themeMode.next(systemDark)) }
                        } else {
                            {}
                        },
                    )
                }
                Screen.EDIT -> key(editEntry) { EditScreen(
                    vm = sourcesVm,
                    onDone = {
                        screen = Screen.STAND
                        // New sources should show up without waiting on the
                        // background cadence; conditional GETs keep this cheap.
                        readerVm.refresh()
                    },
                    startTab = editStartTab,
                    settingsTab = {
                        SettingsBody(
                            vm = settingsVm,
                            compact = false,
                            onOpenLensWordList = { screen = Screen.LENS_WORDS },
                            // About is its own Edit tab now (below); showing
                            // it again at the bottom of this one would just
                            // duplicate it.
                            showAbout = false,
                        )
                    },
                    aboutTab = { AboutTab() },
                ) }
                Screen.SETTINGS -> SettingsScreen(
                    vm = settingsVm,
                    onBack = { screen = Screen.STAND },
                    onOpenLensWordList = { screen = Screen.LENS_WORDS },
                )
                Screen.LENS_WORDS -> LensWordListScreen(vm = settingsVm, onBack = { screen = Screen.STAND })
                Screen.LOOM -> LoomScreen(
                    vm = loomVm,
                    onClose = { screen = Screen.STAND },
                    // The loom is where you notice, the Stand is where you
                    // read — tapping any item it surfaces opens it in the
                    // reader. Currently unused while Framings (the one mode
                    // that opened items) is disabled; kept live for when it
                    // returns.
                    onOpenItem = { item ->
                        readerVm.openItem(item)
                        screen = Screen.STAND
                    },
                )
                Screen.CLIPPINGS -> ClippingsScreen(
                    vm = clippingsVm,
                    paperGrain = settings.paperGrain,
                    onBack = { screen = Screen.STAND },
                )
            }
            }
        }
    }
}

