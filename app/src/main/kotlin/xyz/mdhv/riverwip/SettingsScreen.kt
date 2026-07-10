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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.design.HyleMono
import xyz.mdhv.riverwip.design.HyleSans
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.AppSettings
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.TextScale
import xyz.mdhv.riverwip.model.ThemeMode

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setFont(font: ReaderFont) = viewModelScope.launch { repo.setReaderFont(font) }
    fun setShowReadingTime(show: Boolean) = viewModelScope.launch { repo.setShowReadingTime(show) }
    fun setTextScale(scale: TextScale) = viewModelScope.launch { repo.setTextScale(scale) }

    class Factory(private val repo: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repo) as T
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
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                SwatchCircle(
                    label = "Serif reading font",
                    selected = settings.readerFont == ReaderFont.SERIF,
                    background = Tokens.Palette.paperRaised,
                    letterColor = Tokens.Palette.paperInk,
                    fontFamily = FontFamily.Serif,
                    onClick = { vm.setFont(ReaderFont.SERIF) },
                )
                SwatchCircle(
                    label = "Archivo reading font",
                    selected = settings.readerFont == ReaderFont.SANS,
                    background = Tokens.Palette.paperRaised,
                    letterColor = Tokens.Palette.paperInk,
                    fontFamily = HyleSans,
                    onClick = { vm.setFont(ReaderFont.SANS) },
                )
                SwatchCircle(
                    label = "JetBrains Mono reading font",
                    selected = settings.readerFont == ReaderFont.MONO,
                    background = Tokens.Palette.paperRaised,
                    letterColor = Tokens.Palette.paperInk,
                    fontFamily = HyleMono,
                    onClick = { vm.setFont(ReaderFont.MONO) },
                )
            }
            Text(
                "Serif · Archivo · JetBrains Mono — the last two are Hyle's own faces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    Text(
                        scale.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (settings.textScale == scale) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .selectable(
                                selected = settings.textScale == scale,
                                role = Role.RadioButton,
                                onClick = { vm.setTextScale(scale) },
                            )
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
        }
    }
}

/** One circular "T" swatch (the mock's selector idiom). Selection = ring, never colour alone. */
@Composable
private fun SwatchCircle(
    label: String,
    selected: Boolean,
    background: Color,
    letterColor: Color,
    onClick: () -> Unit,
    fontFamily: FontFamily = FontFamily.Serif,
) {
    val ring = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(56.dp)
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
}
