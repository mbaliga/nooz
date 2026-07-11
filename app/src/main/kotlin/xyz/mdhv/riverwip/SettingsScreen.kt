package xyz.mdhv.riverwip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
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
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setFont(font: ReaderFont) = viewModelScope.launch { repo.setReaderFont(font) }
    fun setShowReadingTime(show: Boolean) = viewModelScope.launch { repo.setShowReadingTime(show) }
    fun setTextScale(scale: TextScale) = viewModelScope.launch { repo.setTextScale(scale) }
    fun setHighlightLoadedLanguage(on: Boolean) = viewModelScope.launch { repo.setHighlightLoadedLanguage(on) }
    fun setImmersiveReader(on: Boolean) = viewModelScope.launch { repo.setImmersiveReader(on) }

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

    class Factory(
        private val repo: SettingsRepository,
        private val dictionaryRepo: DictionaryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repo, dictionaryRepo) as T
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
                .padding(horizontal = Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
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
        }
    }
}

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
