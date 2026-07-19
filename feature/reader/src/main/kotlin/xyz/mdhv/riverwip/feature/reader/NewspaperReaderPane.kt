package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.paperGrain
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.PaperGrain

/** Comfortable reading measure per column — narrower and it starts feeling like a phone column squeezed sideways. */
private val IDEAL_COLUMN_WIDTH = 340.dp
private val COLUMN_GUTTER = 32.dp
private const val MAX_COLUMNS = 3

/**
 * How many newspaper columns fit at this pane width, or null if it's not
 * wide enough for at least two — the "or a combination based on the space"
 * case, where [ReaderScreenTwoPane] falls back to the plain single-column
 * [ReaderDetailScreen] instead of this layout.
 */
fun columnsForWidth(paneWidth: Dp): Int? {
    val fit = ((paneWidth + COLUMN_GUTTER) / (IDEAL_COLUMN_WIDTH + COLUMN_GUTTER)).toInt().coerceAtMost(MAX_COLUMNS)
    return fit.takeIf { it >= 2 }
}

/**
 * The newspaper-column reading layout (owner: "the reading panel structured
 * into the columns of text as newspapers do, with the first becoming a
 * headline... above the fold, below the fold"). Only used once
 * [columnsForWidth] says the pane is wide enough — the masthead-style
 * headline sits full-width at the top, and the body is chunked into
 * fixed-height "pages" of [columns] side-by-side columns, filled in true
 * reading order (all of column 1 top-to-bottom, then column 2, ...) rather
 * than several columns scrolling in lockstep. Each page is one "fold" —
 * scrolling from one to the next is turning the page.
 *
 * Known gap: the interactive reading lens (tap-to-define, loaded-language
 * underlining) is [ReaderDetailScreen]'s own per-paragraph composable and
 * doesn't carry over here — it needs the exact character range of an
 * annotated span to survive being sliced across column/page boundaries,
 * which is a separate piece of work. The lens toggle still shows in the
 * utility bar for a consistent bar between layouts; it just has nothing to
 * do yet when the body is columned.
 */
@Composable
fun NewspaperReaderPane(
    vm: ReaderViewModel,
    item: Item,
    showReadingTime: Boolean,
    lensOn: Boolean,
    saved: Boolean,
    paperGrain: PaperGrain,
    columns: Int,
    onToggleLens: () -> Unit,
    onToggleClip: () -> Unit,
    onOpenLoom: () -> Unit,
) {
    val state by vm.articleState.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    val todayMix by vm.todayReadMix.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val topic = Classifier.dominantTopic(item.topics)
    val background = MaterialTheme.colorScheme.background

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(background)
            .paperGrain(paperGrain, MaterialTheme.colorScheme.onBackground),
    ) {
        // The true viewport, measured here — outside the scroll below, which
        // would otherwise report unbounded height. Pages budget off this, not
        // off the headline's actual (variable) height, so every page after
        // the first is a plain, predictable full page of columns; the first
        // is simply whatever's left under the headline once it's drawn.
        val pageHeight = (maxHeight - 120.dp).coerceAtLeast(200.dp)
        val columnsWidth = maxWidth - Tokens.Spacing.xl * 2
        val columnWidth = ((columnsWidth + COLUMN_GUTTER) / columns) - COLUMN_GUTTER

        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Tokens.Spacing.xl, vertical = Tokens.Spacing.xxl),
        ) {
            // The masthead-style headline (owner: "the first becoming a
            // headline") — full width, above the columns, with a rule
            // beneath it like a real front page.
            Text(topic.placeholderLabel, style = MaterialTheme.typography.labelLarge, color = topic.toComposeColor())
            Spacer(Modifier.height(Tokens.Spacing.xs))
            Text(item.title, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(Tokens.Spacing.sm))
            val author = item.author
            val sourceTitle = sourceTitles[item.sourceId]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    sourceTitle ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!author.isNullOrBlank()) {
                    Text(
                        author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Tokens.Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Tokens.Spacing.lg))

            when (val s = state) {
                is ArticleUiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(top = Tokens.Spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Loading article"
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                is ArticleUiState.Loaded -> NewspaperColumns(
                    paragraphs = s.paragraphs,
                    columns = columns,
                    columnWidth = columnWidth,
                    pageHeight = pageHeight,
                    bodyStyle = MaterialTheme.typography.bodyLarge,
                )
                is ArticleUiState.Fallback -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                    Text(
                        s.summary?.takeIf { it.isNotBlank() }
                            ?: "This source shares only a short summary in its feed.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Read the full story at the source ↗",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clickable {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.canonicalUrl)),
                                )
                            }
                            .padding(top = Tokens.Spacing.xs),
                    )
                }
            }
            Spacer(Modifier.height(Tokens.Spacing.lg))
            Text(
                "— end of article —",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(96.dp))
        }

        val readingProgress by remember {
            derivedStateOf {
                if (scrollState.maxValue == 0) 0f else (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
            }
        }
        ReaderUtilityBar(
            state = state,
            showReadingTime = showReadingTime,
            todayMix = todayMix,
            progress = readingProgress,
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
    }
}

/**
 * The body text, chunked into fixed-height "columns" in true reading order
 * (column 1 top-to-bottom, then column 2, ...) and grouped into pages of
 * [columns] each. Measures the whole body once at the column width to get
 * real line breaks (`TextMeasurer`), then slices by line index — never mid-
 * line — so each rendered slice re-wraps to exactly the lines it was cut at.
 */
@Composable
private fun NewspaperColumns(
    paragraphs: List<String>,
    columns: Int,
    columnWidth: Dp,
    pageHeight: Dp,
    bodyStyle: TextStyle,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // A blank line between paragraphs — simpler and safer to reason about
    // than reproducing true newsprint's indented, gapless paragraph marks,
    // and it re-wraps identically wherever a column slice lands inside it.
    val fullText = remember(paragraphs) { paragraphs.joinToString("\n\n") }

    val columnWidthPx = with(density) { columnWidth.roundToPx() }.coerceAtLeast(1)
    val pageHeightPx = with(density) { pageHeight.roundToPx() }.coerceAtLeast(1)

    val layout = remember(fullText, bodyStyle, columnWidthPx) {
        textMeasurer.measure(text = fullText, style = bodyStyle, constraints = Constraints(maxWidth = columnWidthPx))
    }

    val columnRanges = remember(layout, pageHeightPx) {
        buildList {
            var line = 0
            val total = layout.lineCount
            while (line < total) {
                val top = layout.getLineTop(line)
                var end = line
                while (end < total && layout.getLineBottom(end) - top <= pageHeightPx) end++
                if (end == line) end++ // always progress, even if a single line alone overflows the page
                add(layout.getLineStart(line) to layout.getLineEnd(end - 1))
                line = end
            }
        }
    }
    val pages = remember(columnRanges, columns) { columnRanges.chunked(columns) }

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxl)) {
        for (page in pages) {
            Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GUTTER)) {
                for ((start, end) in page) {
                    Text(
                        text = fullText.substring(start, end),
                        style = bodyStyle,
                        modifier = Modifier.width(columnWidth).height(pageHeight),
                    )
                }
            }
        }
    }
}
