package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.AppSettings
import xyz.mdhv.riverwip.model.ReaderAlign
import xyz.mdhv.riverwip.model.ReaderTransition
import xyz.mdhv.riverwip.model.ReaderTypeBounds
import xyz.mdhv.riverwip.design.R as DesignR
import kotlin.math.roundToInt

/**
 * Reading settings: a live preview, the horizontal size and weight sliders,
 * ligatures, alignment, page-turn vs continuous, rotation lock and offline.
 * The preview is set in the app's own body style, so every control here moves
 * it as it is dragged. Sliders keep their value locally while dragging and
 * persist only when released, so DataStore isn't written on every pixel.
 */
@Composable
internal fun ReadingSection(settings: AppSettings, vm: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Tokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                Text(
                    stringResource(DesignR.string.reading_preview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(DesignR.string.reading_preview_text), style = MaterialTheme.typography.bodyLarge)
            }
        }

        SectionHeading(stringResource(DesignR.string.reading_typography))

        // Font size — continuous, horizontal.
        var scale by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }
        val sizePercent = (scale * 100).roundToInt()
        SliderRow(
            label = stringResource(DesignR.string.reading_font_size),
            value = stringResource(DesignR.string.reading_font_size_value, sizePercent),
        ) {
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { vm.setFontScale((scale * 20).roundToInt() / 20f) },
                valueRange = ReaderTypeBounds.MIN_SCALE..ReaderTypeBounds.MAX_SCALE,
            )
        }

        // Font weight — 400..700 in steps of 100.
        var weight by remember(settings.fontWeight) { mutableFloatStateOf(settings.fontWeight.toFloat()) }
        SliderRow(
            label = stringResource(DesignR.string.reading_font_weight),
            value = weight.roundToInt().toString(),
        ) {
            Slider(
                value = weight,
                onValueChange = { weight = it },
                onValueChangeFinished = { vm.setFontWeight(weight.roundToInt()) },
                valueRange = ReaderTypeBounds.MIN_WEIGHT.toFloat()..ReaderTypeBounds.MAX_WEIGHT.toFloat(),
                steps = (ReaderTypeBounds.MAX_WEIGHT - ReaderTypeBounds.MIN_WEIGHT) / ReaderTypeBounds.WEIGHT_STEP - 1,
            )
        }

        SettingSwitchRow(
            title = stringResource(DesignR.string.reading_ligatures),
            subtitle = stringResource(DesignR.string.reading_ligatures_body),
            checked = settings.ligatures,
            onCheckedChange = vm::setLigatures,
        )

        SectionHeading(stringResource(DesignR.string.reading_alignment))
        val alignLabels = listOf(
            ReaderAlign.DEFAULT to stringResource(DesignR.string.reading_align_default),
            ReaderAlign.JUSTIFY to stringResource(DesignR.string.reading_align_justify),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            alignLabels.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = settings.textAlign == value,
                    onClick = { vm.setTextAlign(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, alignLabels.size),
                ) { Text(label) }
            }
        }

        SectionHeading(stringResource(DesignR.string.reading_transitions))
        val transitions = listOf(
            ReaderTransition.CONTINUOUS to stringResource(DesignR.string.reading_transition_continuous),
            ReaderTransition.PAGE to stringResource(DesignR.string.reading_transition_page),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            transitions.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = settings.readerTransition == value,
                    onClick = { vm.setReaderTransition(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, transitions.size),
                ) { Text(label) }
            }
        }
        Text(
            stringResource(
                if (settings.readerTransition == ReaderTransition.PAGE) {
                    DesignR.string.reading_transitions_body_page
                } else {
                    DesignR.string.reading_transitions_body_continuous
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingSwitchRow(
            title = stringResource(DesignR.string.reading_rotation_lock),
            subtitle = stringResource(DesignR.string.reading_rotation_lock_body),
            checked = settings.rotationLock,
            onCheckedChange = vm::setRotationLock,
        )
        SettingSwitchRow(
            title = stringResource(DesignR.string.reading_offline),
            subtitle = stringResource(DesignR.string.reading_offline_body),
            checked = settings.offlineMode,
            onCheckedChange = vm::setOfflineMode,
        )
    }
}

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    Column(Modifier.semantics(mergeDescendants = true) { stateDescription = value }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        slider()
    }
}
