package xyz.mdhv.riverwip.feature.reader

import androidx.compose.runtime.Composable
import xyz.mdhv.riverwip.feature.lens.LensViewModel

/**
 * The reader surface (brief §P3): article list, tap through to a typography-first
 * reading view with the lens woven in (brief §P5). Selection state lives in
 * [ReaderViewModel] — no cross-module navigation graph needed for this
 * single-feature flow.
 */
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    lensVm: LensViewModel,
    showReadingTime: Boolean,
    onOpenSettings: () -> Unit,
) {
    val selected = vm.selectedItem
    if (selected == null) {
        ArticleListScreen(vm, onOpenItem = { vm.openItem(it) }, onOpenSettings = onOpenSettings)
    } else {
        ReaderDetailScreen(vm, lensVm, selected, showReadingTime, onBack = { vm.closeItem() })
    }
}
