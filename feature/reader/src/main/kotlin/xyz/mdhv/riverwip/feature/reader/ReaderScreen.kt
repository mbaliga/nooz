package xyz.mdhv.riverwip.feature.reader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.feature.lens.LensViewModel

/**
 * The reader surface: the Nooz Stand, tap through to the immersive paper with
 * the lens woven in. Selection state lives in [ReaderViewModel] — no
 * cross-module navigation graph needed for this single-feature flow.
 *
 * The immersive paper is a rigid sheet that follows a one-finger horizontal
 * drag. Dragging it right reveals the **actual stand list, sitting still**
 * behind it (the owner's reference), and past the threshold it commits the
 * back; dragging left slides it off to settings. The slide offset is owned here
 * so the list can be rendered as the stationary layer underneath.
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
    onOpenClippings: () -> Unit,
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
            onOpenClippings = onOpenClippings,
        )
        return
    }

    val savedIds by vm.savedIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var containerWidth by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    // Settle after a drag: commit past ~a third of the width, else snap back.
    fun settle() {
        val w = containerWidth.toFloat()
        val threshold = w * 0.32f
        val target = when {
            w <= 0f -> 0f
            offsetX > threshold -> w
            offsetX < -threshold -> -w
            else -> 0f
        }
        scope.launch {
            animate(offsetX, target, animationSpec = tween(220)) { v, _ -> offsetX = v }
            when (target) {
                w -> vm.closeItem()
                -w -> onOpenSettings()
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { containerWidth = it.width }) {
        // The stand list, revealed and stationary while the sheet slides back.
        // Only mounted during a rightward slide so it never steals input at rest.
        if (offsetX > 0f) {
            ArticleListScreen(
                vm = vm,
                onOpenItem = { vm.openItem(it) },
                onOpenEdit = onOpenEdit,
                onOpenLoom = onOpenLoom,
            )
        }
        ReaderDetailScreen(
            vm = vm,
            lensVm = lensVm,
            item = selected,
            showReadingTime = showReadingTime,
            lensOn = highlightLoadedLanguage,
            saved = savedIds.contains(selected.id),
            offsetX = offsetX,
            onToggleLens = onToggleLens,
            onToggleClip = { vm.toggleClip(selected) },
            onDrag = { offsetX += it },
            onDragEnd = { settle() },
            onOpenLoom = onOpenLoom,
            onBrightnessDelta = onBrightnessDelta,
            onThemeFlick = onThemeFlick,
        )
    }
}
