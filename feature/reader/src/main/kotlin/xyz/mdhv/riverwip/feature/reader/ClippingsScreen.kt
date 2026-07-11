package xyz.mdhv.riverwip.feature.reader

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.Clipping
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ClippingsViewModel(private val repo: ClippingRepository) : ViewModel() {
    val clippings: StateFlow<List<Clipping>> =
        repo.observeClippings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun remove(itemId: String) = viewModelScope.launch { repo.remove(itemId) }

    class Factory(private val repo: ClippingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ClippingsViewModel(repo) as T
    }
}

private val CLIP_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The Clippings shelf (owner's Clippings section): the articles the reader kept,
 * each shown as a Nooz-paper clipping — source masthead, serif headline, byline
 * and the date it was clipped. Tap to reopen in the browser; the bookmark
 * removes it, and share re-issues the newspaper clipping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClippingsScreen(vm: ClippingsViewModel, onBack: () -> Unit) {
    val clippings by vm.clippings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CLIPPINGS", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (clippings.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Tokens.Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No clippings yet.\nTap the bookmark on any article to keep it here.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Tokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                items(clippings, key = { it.itemId }) { clip ->
                    ClippingCard(
                        clip = clip,
                        onOpen = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(clip.url)))
                        },
                        onShare = {
                            NewspaperShare.share(context, clip.title, clip.sourceTitle, clip.author, clip.url)
                        },
                        onRemove = { vm.remove(clip.itemId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClippingCard(
    clip: Clipping,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(Tokens.Border.thin, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Tokens.Radius.sm))
            .clickable(onClick = onOpen)
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        // Source masthead + topic, tracked small caps — never colour alone.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (clip.sourceTitle ?: "Nooz").uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                clip.topic.placeholderLabel,
                style = MaterialTheme.typography.labelMedium,
                color = clip.topic.toComposeColor(),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(clip.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                byline(clip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Share, contentDescription = "Share as a newspaper clipping", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Remove clipping", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun byline(clip: Clipping): String {
    val saved = CLIP_DATE.format(Instant.ofEpochMilli(clip.savedAt).atZone(ZoneId.systemDefault()))
    val who = listOfNotNull(clip.author?.takeIf { it.isNotBlank() }, clip.sourceTitle).joinToString(" | ")
    return if (who.isEmpty()) "Clipped $saved" else "$who · clipped $saved"
}
