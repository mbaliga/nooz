package xyz.mdhv.riverwip.feature.reader

import androidx.compose.runtime.Composable

/**
 * The reader surface (brief §P3): article list, tap through to a typography-first
 * reading view. Selection state lives in [ReaderViewModel] — no cross-module
 * navigation graph needed for this single-feature flow.
 */
@Composable
fun ReaderScreen(vm: ReaderViewModel) {
    val selected = vm.selectedItem
    if (selected == null) {
        ArticleListScreen(vm, onOpenItem = { vm.openItem(it) })
    } else {
        ReaderDetailScreen(vm, selected, onBack = { vm.closeItem() })
    }
}
