package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.DayMixBar
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.feature.lens.LensAnnotatedParagraph
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.Topic
import kotlin.math.ceil

/** Static estimate from content length (~200 wpm). Display-only — nothing is measured or stored (brief §3). */
private fun readingMinutes(paragraphs: List<String>): Int {
    val words = paragraphs.sumOf { it.split(Regex("\\s+")).count { w -> w.isNotBlank() } }
    return ceil(words / 200.0).toInt().coerceAtLeast(1)
}

/**
 * The immersive paper (owner's Paper mock + flow map). The article is a rigid
 * sheet that follows a one-finger horizontal drag and slides off with a crisp,
 * still edge (a drop-shadow that stays sharp as it moves): drag right past the
 * threshold to return to the stand, left for settings, otherwise it snaps back.
 * A two-finger vertical drag slides brightness; a two-finger flick steps the
 * theme (see [readerGestures]). The controls float over a gradient fade of the
 * text — no strip — the way the mock's bottom line does: reading progress, open
 * in browser, share, estimated time, and the day bar (which opens the loom).
 */
@Composable
fun ReaderDetailScreen(
    vm: ReaderViewModel,
    lensVm: LensViewModel,
    item: Item,
    showReadingTime: Boolean,
    lensOn: Boolean,
    saved: Boolean,
    immersive: Boolean,
    offsetX: Float,
    // 0f = full Paper, 1f = fully parked — the one value driving scale, shadow,
    // corner radius and (together with offsetX's sign) translation, read live
    // during the drag and only eased by the caller's settle animation after
    // release (owner's brief, 2026-07: never animate() the drag itself).
    progress: Float,
    parkedRoom: ReaderRoom?,
    onToggleLens: () -> Unit,
    onToggleClip: () -> Unit,
    onBack: () -> Unit,
    onRoomDragStart: (ReaderRoom) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (velocityX: Float) -> Unit,
    onParkedTap: () -> Unit,
    onOpenLoom: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onThemeFlick: () -> Unit,
) {
    val state by vm.articleState.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    val todayMix by vm.todayMix.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val topic = Classifier.dominantTopic(item.topics)
    val background = MaterialTheme.colorScheme.background

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX
                    // Lift-and-part: Paper shrinks, gains a shadow and rounds
                    // its corners together, all read directly off `progress`
                    // — the same value the drag is driving, never a tween.
                    val scale = 1f - progress * (1f - PAPER_MIN_SCALE)
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = progress * PAPER_MAX_SHADOW_DP.dp.toPx()
                    shape = RoundedCornerShape((progress * PAPER_MAX_CORNER_DP).dp)
                    clip = true
                }
                .background(background)
                .readerGestures(
                    parkedRoom = parkedRoom,
                    onRoomDragStart = onRoomDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onParkedTap = onParkedTap,
                    onBrightnessDelta = onBrightnessDelta,
                    onThemeFlick = onThemeFlick,
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Tokens.Spacing.md,
                    end = Tokens.Spacing.md,
                    top = Tokens.Spacing.xxl,
                    // Leave room for the floating controls + their fade.
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                item {
                    // Owner's Paper mock: the body begins at the screen's centre,
                    // with the title sitting just above it — so the header block
                    // fills the top ~half and its content bottoms out at centre.
                    Column(
                        modifier = Modifier.fillParentMaxHeight(0.48f),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs, Alignment.Bottom),
                    ) {
                        // Colour never alone: the dominant topic, named.
                        Text(
                            topic.placeholderLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = topic.toComposeColor(),
                        )
                        Text(item.title, style = MaterialTheme.typography.displayLarge)
                        val author = item.author
                        val sourceTitle = sourceTitles[item.sourceId]
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                sourceTitle ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!author.isNullOrBlank()) {
                                Text(
                                    author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                when (val s = state) {
                    is ArticleUiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(top = Tokens.Spacing.xxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = "Loading article"
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                    is ArticleUiState.Loaded -> items(s.paragraphs) { para ->
                        LensAnnotatedParagraph(
                            itemId = item.id,
                            text = para,
                            vm = lensVm,
                            style = MaterialTheme.typography.bodyLarge,
                            // A touch more room than the list's base spacedBy
                            // gap, stacked on top of it: at just Tokens.Spacing.md
                            // between every item, paragraph breaks read as
                            // barely more than an extra line — this widens the
                            // gap specifically between paragraphs so they read
                            // as distinct blocks (owner's #2, rendering quality).
                            modifier = Modifier.padding(bottom = Tokens.Spacing.xs),
                        )
                    }
                    is ArticleUiState.Fallback -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                            if (!s.summary.isNullOrBlank()) {
                                Text(s.summary, style = MaterialTheme.typography.bodyLarge)
                            }
                            Text(
                                "Couldn't extract the full article — open it in the browser to read the rest.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    // The river has banks (brief §3): the article itself has an explicit end too.
                    Text(
                        "— end of article —",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.Spacing.lg),
                    )
                }
            }

            // The bottom line: controls floating over a gradient fade of the text.
            ReaderUtilityBar(
                state = state,
                showReadingTime = showReadingTime,
                todayMix = todayMix,
                listState = listState,
                background = background,
                lensOn = lensOn,
                saved = saved,
                onToggleLens = onToggleLens,
                onToggleClip = onToggleClip,
                onOpenBrowser = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.canonicalUrl)),
                    )
                },
                onShare = {
                    NewspaperShare.share(
                        context = context,
                        title = item.title,
                        source = sourceTitles[item.sourceId],
                        author = item.author,
                        url = item.canonicalUrl,
                    )
                },
                onOpenLoom = onOpenLoom,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // Owner's #22: an obvious back control, on by default. Immersive mode
            // hides it (and everything else fades into the gestures-only page).
            if (!immersive) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Tokens.Spacing.xs),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to the stand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderUtilityBar(
    state: ArticleUiState,
    showReadingTime: Boolean,
    todayMix: List<Pair<Topic, Double>>,
    listState: LazyListState,
    background: Color,
    lensOn: Boolean,
    saved: Boolean,
    onToggleLens: () -> Unit,
    onToggleClip: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShare: () -> Unit,
    onOpenLoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) 0f
            else (((info.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1f) / total).coerceIn(0f, 1f)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, background, background))),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.sm, vertical = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressDial(progress = progress)
            // The lens: an unmistakable eye — bold ink when on, dimmed when off
            // (never the crossed-out incognito look). Toggles the loaded-language
            // highlighting, the same persisted setting Settings shows.
            IconButton(
                onClick = onToggleLens,
                modifier = Modifier.semantics { stateDescription = if (lensOn) "On" else "Off" },
            ) {
                // Non-colour channel: a filled pill behind the eye when on.
                Box(
                    modifier = if (lensOn) {
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.RemoveRedEye,
                        contentDescription = "Lens: loaded-language highlighting",
                        tint = if (lensOn) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.padding(Tokens.Spacing.xxs),
                    )
                }
            }
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Filled.Public, contentDescription = "Open in browser")
            }
            IconButton(onClick = onToggleClip) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "Remove clipping" else "Save as a clipping",
                    tint = if (saved) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share as a newspaper clipping")
            }
            val s = state
            if (showReadingTime && s is ArticleUiState.Loaded) {
                val minutes = remember(s.paragraphs) { readingMinutes(s.paragraphs) }
                Text(
                    "$minutes min",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Tokens.Spacing.sm)
                        .semantics { contentDescription = "Estimated reading time $minutes minutes" },
                )
            } else {
                Spacer(Modifier.width(Tokens.Spacing.sm))
            }
            if (todayMix.isNotEmpty()) {
                // 6dp bar, but a 48dp tap target (the bar is centred inside it).
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Open the day loom") { onOpenLoom() },
                    contentAlignment = Alignment.Center,
                ) {
                    DayMixBar(todayMix, Modifier.fillMaxWidth())
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The mock's small reading-progress ring: a faint track with a sweep from the top. */
@Composable
private fun ProgressDial(progress: Float) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val pct = (progress * 100).toInt()
    Canvas(
        Modifier
            .padding(start = Tokens.Spacing.xs, end = Tokens.Spacing.xxs)
            .size(20.dp)
            .semantics { contentDescription = "About $pct percent through the article" },
    ) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = ink,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
