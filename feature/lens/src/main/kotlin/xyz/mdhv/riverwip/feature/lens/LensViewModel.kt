package xyz.mdhv.riverwip.feature.lens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.inference.InferenceRouter
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult
import xyz.mdhv.riverwip.model.AffectSpanDetector
import xyz.mdhv.riverwip.model.FidelityGuard

/** One detected affect span's defuse state (brief §P5). */
sealed interface AffectSpanUiState {
    data object Untouched : AffectSpanUiState
    data object Loading : AffectSpanUiState
    data class Accepted(val rewrittenText: String, val provenance: Provenance) : AffectSpanUiState
    data object Dismissed : AffectSpanUiState
    /** The guard rejected the rewrite, or the provider failed/was unavailable — reason always shown (brief §3). */
    data class Rejected(val reason: String) : AffectSpanUiState
}

/**
 * The lens (brief §P5). All state here is **session-ephemeral**: it survives
 * rotation (a ViewModel does, by construction) but never an app restart, and is
 * never persisted or aggregated — what a user chooses to defuse is itself a
 * bias trace, and this app does not collect it. There is deliberately no
 * Room/DataStore dependency anywhere in this class.
 */
class LensViewModel(private val inferenceRouter: InferenceRouter) : ViewModel() {

    data class SpanKey(val itemId: String, val start: Int, val end: Int)

    private val overrides: SnapshotStateMap<SpanKey, AffectSpanUiState> = mutableStateMapOf()

    /** Reader-chrome toggle: pre-underline detected spans. Default ON, instantly discoverable OFF (brief §P5). */
    var underlinesEnabled: Boolean by mutableStateOf(true)
        private set

    /** Global "sterile lens": batch-applies the same per-span pipeline to every span. Off by default. */
    var sterileLensEnabled: Boolean by mutableStateOf(false)
        private set

    fun setUnderlinesEnabled(enabled: Boolean) {
        underlinesEnabled = enabled
    }

    fun setSterileLensEnabled(enabled: Boolean) {
        sterileLensEnabled = enabled
    }

    fun detect(text: String): List<AffectSpanDetector.Span> = AffectSpanDetector.detect(text)

    fun stateFor(itemId: String, span: AffectSpanDetector.Span): AffectSpanUiState =
        overrides[SpanKey(itemId, span.start, span.end)] ?: AffectSpanUiState.Untouched

    /**
     * Ask the router for a neutral rewrite, then run the deterministic fidelity
     * guard against the *original full sentence* (not just the span) — a
     * fabricated number/entity/negation flip can appear anywhere in the output.
     */
    fun requestDefuse(itemId: String, span: AffectSpanDetector.Span, fullSentence: String) {
        val key = SpanKey(itemId, span.start, span.end)
        overrides[key] = AffectSpanUiState.Loading
        viewModelScope.launch {
            val result = inferenceRouter.rewrite(RewriteRequest(fullSentence, span.text, span.start, span.end))
            overrides[key] = when (result) {
                is RewriteResult.Success -> {
                    val verdict = FidelityGuard.check(fullSentence, result.rewrittenSentence)
                    if (verdict.accepted) {
                        AffectSpanUiState.Accepted(result.rewrittenSentence, result.provenance)
                    } else {
                        AffectSpanUiState.Rejected(verdict.reason)
                    }
                }
                is RewriteResult.Failed -> AffectSpanUiState.Rejected(result.reason)
            }
        }
    }

    /** Batch-apply the same pipeline to every untouched span in [text] (the global sterile-lens toggle). */
    fun defuseAll(itemId: String, text: String) {
        for (span in detect(text)) {
            if (stateFor(itemId, span) is AffectSpanUiState.Untouched) {
                requestDefuse(itemId, span, text)
            }
        }
    }

    fun dismiss(itemId: String, span: AffectSpanDetector.Span) {
        overrides[SpanKey(itemId, span.start, span.end)] = AffectSpanUiState.Dismissed
    }

    /** Individually revertible (brief §P5): the original is always one tap away. */
    fun revert(itemId: String, span: AffectSpanDetector.Span) {
        overrides.remove(SpanKey(itemId, span.start, span.end))
    }

    /** Globally revertible: undo every accepted rewrite for [itemId] at once. */
    fun revertAllFor(itemId: String) {
        overrides.keys.filter { it.itemId == itemId }.forEach { overrides.remove(it) }
    }

    class Factory(private val inferenceRouter: InferenceRouter) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LensViewModel(inferenceRouter) as T
    }
}
