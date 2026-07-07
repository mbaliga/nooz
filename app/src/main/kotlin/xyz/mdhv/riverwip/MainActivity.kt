package xyz.mdhv.riverwip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mdhv.riverwip.design.RiverTheme
import xyz.mdhv.riverwip.feature.sources.SourcesScreen
import xyz.mdhv.riverwip.feature.sources.SourcesViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RiverApp() }
    }
}

/**
 * P1 shell: the sources surface, wired to the composition root. Real navigation
 * across sources · reader · river lands as those features arrive (P3/P4).
 */
@Composable
fun RiverApp() {
    val container = (LocalContext.current.applicationContext as RiverApplication).container
    RiverTheme {
        val vm: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory(container.sourceRepository))
        SourcesScreen(vm = vm, modifier = Modifier.fillMaxSize())
    }
}
