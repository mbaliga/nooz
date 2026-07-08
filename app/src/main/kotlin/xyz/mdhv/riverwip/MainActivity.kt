package xyz.mdhv.riverwip

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mdhv.riverwip.design.RiverTheme
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

/**
 * The app shell: a bottom-nav switch between Reader (P3), River (P4), and
 * Sources (P1). The lens (P5) surfaces join once its UI lands — it lives inside
 * the reader, not as its own top-level destination.
 */
@Composable
fun RiverApp() {
    val container = (LocalContext.current.applicationContext as RiverApplication).container
    RiverTheme {
        var destination by remember { mutableStateOf(TopLevelDestination.READER) }
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
                            ),
                        )
                        ReaderScreen(vm)
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
                        val vm: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory(container.sourceRepository))
                        SourcesScreen(vm = vm, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
