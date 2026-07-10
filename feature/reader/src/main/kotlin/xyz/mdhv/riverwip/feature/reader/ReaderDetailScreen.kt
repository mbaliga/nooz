package xyz.mdhv.riverwip.feature.reader

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.feature.lens.LensAnnotatedParagraph
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.Item
import kotlin.math.ceil

/** Static estimate from content length (~200 wpm). Display-only — nothing is measured or stored (brief §3). */
private fun readingMinutes(paragraphs: List<String>): Int {
    val words = paragraphs.sumOf { it.split(Regex("\\s+")).count { w -> w.isNotBlank() } }
    return ceil(words / 200.0).toInt().coerceAtLeast(1)
}

/**
 * The paper (owner's Paper mock, 2026-07): a big serif headline over a
 * "Source | Author" byline, quiet body text, and a bottom utility bar —
 * share / open-in-browser on the left, an estimated reading time on the right
 * (Settings-toggleable). The lens stays woven into every paragraph (brief §P5);
 * the mock's struck-through "Show Progress" is deliberately absent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderDetailScreen(
    vm: ReaderViewModel,
    lensVm: LensViewModel,
    item: Item,
    showReadingTime: Boolean,
    onBack: () -> Unit,
) {
    val state by vm.articleState.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val topic = Classifier.dominantTopic(item.topics)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topic.placeholderLabel, style = MaterialTheme.typography.labelLarge, color = topic.toComposeColor()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                    }
                },
                actions = {
                    // Instantly-discoverable lens toggle (brief §P5): default ON, one tap OFF.
                    IconButton(onClick = { lensVm.updateUnderlinesEnabled(!lensVm.underlinesEnabled) }) {
                        Icon(
                            if (lensVm.underlinesEnabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (lensVm.underlinesEnabled) "Hide flagged language" else "Show flagged language",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.Spacing.xs, vertical = Tokens.Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.canonicalUrl}")
                            }.let { Intent.createChooser(it, null) },
                        )
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.canonicalUrl)))
                    }) {
                        Icon(Icons.Filled.Public, contentDescription = "Open in browser")
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        val s = state
                        if (showReadingTime && s is ArticleUiState.Loaded) {
                            val minutes = remember(s.paragraphs) { readingMinutes(s.paragraphs) }
                            Text(
                                "$minutes min",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = Tokens.Spacing.sm)
                                    .semantics { contentDescription = "Estimated reading time $minutes minutes" },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                    // The Paper mock's voice: an oversized serif headline...
                    Text(item.title, style = MaterialTheme.typography.displayLarge)
                    // ...over a source | author byline row.
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    }
}
