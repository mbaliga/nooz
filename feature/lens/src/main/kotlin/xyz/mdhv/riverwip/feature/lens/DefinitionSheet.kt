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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.DictionaryFormatting

private sealed interface DefState {
    data object Looking : DefState
    data object NotFound : DefState
    data class Found(val senses: List<DictionaryFormatting.Sense>) : DefState
}

// A domain label at the very start of a sense, e.g. "(Com.)", "(Law)" — set
// in italics like the reference dictionary does for its field tags.
private val LEADING_DOMAIN_TAG = Regex("""^(\([A-Za-z.& ]+\))\s*""")

/**
 * The dictionary lens's definition sheet (owner's #9: "needs to look like an
 * actual dictionary" — the bundled Webster's 1913 text arrives as one flat,
 * unbroken run with no line breaks at all). [DictionaryFormatting] recovers
 * the entry's real structure — numbered senses, part-of-speech groups, "Syn.
 * --" cross-references — and this sheet lays each out the way a paper
 * dictionary would: a bold sense number, a paragraph break between parts of
 * speech, synonym blocks and domain tags in italics.
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
        state = if (definition.isNullOrBlank()) {
            DefState.NotFound
        } else {
            DefState.Found(DictionaryFormatting.parse(definition))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .padding(Tokens.Spacing.md)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            // Serif headword — the newspaper/reading voice (headlineSmall is serif by role).
            Text(word, style = MaterialTheme.typography.headlineSmall)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.xs, bottom = Tokens.Spacing.sm),
            )
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
                is DefState.Found -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                    for (sense in s.senses) {
                        SenseRow(sense)
                    }
                }
            }
        }
    }
}

/**
 * One sense (or synonym block), as one [Text] with a hanging indent — a
 * wrapped continuation line falls under the body text, not back under the
 * sense number, the way a printed dictionary sets it.
 */
@Composable
private fun SenseRow(sense: DictionaryFormatting.Sense) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onBackground
    val topSpace = if (sense.startsNewGroup) Tokens.Spacing.sm else 0.dp
    val hangingIndent = TextIndent(restLine = 22.sp)

    if (sense.isSynonymBlock) {
        Text(
            buildAnnotatedString {
                withStyle(ParagraphStyle(textIndent = hangingIndent)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)) { append("Syn. — ") }
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(sense.text) }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(top = topSpace),
        )
        return
    }

    val tagMatch = LEADING_DOMAIN_TAG.find(sense.text)
    val body = if (tagMatch != null) sense.text.substring(tagMatch.range.last + 1) else sense.text

    Text(
        buildAnnotatedString {
            withStyle(ParagraphStyle(textIndent = hangingIndent)) {
                if (sense.number != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${sense.number}. ") }
                }
                if (tagMatch != null) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = muted)) { append(tagMatch.groupValues[1]) }
                    append(' ')
                }
                append(body)
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = ink,
        modifier = Modifier.padding(top = topSpace),
    )
}
