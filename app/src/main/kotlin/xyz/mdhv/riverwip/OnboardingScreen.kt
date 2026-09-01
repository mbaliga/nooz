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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.CandyCaneBar
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.byok.ByokConfig

private enum class OnbStep { WELCOME, ADVANCED, TOUR }

/**
 * First-run onboarding (owner's #19): the reader lands on the paper already
 * warming up — a candy-cane bar standing in for stories loading — and is
 * offered two doors. **Quick setup** takes the honest defaults (on-device
 * first, controls shown), adds a small starter set of verified global
 * outlets so the Stand isn't empty. **Advanced** opens a short wizard: the
 * same three-path [ModelChoicePanel] Settings' Reader intelligence uses
 * (on-device / download a model / bring your own key — a cross-repo consumer
 * of Nooz's catalogue flagged the fake-button risk this avoids), and a choice
 * of immersive vs. controls-shown.
 *
 * Both doors then land on [FeatureTourContent] as a send-off (D36), because
 * Cast, Flash and the Loom were going undiscovered: the Loom opens by pulling
 * down on the stand, and the other two ship deliberately off. The tour comes
 * *last* rather than first on purpose — the names mean something once the app
 * is set up and there are stories behind it, and putting it up front would
 * have taxed every reader before they had any reason to care.
 *
 * A **Skip** is always present, and skips the tour too — onboarding is never a
 * wall, and a reader who says "skip" has been clear. Settings keeps the same
 * tour permanently for anyone who skipped, or who onboarded before it existed.
 */
@Composable
fun OnboardingScreen(
    byokConfig: ByokConfig,
    download: ModelDownloadUi,
    onFinish: () -> Unit,
    onQuickSetup: () -> Unit,
    onSaveByok: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onClearByok: () -> Unit,
    onSetImmersive: (Boolean) -> Unit,
) {
    // rememberSaveable, not remember: rotating the phone mid-setup used to drop
    // the reader back on the welcome step, losing whichever door they'd picked.
    var step by rememberSaveable { mutableStateOf(OnbStep.WELCOME) }

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
                    // Quick setup used to just skip Advanced and drop the reader
                    // on an empty Stand — indistinguishable from Skip (owner: "the
                    // quick setup isn't doing any setup at all"). It now actually
                    // adds a small starter set, then hands off to the tour.
                    onQuick = { onQuickSetup(); step = OnbStep.TOUR },
                    onAdvanced = { step = OnbStep.ADVANCED },
                    onSkip = onFinish,
                )
                OnbStep.ADVANCED -> AdvancedStep(
                    byokConfig = byokConfig,
                    download = download,
                    onBack = { step = OnbStep.WELCOME },
                    onDone = { step = OnbStep.TOUR },
                    onSkip = onFinish,
                    onSaveByok = onSaveByok,
                    onClearByok = onClearByok,
                    onSetImmersive = onSetImmersive,
                )
                OnbStep.TOUR -> TourStep(onFinish = onFinish)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onQuick: () -> Unit, onAdvanced: () -> Unit, onSkip: () -> Unit) {
    Text(
        "Read what's there, and notice what isn't.",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        "A quiet news reader: what your sources actually sent you, and — just as plainly — what they left out. " +
            "The lens flags loaded language, and with a dictionary downloaded it defines any word on a long-press. " +
            "Everything runs on your phone by default.",
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

/** The send-off: what's here and where it lives. See [FeatureTourContent]. */
@Composable
private fun TourStep(onFinish: () -> Unit) {
    Text(
        "What's inside",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    FeatureTourContent()
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text("Start reading")
    }
}

@Composable
private fun AdvancedStep(
    byokConfig: ByokConfig,
    download: ModelDownloadUi,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onSaveByok: (String, String, String) -> Unit,
    onClearByok: () -> Unit,
    onSetImmersive: (Boolean) -> Unit,
) {
    var modelPath by rememberSaveable { mutableStateOf(if (byokConfig.isComplete) ModelPath.BYOK else ModelPath.ON_DEVICE) }
    var immersive by rememberSaveable { mutableStateOf(false) }

    Text("Advanced setup", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)

    Text(
        "How should the lens think?",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModelChoicePanel(
        path = modelPath,
        onPathChange = { modelPath = it },
        byokConfig = byokConfig,
        onSaveByok = onSaveByok,
        onClearByok = onClearByok,
        download = download,
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SectionHeading("Reading mode")
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        ModeChip("Controls shown", !immersive) { immersive = false }
        ModeChip("Immersive", immersive) { immersive = true }
    }
    Text(
        "Controls shown keeps an obvious back button and the utility bar. Immersive hides them for a bare page; swipe right to go back. You can change this any time in Settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = {
            onSetImmersive(immersive)
            onDone()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Done") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back") }
        TextButton(onClick = onSkip) { Text("Skip") }
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

