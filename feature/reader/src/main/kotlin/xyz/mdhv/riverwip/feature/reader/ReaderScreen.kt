package xyz.mdhv.riverwip.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import xyz.mdhv.riverwip.feature.lens.LensViewModel

/**
 * The reader surface: the Nooz Stand, tap through to the immersive paper with
 * the lens woven in. Selection state lives in [ReaderViewModel] — no
 * cross-module navigation graph needed for this single-feature flow.
 */
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    lensVm: LensViewModel,
    showReadingTime: Boolean,
    highlightLoadedLanguage: Boolean,
    onToggleLens: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLoom: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onThemeFlick: () -> Unit,
) {
    // The lens's underlines follow the persisted Setting; the reader's eye
    // toggle flips that same setting, so the two never disagree.
    LaunchedEffect(highlightLoadedLanguage) {
        lensVm.updateUnderlinesEnabled(highlightLoadedLanguage)
    }

    val selected = vm.selectedItem
    if (selected == null) {
        ArticleListScreen(
            vm = vm,
            onOpenItem = { vm.openItem(it) },
            onOpenEdit = onOpenEdit,
            onOpenLoom = onOpenLoom,
        )
    } else {
        ReaderDetailScreen(
            vm = vm,
            lensVm = lensVm,
            item = selected,
            showReadingTime = showReadingTime,
            lensOn = highlightLoadedLanguage,
            onToggleLens = onToggleLens,
            onBack = { vm.closeItem() },
            onOpenSettings = onOpenSettings,
            onOpenLoom = onOpenLoom,
            onBrightnessDelta = onBrightnessDelta,
            onThemeFlick = onThemeFlick,
        )
    }
}
