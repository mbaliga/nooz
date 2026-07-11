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
import androidx.compose.ui.graphics.Color
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
import xyz.mdhv.riverwip.model.ObscureWords

// The owner's colour scheme: red for loaded/strong language, blue for obscure
// words worth a definition. Mid-tones legible on paper, white, and charcoal;
// red-vs-blue is colour-vision-deficiency safe (the CVD hazard is red/green).
private val LoadedColor = Color(0xFFD1503F)
private val ObscureColor = Color(0xFF3E7BE0)

/** A lens mark to render: either a loaded-language span (red) or an obscure word (blue). */
private sealed interface Mark {
    val start: Int
    val end: Int

    data class Affect(val span: AffectSpanDetector.Span) : Mark {
        override val start get() = span.start
        override val end get() = span.end
    }

    data class Obscure(val ws: ObscureWords.WordSpan) : Mark {
        override val start get() = ws.start
        override val end get() = ws.end
    }
}

private data class RenderedMark(val mark: Mark, val renderedStart: Int, val renderedEnd: Int)

/**
 * A paragraph with the lens woven in (brief §P5 + owner's dictionary lens):
 * loaded language is underlined red, obscure words blue. Tap a red mark for its
 * defuse sheet; tap a blue word for its definition. Loaded-language marks are
 * gated by the "Highlight loaded language" setting; obscure marks appear once a
 * dictionary is downloaded. Affect marks take priority where the two overlap.
 *
 * NOTE: solid underline (a subtle dotted treatment needs a custom draw pass
 * keyed to `TextLayoutResult`); logged as a visual follow-up in STATE.md.
 */
@Composable
fun LensAnnotatedParagraph(
    itemId: String,
    text: String,
    vm: LensViewModel,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val affect = if (vm.underlinesEnabled) remember(text) { vm.detect(text) } else emptyList()
    val obscure = remember(text, vm.obscureActive) {
        if (vm.obscureActive) vm.detectObscure(text) else emptyList()
    }

    if (affect.isEmpty() && obscure.isEmpty()) {
        Text(text = text, style = style, modifier = modifier)
        return
    }

    // Merge: affect first (priority), then obscure words that don't overlap one.
    val marks = remember(affect, obscure) {
        val list = ArrayList<Mark>(affect.size + obscure.size)
        affect.forEach { list.add(Mark.Affect(it)) }
        obscure.forEach { ob ->
            if (affect.none { ob.start < it.end && it.start < ob.end }) list.add(Mark.Obscure(ob))
        }
        list.sortBy { it.start }
        list
    }

    // Sterile-lens still applies to loaded language only.
    LaunchedEffect(itemId, text, vm.sterileLensEnabled) {
        if (vm.sterileLensEnabled) vm.defuseAll(itemId, text)
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selectedSpan by remember { mutableStateOf<AffectSpanDetector.Span?>(null) }
    var selectedWord by remember { mutableStateOf<String?>(null) }

    val rendered = remember(text, marks) { mutableListOf<RenderedMark>() }
    // Re-derive when any affect span's state changes (an accepted rewrite can
    // change length, so the tap-mapping must move with it).
    val affectStates = marks.mapNotNull { (it as? Mark.Affect)?.let { m -> vm.stateFor(itemId, m.span) } }
    val annotated = remember(text, marks, affectStates) {
        rendered.clear()
        buildAnnotatedString {
            var cursor = 0
            for (mark in marks) {
                if (mark.start > cursor) append(text.substring(cursor, mark.start))
                val renderedStart = length
                when (mark) {
                    is Mark.Obscure -> {
                        withStyle(SpanStyle(color = ObscureColor, textDecoration = TextDecoration.Underline)) {
                            append(text.substring(mark.start, mark.end))
                        }
                    }
                    is Mark.Affect -> when (val state = vm.stateFor(itemId, mark.span)) {
                        is AffectSpanUiState.Accepted -> {
                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                append(state.rewrittenText)
                            }
                            val dot = if (state.provenance == Provenance.NATIVE) Tokens.Color.provenanceNative else Tokens.Color.provenanceCloud
                            withStyle(SpanStyle(color = dot)) { append(" •") }
                        }
                        is AffectSpanUiState.Dismissed, is AffectSpanUiState.Rejected ->
                            append(text.substring(mark.start, mark.end))
                        else -> withStyle(SpanStyle(color = LoadedColor, textDecoration = TextDecoration.Underline)) {
                            append(text.substring(mark.start, mark.end))
                        }
                    }
                }
                rendered.add(RenderedMark(mark, renderedStart, length))
                cursor = mark.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    Text(
        text = annotated,
        style = style,
        modifier = modifier
            .pointerInput(rendered.size) {
                detectTapGestures { pos ->
                    val lr = layoutResult ?: return@detectTapGestures
                    val charOffset = lr.getOffsetForPosition(pos)
                    val hit = rendered.firstOrNull { charOffset in it.renderedStart until it.renderedEnd }?.mark
                    when (hit) {
                        is Mark.Affect -> selectedSpan = hit.span
                        is Mark.Obscure -> selectedWord = hit.ws.word
                        null -> {}
                    }
                }
            }
            // TalkBack can't land a raw tap on an exact span; one custom action
            // per mark is the actual way in (brief §P7). Indexed so repeats stay
            // distinguishable.
            .semantics {
                customActions = rendered.mapIndexed { index, r ->
                    val (label, action) = when (val mark = r.mark) {
                        is Mark.Obscure -> "Define “${mark.ws.word}” (${index + 1} of ${rendered.size})" to {
                            selectedWord = mark.ws.word; true
                        }
                        is Mark.Affect -> {
                            val verb = when (vm.stateFor(itemId, mark.span)) {
                                is AffectSpanUiState.Accepted -> "Revert suggestion"
                                is AffectSpanUiState.Rejected -> "Rewrite unavailable"
                                is AffectSpanUiState.Loading -> "Suggestion loading"
                                else -> "View suggestion"
                            }
                            "${mark.span.evidence} (${index + 1} of ${rendered.size}). $verb." to {
                                selectedSpan = mark.span; true
                            }
                        }
                    }
                    CustomAccessibilityAction(label = label, action = action)
                }
            },
        onTextLayout = { layoutResult = it },
    )

    val currentSpan = selectedSpan
    if (currentSpan != null) {
        DefuseBottomSheet(
            itemId = itemId,
            span = currentSpan,
            originalText = text.substring(currentSpan.start, currentSpan.end),
            fullSentence = text,
            vm = vm,
            onDismissRequest = { selectedSpan = null },
        )
    }

    val currentWord = selectedWord
    if (currentWord != null) {
        DefinitionSheet(
            word = currentWord,
            vm = vm,
            onDismissRequest = { selectedWord = null },
        )
    }
}
