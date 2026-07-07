package xyz.mdhv.riverwip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.RiverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RiverApp() }
    }
}

/**
 * P0 themed shell: a single empty state that already speaks the register —
 * denominator-honest, descriptive, never "all the news". Real navigation
 * (sources · reader · river) is wired in later phases.
 */
@Composable
fun RiverApp() {
    RiverTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            EmptyState(
                title = "A reader for what flowed past",
                body = "This shows the shape of the news ${Copy.fromSources(0)}, " +
                    "against what you actually read. It never claims to show all the news. " +
                    "Add a source to begin.",
                modifier = Modifier.padding(padding),
            )
        }
    }
}
