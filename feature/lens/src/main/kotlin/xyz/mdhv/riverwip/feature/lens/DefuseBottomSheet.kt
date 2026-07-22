package xyz.mdhv.riverwip.feature.lens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.model.AffectSpanDetector

/**
 * Tap a span → this sheet (brief §P5): original text, the evidence, a proposed
 * neutral rewrite (with provenance), accept/dismiss. Detection is framed as
 * opinion, never verdict (brief §7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefuseBottomSheet(
    itemId: String,
    span: AffectSpanDetector.Span,
    originalText: String,
    fullSentence: String,
    vm: LensViewModel,
    onDismissRequest: () -> Unit,
) {
    val state = vm.stateFor(itemId, span)

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            // The rewrite state below changes without any user action of its own
            // (Loading -> Accepted/Rejected) — a live region announces that
            // transition to TalkBack instead of leaving it silent.
            modifier = Modifier.padding(Tokens.Spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            Text("Original", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(originalText, style = MaterialTheme.typography.bodyLarge)
            Text(span.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Copy.DETECTION_IS_OPINION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state) {
                is AffectSpanUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = Tokens.Spacing.sm)
                            .semantics { contentDescription = "Loading suggested rewrite" },
                    )
                }
                is AffectSpanUiState.Accepted -> {
                    Text(
                        "Proposed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.Spacing.xs),
                    )
                    Text(state.rewrittenText, style = MaterialTheme.typography.bodyLarge)
                    val provenanceLabel = if (state.provenance == Provenance.NATIVE) "on-device" else "cloud (via Urbana)"
                    val provenanceColor = if (state.provenance == Provenance.NATIVE) Tokens.Color.provenanceNative else Tokens.Color.provenanceCloud
                    Text(provenanceLabel, style = MaterialTheme.typography.labelSmall, color = provenanceColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                        TextButton(onClick = { vm.revert(itemId, span); onDismissRequest() }) { Text("Revert") }
                    }
                }
                is AffectSpanUiState.Rejected -> {
                    // Not an error the reader caused — a capability that isn't
                    // set up yet. Muted, not alarming red (owner).
                    Text(
                        "No neutral rewrite yet. This needs an on-device model or your own key, from Settings › Reader intelligence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.Spacing.xs),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                        TextButton(onClick = onDismissRequest) { Text("Close") }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                        Button(onClick = { vm.requestDefuse(itemId, span, fullSentence) }) { Text("Suggest a neutral rewrite") }
                        TextButton(onClick = { vm.dismiss(itemId, span); onDismissRequest() }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
