package xyz.mdhv.riverwip.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.DisplayFontFamily
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.Provenance

/**
 * Nooz Flash (owner's #6): today's flowed headlines, compressed to 10 words or
 * fewer. Never fetched automatically — the reader taps for it, same "explicit
 * consent" contract every other generation in this app follows. "Go deeper"
 * doesn't call the model a second time; it just reveals the real headline list
 * the flash line was compressed from, so the honest source is always one tap
 * away, never a second, ungrounded generation.
 */
@Composable
fun FlashCard(vm: ReaderViewModel, modifier: Modifier = Modifier) {
    val state by vm.flashState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xs)) {
        when (val s = state) {
            is FlashUiState.Idle -> Row(
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Compress today's news") { vm.requestFlash() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeading("Nooz Flash", modifier = Modifier.weight(1f))
                Text(
                    "Compress today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is FlashUiState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Compressing today's news…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is FlashUiState.Ready -> {
                Text(
                    s.flash,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
                )
                Row(
                    modifier = Modifier.padding(top = Tokens.Spacing.xxs).clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                ) {
                    val provenanceLabel = if (s.provenance == Provenance.NATIVE) "on-device" else "cloud (your key)"
                    val provenanceColor = if (s.provenance == Provenance.NATIVE) Tokens.Color.provenanceNative else Tokens.Color.provenanceCloud
                    Text(provenanceLabel, style = MaterialTheme.typography.labelSmall, color = provenanceColor)
                    Text(
                        if (expanded) "Hide the headlines" else "Go deeper",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(top = Tokens.Spacing.xs)) {
                        for (headline in s.headlines) {
                            Text(
                                headline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Tokens.Spacing.xxs),
                            )
                        }
                    }
                }
            }
            is FlashUiState.Unavailable -> Text(
                "Nooz Flash: ${s.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = Tokens.Spacing.xs))
    }
}
