package xyz.mdhv.riverwip.feature.lens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.model.AffectSpanDetector

private data class RenderedSpan(val span: AffectSpanDetector.Span, val renderedStart: Int, val renderedEnd: Int)

/**
 * A paragraph with lens spans woven in (brief §P5): detected loaded language is
 * pre-underlined; accepted rewrites show in place with a provenance dot. Tap
 * any marked span to open its defuse sheet.
 *
 * NOTE: uses a solid underline as the pre-underline treatment — the brief's
 * "subtle dotted underline" would need a custom draw pass keyed to the actual
 * line-broken glyph positions (`TextLayoutResult`); left as a follow-up visual
 * refinement (see STATE.md), since it does not change the underlying
 * detect/accept/dismiss/revert behavior, which is fully wired here.
 */
@Composable
fun LensAnnotatedParagraph(
    itemId: String,
    text: String,
    vm: LensViewModel,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val spans = remember(text) { vm.detect(text) }

    if (!vm.underlinesEnabled || spans.isEmpty()) {
        Text(text = text, style = style, modifier = modifier)
        return
    }

    // Global sterile-lens toggle: auto-request a rewrite for every untouched span.
    LaunchedEffect(itemId, text, vm.sterileLensEnabled) {
        if (vm.sterileLensEnabled) vm.defuseAll(itemId, text)
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selectedSpan by remember { mutableStateOf<AffectSpanDetector.Span?>(null) }

    val renderedSpans = remember(text, spans) { mutableListOf<RenderedSpan>() }
    // Re-derive on every recomposition triggered by a state change so accepted
    // rewrite text (which can change length) keeps the tap-mapping correct.
    val spanStates = spans.map { vm.stateFor(itemId, it) }
    val annotated = remember(text, spans, spanStates) {
        renderedSpans.clear()
        buildAnnotatedString {
            var cursor = 0
            for (span in spans) {
                if (span.start > cursor) append(text.substring(cursor, span.start))
                val state = vm.stateFor(itemId, span)
                val renderedStart = length
                when (state) {
                    is AffectSpanUiState.Accepted -> {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(state.rewrittenText)
                        }
                        val dotColor = if (state.provenance == Provenance.NATIVE) Tokens.Color.provenanceNative else Tokens.Color.provenanceCloud
                        withStyle(SpanStyle(color = dotColor)) { append(" •") }
                    }
                    is AffectSpanUiState.Dismissed, is AffectSpanUiState.Rejected -> {
                        append(text.substring(span.start, span.end))
                    }
                    else -> {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(text.substring(span.start, span.end))
                        }
                    }
                }
                renderedSpans.add(RenderedSpan(span, renderedStart, length))
                cursor = span.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    Text(
        text = annotated,
        style = style,
        modifier = modifier
            .pointerInput(renderedSpans.size) {
                detectTapGestures { pos ->
                    val lr = layoutResult ?: return@detectTapGestures
                    val charOffset = lr.getOffsetForPosition(pos)
                    selectedSpan = renderedSpans.firstOrNull { charOffset in it.renderedStart until it.renderedEnd }?.span
                }
            }
            // TalkBack's "explore by touch" never lands a raw tap at the exact
            // pixel a span occupies, so the gesture above is unreachable to it —
            // this is the actual way in (brief §P7: "TalkBack labels including
            // lens spans"). One custom action per detected span, surfaced in
            // TalkBack's local context menu regardless of where the span sits.
            .semantics {
                customActions = renderedSpans.map { rendered ->
                    CustomAccessibilityAction(label = "${rendered.span.evidence}. View suggestion.") {
                        selectedSpan = rendered.span
                        true
                    }
                }
            },
        onTextLayout = { layoutResult = it },
    )

    val current = selectedSpan
    if (current != null) {
        DefuseBottomSheet(
            itemId = itemId,
            span = current,
            originalText = text.substring(current.start, current.end),
            fullSentence = text,
            vm = vm,
            onDismissRequest = { selectedSpan = null },
        )
    }
}
