package xyz.mdhv.riverwip.feature.reader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
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

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var containerWidth by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    // Settle the sheet after a drag: commit past ~a third of the width, else snap back.
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
                w -> onBack()
                -w -> onOpenSettings()
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { containerWidth = it.width }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX
                    // A rigid sheet with a hard shadowed edge — crisp as it slides.
                    shadowElevation = if (offsetX != 0f) 24.dp.toPx() else 0f
                    shape = RectangleShape
                    clip = false
                }
                .background(background)
                .readerGestures(
                    onDrag = { d -> offsetX += d },
                    onDragEnd = { settle() },
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
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
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
                            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Loading article" })
                        }
                    }
                    is ArticleUiState.Loaded -> items(s.paragraphs) { para ->
                        LensAnnotatedParagraph(
                            itemId = item.id,
                            text = para,
                            vm = lensVm,
                            style = MaterialTheme.typography.bodyLarge,
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
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Filled.Public, contentDescription = "Open in browser")
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
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clickable(onClickLabel = "Open the day loom") { onOpenLoom() },
                ) {
                    DayMixBar(todayMix, Modifier.fillMaxSize())
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
