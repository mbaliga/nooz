package xyz.mdhv.riverwip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import xyz.mdhv.riverwip.design.RiverTheme
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.feature.reader.ReaderScreen
import xyz.mdhv.riverwip.feature.reader.ReaderViewModel
import xyz.mdhv.riverwip.feature.river.RiverScreen
import xyz.mdhv.riverwip.feature.river.RiverViewModel
import xyz.mdhv.riverwip.feature.sources.SourcesScreen
import xyz.mdhv.riverwip.feature.sources.SourcesViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RiverApp() }
    }
}

private enum class TopLevelDestination { READER, RIVER, SOURCES }

private const val SPLASH_MILLIS = 1_100L

/**
 * The app shell: a brief splash (owner's mock), then the bottom-nav switch
 * between Reader (P3), River (P4), and Sources (P1) — kept as-is per the
 * owner's instruction; the mocks restyle the surfaces inside it. Theme and
 * reading font follow the persisted Settings.
 */
@Composable
fun RiverApp() {
    val container = (LocalContext.current.applicationContext as RiverApplication).container
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container.settingsRepository))
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    RiverTheme(themeMode = settings.themeMode, readerFont = settings.readerFont) {
        // Splash once per process-fresh launch; rememberSaveable keeps rotation
        // from replaying it.
        var splashDone by rememberSaveable { mutableStateOf(false) }
        if (!splashDone) {
            LaunchedEffect(Unit) {
                delay(SPLASH_MILLIS)
                splashDone = true
            }
            SplashScreen()
            return@RiverTheme
        }

        var destination by remember { mutableStateOf(TopLevelDestination.READER) }
        var showSettings by rememberSaveable { mutableStateOf(false) }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(vm = settingsVm, onBack = { showSettings = false })
            return@RiverTheme
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = destination == TopLevelDestination.READER,
                        onClick = { destination = TopLevelDestination.READER },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Reader") },
                    )
                    NavigationBarItem(
                        selected = destination == TopLevelDestination.RIVER,
                        onClick = { destination = TopLevelDestination.RIVER },
                        icon = { Icon(Icons.Filled.Timeline, contentDescription = null) },
                        label = { Text("River") },
                    )
                    NavigationBarItem(
                        selected = destination == TopLevelDestination.SOURCES,
                        onClick = { destination = TopLevelDestination.SOURCES },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Sources") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (destination) {
                    TopLevelDestination.READER -> {
                        val vm: ReaderViewModel = viewModel(
                            factory = ReaderViewModel.Factory(
                                itemRepository = container.itemRepository,
                                sourceRepository = container.sourceRepository,
                                articleRepository = container.articleRepository,
                                readEventRepository = container.readEventRepository,
                                weeklyAggregateRepository = container.weeklyAggregateRepository,
                            ),
                        )
                        val lensVm: LensViewModel = viewModel(factory = LensViewModel.Factory(container.inferenceRouter))
                        ReaderScreen(
                            vm = vm,
                            lensVm = lensVm,
                            showReadingTime = settings.showReadingTime,
                            onOpenSettings = { showSettings = true },
                        )
                    }
                    TopLevelDestination.RIVER -> {
                        val vm: RiverViewModel = viewModel(
                            factory = RiverViewModel.Factory(
                                weeklyAggregateRepository = container.weeklyAggregateRepository,
                                sourceRepository = container.sourceRepository,
                            ),
                        )
                        RiverScreen(vm)
                    }
                    TopLevelDestination.SOURCES -> {
                        val vm: SourcesViewModel = viewModel(
                            factory = SourcesViewModel.Factory(container.sourceRepository, container.catalogueRepository),
                        )
                        SourcesScreen(vm = vm, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
