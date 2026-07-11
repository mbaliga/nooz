package xyz.mdhv.riverwip.feature.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.design.Tokens

/** OPML import/export — the user's data is the user's file. */
@Composable
fun OpmlButtons(vm: SourcesViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml"),
    ) { uri ->
        if (uri != null) scope.launch {
            val opml = vm.exportOpml()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(opml.toByteArray()) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            val xml = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (xml != null) vm.importOpml(xml) { /* count merged; UI updates via flow */ }
        }
    }

    OutlinedButton(onClick = {
        importLauncher.launch(arrayOf("text/xml", "application/xml", "text/x-opml", "*/*"))
    }) { Text("Import OPML") }
    OutlinedButton(onClick = { exportLauncher.launch("nooz-sources.opml") }) {
        Text("Export OPML")
    }
}

/** Coarse relative-time label for catalogue freshness. */
private fun relativeTime(epochMillis: Long?): String {
    if (epochMillis == null) return "never"
    val deltaMs = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0)
    val minutes = deltaMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

/**
 * The optional remote catalogue (brief §P6): user-supplied URL, manual pull,
 * never automatic. Rendered as a plain line/text section — an underlined field
 * and text actions — to match the rest of Edit (owner: no boxed cards).
 */
@Composable
fun CatalogueCard(vm: SourcesViewModel) {
    val savedUrl by vm.catalogueUrl.collectAsStateWithLifecycle()
    val lastRefreshedAt by vm.catalogueLastRefreshedAt.collectAsStateWithLifecycle()
    var urlInput by remember(savedUrl) { mutableStateOf(savedUrl.orEmpty()) }
    val refreshState = vm.catalogueRefreshState

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Tokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        Text(
            "Catalogue",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Optionally point this at a catalogue.json to refresh starters and free-tier " +
                "limits without an app update. Nothing is fetched unless you tap refresh — " +
                "there is no default URL, since this build can't verify one is live.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.setCatalogueUrl(urlInput) }),
                decorationBox = { inner ->
                    if (urlInput.isEmpty()) {
                        Text(
                            "https://…/catalogue.json",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Catalogue URL" },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            TextButton(
                onClick = {
                    vm.setCatalogueUrl(urlInput)
                    vm.refreshCatalogue()
                },
                enabled = urlInput.isNotBlank() && refreshState !is CatalogueRefreshUiState.Loading,
                contentPadding = PaddingValues(0.dp),
            ) { Text(if (refreshState is CatalogueRefreshUiState.Loading) "Refreshing…" else "Refresh") }
            if (savedUrl != null) {
                TextButton(
                    onClick = {
                        urlInput = ""
                        vm.clearCatalogue()
                    },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Use built-in starters") }
            }
        }
        when (refreshState) {
            is CatalogueRefreshUiState.Error -> Text(
                "Couldn't refresh: ${refreshState.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            is CatalogueRefreshUiState.Refreshed -> Text(
                "Loaded ${refreshState.serviceCount} service definitions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> lastRefreshedAt?.let {
                Text(
                    "Last refreshed ${relativeTime(it)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
