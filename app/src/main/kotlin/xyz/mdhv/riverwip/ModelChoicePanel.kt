package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.byok.ByokConfig
import xyz.mdhv.riverwip.inference.local.ModelCatalog
import xyz.mdhv.riverwip.inference.local.StorageBudget

/** Which of the lens's three intelligence paths the reader is looking at. */
enum class ModelPath { ON_DEVICE, DOWNLOAD, BYOK }

/**
 * The one honest "how should the lens think" chooser (owner's #18; a
 * cross-repo consumer of Nooz's catalogue flagged the same gap: no verified
 * model download URLs or checksums exist yet, so a one-tap "download" button
 * would be fake or broken). Three real paths, shown the same way wherever this
 * question comes up — onboarding's Advanced step and Settings' Reader
 * intelligence share this exact panel, so a reader who skips it in onboarding
 * finds the identical doors later rather than a diminished version.
 *
 * - **On-device**: the default, nothing to configure — stated plainly that
 *   this build has no local model runtime wired in yet.
 * - **Download a model**: lists the catalogue's named models with their size,
 *   but honestly, since no live/checksummed mirror is verified for either —
 *   informational, not a button that pretends to work.
 * - **Bring your own key**: the one path that actually runs today.
 */
@Composable
fun ModelChoicePanel(
    path: ModelPath,
    onPathChange: (ModelPath) -> Unit,
    byokConfig: ByokConfig,
    onSaveByok: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onClearByok: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
            PathChip("On-device", path == ModelPath.ON_DEVICE) { onPathChange(ModelPath.ON_DEVICE) }
            PathChip("Download a model", path == ModelPath.DOWNLOAD) { onPathChange(ModelPath.DOWNLOAD) }
            PathChip("Bring your own key", path == ModelPath.BYOK) { onPathChange(ModelPath.BYOK) }
        }
        when (path) {
            ModelPath.ON_DEVICE -> OnDeviceStanza()
            ModelPath.DOWNLOAD -> DownloadStanza()
            ModelPath.BYOK -> ByokStanza(byokConfig, onSaveByok, onClearByok)
        }
    }
}

@Composable
private fun PathChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun OnDeviceStanza() {
    Text(
        "Nooz tries your device first, automatically — nothing to set up. This build doesn't yet include a local model runtime, so until one lands (or you add a key below), the lens honestly reports \"unavailable\" instead of guessing.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DownloadStanza() {
    Text(
        "Nooz hasn't verified a live, checksummed download source for either model yet — so instead of a one-tap button that might fail or fetch the wrong file, here's the honest state:",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    for (spec in ModelCatalog.all) {
        Column(Modifier.padding(top = Tokens.Spacing.xs)) {
            Text(spec.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${StorageBudget.humanReadable(spec.approxSizeBytes)} · not available in this build",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ByokStanza(
    config: ByokConfig,
    onSave: (String, String, String) -> Unit,
    onClear: () -> Unit,
) {
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    Text(
        "Route defuse rewrites to your own OpenAI-compatible endpoint. Results are always marked cloud; your key stays on this device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModelChoiceField("Base URL", baseUrl, "https://api.openai.com/v1") { baseUrl = it }
    ModelChoiceField("API key", apiKey, "sk-…", masked = true) { apiKey = it }
    ModelChoiceField("Model", model, "gpt-4o-mini") { model = it }
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
        TextButton(
            onClick = { onSave(baseUrl, apiKey, model) },
            enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(),
            contentPadding = PaddingValues(0.dp),
        ) { Text("Save key") }
        if (config.isComplete) {
            TextButton(onClick = onClear, contentPadding = PaddingValues(0.dp)) { Text("Remove key") }
        }
    }
}

@Composable
private fun ModelChoiceField(
    label: String,
    value: String,
    placeholder: String,
    masked: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = Tokens.Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xxs),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                inner()
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
