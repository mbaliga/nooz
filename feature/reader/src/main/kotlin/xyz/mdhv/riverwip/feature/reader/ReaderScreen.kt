package xyz.mdhv.riverwip.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import kotlin.math.abs

private const val PARK_THRESHOLD = 0.45f
private const val FLICK_VELOCITY_DP_PER_SEC = 1200f
private const val SETTLE_MS = 300

/**
 * The reader surface: the Nooz Stand, tap through to the immersive paper with
 * the lens woven in. Selection state lives in [ReaderViewModel] — no
 * cross-module navigation graph needed for this single-feature flow.
 *
 * Lift-and-part navigation (owner's brief, 2026-07): the immersive paper is a
 * rigid sheet that only starts moving from a drag that *originates* in the
 * ~24dp edge zone — an interior swipe never hijacks the reading gesture. The
 * drag then drives one `progress` value directly off the finger, every
 * frame, no animate() in the loop: Paper shrinks, gains a shadow, rounds its
 * corners and translates away, while the room the drag is heading toward
 * (Stand to the left, Settings to the right) slides in under it at the same
 * rate. Only on release does a single eased tween take over, animating the
 * *remaining* distance to wherever the gesture committed: past ~45% of the
 * drag (or a fast flick) parks Paper as a small floating card — at least
 * 64dp of it always left showing, still tap/drag-interactive to return —
 * otherwise it snaps back to full-screen. Parking never closes the article:
 * the same `ReaderDetailScreen` instance (and its `LazyListState`) stays
 * mounted the whole time, just resized and repositioned, so the reader's
 * exact scroll position survives every trip to either room and back.
 */
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    lensVm: LensViewModel,
    showReadingTime: Boolean,
    highlightLoadedLanguage: Boolean,
    immersiveReader: Boolean,
    noozFlashEnabled: Boolean,
    onToggleLens: () -> Unit,
    onOpenEdit: () -> Unit,
    // The right-hand room's content, supplied by the caller so this
    // feature module never needs a dependency on wherever Settings lives —
    // `onBack` is this screen's own un-park, wired through so Settings'
    // own back arrow returns to the floating-card flow, not a nav pop.
    settingsRoom: @Composable (onBack: () -> Unit) -> Unit,
    onOpenLoom: () -> Unit,
    onOpenDatePicker: () -> Unit,
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

    // Declared unconditionally (never behind the `selected == null` branch
    // below) so the slide rig survives every round-trip through the article
    // list, including exits this composable's own gestures don't know about —
    // the system back button closes the item straight through the ViewModel
    // (owner's #1: "the flow still breaks sometimes" — reopening a just-closed
    // article showed nothing, because the rig was left wherever it last was).
    val scope = rememberCoroutineScope()
    var containerWidth by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var enteredId by remember { mutableStateOf<String?>(null) }
    // Which room Paper is currently resting over as a floating card — null
    // while Paper is full-screen (dragging or at rest).
    var parkedRoom by remember { mutableStateOf<ReaderRoom?>(null) }
    // The room a *fresh* edge-origin drag is heading toward, fixed for the
    // gesture's duration. Stays null while parked — there, the room being
    // dragged is simply `parkedRoom` itself.
    var draggingRoom by remember { mutableStateOf<ReaderRoom?>(null) }

    // The floating card's minimum visible band (owner's brief: "at least
    // 64dp"), and the fast-flick velocity that commits a park regardless of
    // distance dragged.
    val peekPx = with(LocalDensity.current) { 64.dp.toPx() }
    val flickVelocityPx = with(LocalDensity.current) { FLICK_VELOCITY_DP_PER_SEC.dp.toPx() }

    // How far Paper's own edge is inset by its parked scale — subtracted out
    // of the drag range so the *visible* strip at full park is really 64dp,
    // not 64dp minus however much scaling ate off that edge. A plain function
    // rather than a `val`: the one-finger gesture's `pointerInput` only
    // relaunches when `parkedRoom` changes, so callbacks it captures can run
    // for many frames on an old closure — reading `containerWidth` (state)
    // through a function call, instead of a `val` frozen at some earlier
    // composition, keeps `settle()`/`onDrag` correct regardless.
    fun dragRange(): Float {
        val scaleInsetPx = containerWidth * (1f - PAPER_MIN_SCALE) / 2f
        return (containerWidth - peekPx - scaleInsetPx).coerceAtLeast(1f)
    }

    // 0f = full Paper, 1f = fully parked. The single value driving Paper's
    // scale/shadow/corner-radius and the incoming room's slide-in, read live
    // off `offsetX` every frame — never animated directly during a drag.
    val progress = (abs(offsetX) / dragRange()).coerceIn(0f, 1f)

    // The one place the rig resets: whenever there's no open article, at rest,
    // regardless of which of the several exits (drag-settle, the back button,
    // system back) caused it.
    LaunchedEffect(selected) {
        if (selected == null) {
            offsetX = 0f
            enteredId = null
            parkedRoom = null
            draggingRoom = null
        }
    }

    if (selected == null) {
        ArticleListScreen(
            vm = vm,
            noozFlashEnabled = noozFlashEnabled,
            onOpenItem = { vm.openItem(it) },
            onOpenEdit = onOpenEdit,
            onOpenLoom = onOpenLoom,
            onOpenDatePicker = onOpenDatePicker,
            onOpenClippings = onOpenClippings,
        )
        return
    }

    val savedIds by vm.savedIds.collectAsStateWithLifecycle()

    // Slide the detail in from the right over the stationary stand (owner's
    // reference: opening an article slides it in; the list stays still behind).
    // Runs once per newly-opened item, as soon as the width is known — a fresh
    // article always opens at full Paper, even if the previous one was parked.
    LaunchedEffect(selected.id, containerWidth) {
        if (containerWidth > 0 && enteredId != selected.id) {
            parkedRoom = null
            draggingRoom = null
            offsetX = containerWidth.toFloat()
            animate(offsetX, 0f, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            enteredId = selected.id
        }
    }

    // Settle after a drag: past ~45% of the drag (or a fast flick), Paper
    // parks as a floating card over whichever room it was heading toward;
    // short of that, it snaps back to full-screen. The same logic handles a
    // drag starting *from* a parked rest, so dragging the floating card back
    // past halfway returns it to full Paper — parking is never a one-way
    // trip, and neither direction ever closes the article on its own.
    fun settle(velocityX: Float) {
        val room = draggingRoom ?: parkedRoom ?: return
        if (containerWidth <= 0) return
        val range = dragRange()
        val liveProgress = (abs(offsetX) / range).coerceIn(0f, 1f)
        val towardPark = if (room == ReaderRoom.STAND) velocityX else -velocityX
        val committing = liveProgress > PARK_THRESHOLD || towardPark > flickVelocityPx
        val target = when {
            !committing -> 0f
            room == ReaderRoom.STAND -> range
            else -> -range
        }
        scope.launch {
            animate(offsetX, target, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            parkedRoom = if (committing) room else null
            draggingRoom = null
        }
    }

    // Un-park back to full Paper: the floating card's tap affordance, its own
    // "back" controls (Settings' app bar arrow), and the hardware back button.
    fun unpark() {
        if (parkedRoom == null) return
        scope.launch {
            animate(offsetX, 0f, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            parkedRoom = null
            draggingRoom = null
        }
    }

    // The visible back control: slide the sheet off to the right, then close —
    // the one affordance that actually ends the reading session, from either
    // full Paper or parked.
    fun slideBack() {
        val w = containerWidth.toFloat()
        scope.launch {
            if (w > 0f) animate(offsetX, w, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            vm.closeItem()
        }
    }

    // Hardware back unwinds one step at a time: parked → full Paper, full
    // Paper → closed. Registered after the `selected == null` return above,
    // so it's only live while an article is actually open.
    BackHandler(enabled = selected != null) {
        if (parkedRoom != null) unpark() else vm.closeItem()
    }

    Box(Modifier.fillMaxSize().onSizeChanged { containerWidth = it.width }) {
        // The stand, sliding in from off-screen left at the same rate Paper
        // parts — visible (and interactive) the moment a Stand-ward drag
        // starts, not only once it's parked.
        if (offsetX > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -(1f - progress) * containerWidth },
            ) {
                ArticleListScreen(
                    vm = vm,
                    noozFlashEnabled = noozFlashEnabled,
                    onOpenItem = { vm.openItem(it) },
                    onOpenEdit = onOpenEdit,
                    onOpenLoom = onOpenLoom,
                    onOpenDatePicker = onOpenDatePicker,
                    onOpenClippings = onOpenClippings,
                )
            }
        }
        // Settings, sliding in from off-screen right the same way — the
        // symmetric second room, embedded rather than navigated to, so its
        // own back arrow can un-park instead of tearing this screen down.
        if (offsetX < 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - progress) * containerWidth },
            ) {
                settingsRoom { unpark() }
            }
        }
        ReaderDetailScreen(
            vm = vm,
            lensVm = lensVm,
            item = selected,
            showReadingTime = showReadingTime,
            lensOn = highlightLoadedLanguage,
            saved = savedIds.contains(selected.id),
            immersive = immersiveReader,
            offsetX = offsetX,
            progress = progress,
            parkedRoom = parkedRoom,
            onToggleLens = onToggleLens,
            onToggleClip = { vm.toggleClip(selected) },
            onBack = { slideBack() },
            onRoomDragStart = { room -> draggingRoom = room },
            onDrag = { dx ->
                val room = draggingRoom ?: parkedRoom
                if (room != null && containerWidth > 0) {
                    val range = dragRange()
                    offsetX = when (room) {
                        ReaderRoom.STAND -> (offsetX + dx).coerceIn(0f, range)
                        ReaderRoom.SETTINGS -> (offsetX + dx).coerceIn(-range, 0f)
                    }
                }
            },
            onDragEnd = { velocity -> settle(velocity) },
            onParkedTap = { unpark() },
            onOpenLoom = onOpenLoom,
            onBrightnessDelta = onBrightnessDelta,
            onThemeFlick = onThemeFlick,
        )
    }
}
