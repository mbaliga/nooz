package xyz.mdhv.riverwip

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.DataExporter
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.crash.CrashRecovery
import xyz.mdhv.riverwip.inference.byok.ByokConfig
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore
import xyz.mdhv.riverwip.design.HyleGroteskClassic
import xyz.mdhv.riverwip.design.HyleGroteskPlus
import xyz.mdhv.riverwip.design.HylePrint
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.AppSettings
import xyz.mdhv.riverwip.model.DictionaryOption
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.TextScale
import xyz.mdhv.riverwip.model.ThemeMode

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val dictionaryRepo: DictionaryRepository,
    private val dataExporter: DataExporter,
    private val byokStore: ByokConfigStore,
) : ViewModel() {

    // BYOK (#18): the user's own OpenAI-compatible endpoint. SharedPreferences
    // isn't reactive, so mirror it into Compose state and refresh on save/clear.
    var byokConfig: ByokConfig by mutableStateOf(byokStore.load())
        private set

    fun saveByok(baseUrl: String, apiKey: String, model: String) {
        val config = ByokConfig(baseUrl, apiKey, model)
        byokStore.save(config)
        byokConfig = byokStore.load()
    }

    fun clearByok() {
        byokStore.clear()
        byokConfig = byokStore.load()
    }
    val settings: StateFlow<AppSettings> =
        repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setFont(font: ReaderFont) = viewModelScope.launch { repo.setReaderFont(font) }
    fun setShowReadingTime(show: Boolean) = viewModelScope.launch { repo.setShowReadingTime(show) }
    fun setTextScale(scale: TextScale) = viewModelScope.launch { repo.setTextScale(scale) }
    fun setHighlightLoadedLanguage(on: Boolean) = viewModelScope.launch { repo.setHighlightLoadedLanguage(on) }
    fun setImmersiveReader(on: Boolean) = viewModelScope.launch { repo.setImmersiveReader(on) }
    fun setTwoFingerBrightness(on: Boolean) = viewModelScope.launch { repo.setTwoFingerBrightness(on) }
    fun setTwoFingerThemeFlick(on: Boolean) = viewModelScope.launch { repo.setTwoFingerThemeFlick(on) }
    fun completeOnboarding() = viewModelScope.launch { repo.setOnboarded(true) }

    // Dictionary lens: one-click download of a chosen dictionary (owner's spec).
    val dictionaryOptions: List<DictionaryOption> = dictionaryRepo.options
    val downloadedDictionaryId: StateFlow<String?> =
        dictionaryRepo.observeDownloadedId().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    var downloadingDictionaryId: String? by mutableStateOf(null)
        private set
    var dictionaryError: String? by mutableStateOf(null)
        private set

    fun downloadDictionary(option: DictionaryOption) {
        if (downloadingDictionaryId != null) return
        downloadingDictionaryId = option.id
        dictionaryError = null
        viewModelScope.launch {
            val result = dictionaryRepo.download(option)
            downloadingDictionaryId = null
            if (result.isFailure) {
                dictionaryError = result.exceptionOrNull()?.message ?: "Couldn't download the dictionary."
            }
        }
    }

    /** Assemble the user's whole profile as JSON, for the export-to-file action (#9). */
    suspend fun exportJson(): String = dataExporter.exportJson(System.currentTimeMillis())

    class Factory(
        private val repo: SettingsRepository,
        private val dictionaryRepo: DictionaryRepository,
        private val dataExporter: DataExporter,
        private val byokStore: ByokConfigStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repo, dictionaryRepo, dataExporter, byokStore) as T
    }
}

/**
 * Settings (owner's mock, 2026-07): Theme and Font as rows of circular "T"
 * swatches, plus the Show Reading Time toggle. The mock's fourth row, "Show
 * Progress", was struck through in the owner's own artwork — omitted on
 * purpose, not forgotten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val downloadedDictId by vm.downloadedDictionaryId.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Spacing.md)
                .padding(bottom = Tokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
            CrashSection()

            Text(
                "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                SwatchCircle(
                    label = "White theme",
                    selected = settings.themeMode == ThemeMode.WHITE,
                    background = Color(0xFFFFFFFF),
                    letterColor = Tokens.Palette.paperInk,
                    onClick = { vm.setTheme(ThemeMode.WHITE) },
                )
                SwatchCircle(
                    label = "Paper theme",
                    selected = settings.themeMode == ThemeMode.PAPER,
                    background = Tokens.Palette.paperField,
                    letterColor = Tokens.Palette.paperInk,
                    onClick = { vm.setTheme(ThemeMode.PAPER) },
                )
                SwatchCircle(
                    label = "Dark theme",
                    selected = settings.themeMode == ThemeMode.DARK,
                    background = Color(0xFF262624),
                    letterColor = Color(0xFFECEAE6),
                    onClick = { vm.setTheme(ThemeMode.DARK) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                "Font",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The owner's Settings mock: a list of the font names, each set in
            // its own face, a check on the chosen one (not colour swatches).
            Column(modifier = Modifier.selectableGroup()) {
                FontRow(
                    name = "Hyle Grotesk Classic",
                    family = HyleGroteskClassic,
                    selected = settings.readerFont == ReaderFont.GROTESK_CLASSIC,
                    onClick = { vm.setFont(ReaderFont.GROTESK_CLASSIC) },
                )
                FontRow(
                    name = "Hyle Grotesk Plus",
                    family = HyleGroteskPlus,
                    selected = settings.readerFont == ReaderFont.GROTESK_PLUS,
                    onClick = { vm.setFont(ReaderFont.GROTESK_PLUS) },
                )
                FontRow(
                    name = "Hyle Print",
                    family = HylePrint,
                    selected = settings.readerFont == ReaderFont.PRINT,
                    onClick = { vm.setFont(ReaderFont.PRINT) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                "Text Size",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                for (scale in TextScale.entries) {
                    val chosen = settings.textScale == scale
                    Text(
                        scale.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            // Non-colour channel: the chosen size is bold, not just darker.
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (chosen) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .selectable(
                                selected = chosen,
                                role = Role.RadioButton,
                                onClick = { vm.setTextScale(scale) },
                            )
                            .minimumInteractiveComponentSize()
                            .padding(vertical = Tokens.Spacing.xxs),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.showReadingTime,
                        onValueChange = { vm.setShowReadingTime(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show Reading Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = settings.showReadingTime, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.highlightLoadedLanguage,
                        onValueChange = { vm.setHighlightLoadedLanguage(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Highlight loaded language",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Underlines charged wording as you read; tap it for the evidence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.highlightLoadedLanguage, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.immersiveReader,
                        onValueChange = { vm.setImmersiveReader(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Immersive reading",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Hides the back button and controls for a bare page. Off by default — swipe right or tap back to return.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.immersiveReader, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                "Dictionary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Download a dictionary, then long-press any word as you read for its meaning — Kindle-style.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (option in vm.dictionaryOptions) {
                DictionaryRow(
                    option = option,
                    downloaded = downloadedDictId == option.id,
                    downloading = vm.downloadingDictionaryId == option.id,
                    onDownload = { vm.downloadDictionary(option) },
                )
            }
            vm.dictionaryError?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            IntelligenceSection(vm)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            GesturesSection(settings, vm)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            YourDataSection(vm)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AboutSection()
        }
    }
}

/**
 * A pending device-only crash report (owner's #17, Hyle crash-recovery). Shown
 * only when the app crashed last run — headline first, the full trace behind a
 * toggle, copy-to-clipboard, and dismiss. Nothing is ever transmitted.
 */
@Composable
private fun CrashSection() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(CrashRecovery.pending(context)) }
    var showTrace by remember { mutableStateOf(false) }
    val current = report ?: return

    Column(
        Modifier
            .fillMaxWidth()
            .border(Tokens.Border.thin, MaterialTheme.colorScheme.error, RoundedCornerShape(Tokens.Radius.sm))
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        Text("Nooz closed unexpectedly last time", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(current.headline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(
            "The report stayed on your device — nothing was sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showTrace) {
            Text(
                current.fullReport,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            TextButton(onClick = { showTrace = !showTrace }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showTrace) "Hide details" else "Show details")
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(current.fullReport)) },
                contentPadding = PaddingValues(0.dp),
            ) { Text("Copy") }
            TextButton(
                onClick = { CrashRecovery.clear(context); report = null },
                contentPadding = PaddingValues(0.dp),
            ) { Text("Dismiss") }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Reader intelligence (owner's #18). The lens defuse is on-device-first and
 * honest about its gaps; this is where a reader can bring their own key — an
 * OpenAI-compatible endpoint they control — for cloud rewrites, always marked
 * cloud. On-device model execution isn't wired in this build yet, and that's
 * stated plainly rather than dressed up.
 */
@Composable
private fun IntelligenceSection(vm: SettingsViewModel) {
    val config = vm.byokConfig
    var expanded by remember { mutableStateOf(config.isComplete) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Reader intelligence", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (config.isComplete) "Bring-your-own-key: ${config.model}" else "On-device first · bring your own key (optional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse intelligence" else "Expand intelligence",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        Text(
            "The lens defuses loaded language on-device by default. On-device model execution is still being wired in — until then, defuse can route to your own OpenAI-compatible endpoint. Cloud rewrites are always marked cloud, and your key stays on the device (never exported).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UnderlinedField("Base URL", baseUrl, "https://api.openai.com/v1", onValueChange = { baseUrl = it })
        UnderlinedField("API key", apiKey, "sk-…", masked = true, onValueChange = { apiKey = it })
        UnderlinedField("Model", model, "gpt-4o-mini", onValueChange = { model = it })
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            TextButton(
                onClick = { vm.saveByok(baseUrl, apiKey, model) },
                enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(),
                contentPadding = PaddingValues(0.dp),
            ) { Text("Save key") }
            if (config.isComplete) {
                TextButton(onClick = { vm.clearByok() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Remove key")
                }
            }
        }
    }
}

@Composable
private fun UnderlinedField(
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

/**
 * Configurable reader gestures (owner's #11). Collapsed by default — a
 * "Gestures" disclosure that opens the second-level toggles, since the defaults
 * are fine and most readers never need them.
 */
@Composable
private fun GesturesSection(settings: AppSettings, vm: SettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Gestures", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Two-finger reader gestures. Defaults are on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse gestures" else "Expand gestures",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        SettingSwitchRow(
            title = "Two-finger drag → brightness",
            subtitle = "Slide two fingers up or down to dim or brighten the page.",
            checked = settings.twoFingerBrightness,
            onCheckedChange = { vm.setTwoFingerBrightness(it) },
        )
        SettingSwitchRow(
            title = "Two-finger flick → theme",
            subtitle = "Flick two fingers sideways to step the paper tint.",
            checked = settings.twoFingerThemeFlick,
            onCheckedChange = { vm.setTwoFingerThemeFlick(it) },
        )
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * The user's data is the user's (owner's #9 + brief §4). One tap writes the
 * whole local profile — settings, filter, sources, clippings, and the coarse
 * read log — to a JSON file the user picks. Local only; no keys, no upload.
 */
@Composable
private fun YourDataSection(vm: SettingsViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var exporting by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            exporting = true
            scope.launch {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                exporting = false
            }
        }
    }

    Text(
        "Your data",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Export everything as open JSON — preferences, your region and topics, your sources, your clippings, and the coarse read log. Local only; API keys are never included.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = { exportLauncher.launch("nooz-data.json") },
        enabled = !exporting,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(if (exporting) "Exporting…" else "Export everything")
    }
}

/**
 * About (owner's #16): Nooz is one of the mdhv.xyz apps. Point curious readers
 * at the studio and its siblings — quietly, no tracking, just links.
 */
@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    Text(
        "About",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Nooz is a news reader whose subject is omission — what got left out. It's made by mdhv.xyz, a small studio of focused, quiet apps.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    TextButton(onClick = { uriHandler.openUri("https://mdhv.xyz") }, contentPadding = PaddingValues(0.dp)) {
        Text("Visit mdhv.xyz")
    }
    Text(
        "More from the studio",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    for (app in SISTER_APPS) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(app.url) }
                .padding(vertical = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(app.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private data class SisterApp(val name: String, val blurb: String, val url: String)

// The mdhv.xyz family the owner named (#16). Blurbs are deliberately spare;
// links point at the studio's own pages, not third-party stores.
private val SISTER_APPS = listOf(
    SisterApp("Animalcules", "A microcosm you keep.", "https://mdhv.xyz/animalcules"),
    SisterApp("Clackpad", "A keyboard that feels like one.", "https://mdhv.xyz/clackpad"),
    SisterApp("FoneBru", "Your phone, brewed down.", "https://mdhv.xyz/fonebru"),
)

@Composable
private fun DictionaryRow(
    option: xyz.mdhv.riverwip.model.DictionaryOption,
    downloaded: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(option.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${option.sizeHuman} · ${option.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Live region: the download status change is spoken, not silent.
        Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
            when {
                downloading -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).semantics { contentDescription = "Downloading dictionary" },
                    strokeWidth = 2.dp,
                )
                downloaded -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                else -> androidx.compose.material3.TextButton(onClick = onDownload) {
                    Text("Download")
                }
            }
        }
    }
}

/** One circular "T" swatch (the mock's theme selector). Selection = ring + a check badge, never colour alone. */
@Composable
private fun SwatchCircle(
    label: String,
    selected: Boolean,
    background: Color,
    letterColor: Color,
    onClick: () -> Unit,
    fontFamily: FontFamily = HyleGroteskClassic,
) {
    val ring = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant
    Box(modifier = Modifier.size(62.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(background)
                .border(if (selected) Tokens.Border.thick else Tokens.Border.thin, ring, CircleShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "T",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = fontFamily),
                color = letterColor,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** One reading-font choice: the family's own name, set in that family, checked when chosen. */
@Composable
private fun FontRow(
    name: String,
    family: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Tokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = family),
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
