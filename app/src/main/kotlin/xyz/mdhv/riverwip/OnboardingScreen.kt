package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.CandyCaneBar
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.Tokens

private enum class OnbStep { WELCOME, ADVANCED }

/**
 * First-run onboarding (owner's #19): the reader lands on the paper already
 * warming up — a candy-cane bar standing in for stories loading — and is
 * offered two doors. **Quick setup** takes the honest defaults (on-device
 * first, controls shown) and drops straight into the Stand. **Advanced** opens
 * a short wizard: bring your own key, and choose immersive vs. controls-shown.
 * A **Skip** is always present — onboarding is never a wall.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onSaveByok: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onSetImmersive: (Boolean) -> Unit,
) {
    var step by remember { mutableStateOf(OnbStep.WELCOME) }

    Box(Modifier.fillMaxSize()) {
        // The "articles loading" backdrop: the wordmark and a live candy-cane bar.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xl),
            horizontalAlignment = Alignment.Start,
        ) {
            NoozWordmark(fontSize = 30.sp)
            Spacer(Modifier.height(Tokens.Spacing.md))
            CandyCaneBar()
            Spacer(Modifier.height(Tokens.Spacing.xs))
            Text(
                "Gathering today's stories…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The setup card, docked to the bottom half.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
            when (step) {
                OnbStep.WELCOME -> WelcomeStep(
                    onQuick = onFinish,
                    onAdvanced = { step = OnbStep.ADVANCED },
                    onSkip = onFinish,
                )
                OnbStep.ADVANCED -> AdvancedStep(
                    onBack = { step = OnbStep.WELCOME },
                    onFinish = onFinish,
                    onSaveByok = onSaveByok,
                    onSetImmersive = onSetImmersive,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onQuick: () -> Unit, onAdvanced: () -> Unit, onSkip: () -> Unit) {
    Text(
        "Read what's there — and notice what isn't.",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        "Nooz is a quiet news reader. The lens can flag loaded language and, with a dictionary, define any word on a long-press. Everything runs on your device by default.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onQuick, modifier = Modifier.fillMaxWidth()) {
        Text("Quick setup")
    }
    OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
        Text("Advanced setup")
    }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text("Skip")
    }
}

@Composable
private fun AdvancedStep(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onSaveByok: (String, String, String) -> Unit,
    onSetImmersive: (Boolean) -> Unit,
) {
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var immersive by remember { mutableStateOf(false) }

    Text("Advanced setup", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)

    Text(
        "Bring your own key (optional)",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Route defuse rewrites to your own OpenAI-compatible endpoint. Cloud results are always marked cloud; your key stays on the device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OnbField("Base URL", baseUrl, "https://api.openai.com/v1") { baseUrl = it }
    OnbField("API key", apiKey, "sk-…", masked = true) { apiKey = it }
    OnbField("Model", model, "gpt-4o-mini") { model = it }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Reading mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        ModeChip("Controls shown", !immersive) { immersive = false }
        ModeChip("Immersive", immersive) { immersive = true }
    }
    Text(
        "Controls shown keeps an obvious back button and the utility bar. Immersive hides them for a bare page — swipe right to go back. You can change this any time in Settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = {
            if (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
                onSaveByok(baseUrl, apiKey, model)
            }
            onSetImmersive(immersive)
            onFinish()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Done") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back") }
        TextButton(onClick = onFinish) { Text("Skip") }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun OnbField(
    label: String,
    value: String,
    placeholder: String,
    masked: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
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
