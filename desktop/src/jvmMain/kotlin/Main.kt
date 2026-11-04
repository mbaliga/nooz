package xyz.mdhv.riverwip.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Desktop head skeleton: an empty window. No screens, no data, no network yet. */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Nooz") {
        MaterialTheme {}
    }
}
