package xyz.mdhv.riverwip.feature.lens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.Tokens

private sealed interface DefState {
    data object Looking : DefState
    data object NotFound : DefState
    data class Found(val text: String) : DefState
}

/**
 * The dictionary lens's definition sheet (owner's Kindle-style lookup):
 * long-press any word → its meaning, right here. Loads from the downloaded
 * dictionary off the main thread; an honest "no definition" if the word isn't
 * in it. Nothing about the lookup is stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefinitionSheet(
    word: String,
    vm: LensViewModel,
    onDismissRequest: () -> Unit,
) {
    var state by remember(word) { mutableStateOf<DefState>(DefState.Looking) }
    LaunchedEffect(word) {
        val definition = vm.define(word)?.trim()?.replace(Regex("\\s+"), " ")
        state = if (definition.isNullOrBlank()) DefState.NotFound else DefState.Found(definition)
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .padding(Tokens.Spacing.md)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            // Serif headword — the newspaper/reading voice (headlineSmall is serif by role).
            Text(word, style = MaterialTheme.typography.headlineSmall)
            when (val s = state) {
                DefState.Looking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Looking up…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DefState.NotFound -> Text(
                    "No definition for this word in your dictionary.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is DefState.Found -> Text(s.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
