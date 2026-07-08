package xyz.mdhv.riverwip.feature.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.HealthStatus
import xyz.mdhv.riverwip.model.ServiceDef
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceHealth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(vm: SourcesViewModel, modifier: Modifier = Modifier) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledCount.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val startersByRegion by vm.startersByRegion.collectAsStateWithLifecycle()
    val builders by vm.builders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // OPML export: user chooses where to save; we write the current source-set.
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

    // OPML import: read the chosen file and merge.
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Sources", style = MaterialTheme.typography.titleLarge)
                    Text(
                        // Denominator honesty on every stream-total surface (brief §1/§7).
                        "The river is drawn ${Copy.fromSources(enabledCount)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            item { AddByUrlCard(vm) }

            item {
                SectionHeader("Import / export")
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("text/xml", "application/xml", "text/x-opml", "*/*"))
                    }) { Text("Import OPML") }
                    OutlinedButton(onClick = { exportLauncher.launch("river-sources.opml") }) {
                        Text("Export OPML")
                    }
                }
            }

            item { SectionHeader("One-click starters") }
            for ((region, defs) in startersByRegion) {
                item {
                    Text(
                        region.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.Spacing.xs),
                    )
                }
                items(defs, key = { "starter-${it.id}" }) { def -> StarterRow(def) { vm.addStarter(def) } }
            }

            item { SectionHeader("Query builders & keyed") }
            items(builders, key = { "builder-${it.id}" }) { def -> BuilderRow(def) { vm.addStarter(def) } }

            item { CatalogueCard(vm) }

            item {
                SectionHeader("Your sources")
                if (sources.isEmpty()) {
                    Text(
                        "None yet. Add a feed above — this reader only ever shows " +
                            "${Copy.fromSources(0)}, never all the news.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(sources, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    health = health[source.id],
                    onToggle = { enabled -> vm.setEnabled(source.id, enabled) },
                    onRemove = { vm.remove(source.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Tokens.Spacing.md, bottom = Tokens.Spacing.xxs),
    )
}

@Composable
private fun AddByUrlCard(vm: SourcesViewModel) {
    var url by remember { mutableStateOf("") }
    val state = vm.addState

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Tokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
            Text("Add by URL", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste a feed or a site — the app finds the feed. It fetches only what you add.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    // A placeholder alone is only the accessible name while the
                    // field is empty; a label keeps it once text is entered.
                    label = { Text("Feed or site URL") },
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) vm.addByUrl(url) }),
                )
                Button(onClick = { if (url.isNotBlank()) vm.addByUrl(url) }, enabled = state !is AddUiState.Loading) {
                    Text(if (state is AddUiState.Loading) "…" else "Add")
                }
            }
            when (state) {
                is AddUiState.Error -> Text(
                    "Couldn't add: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is AddUiState.Added -> Text(
                    "Added “${state.title}”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is AddUiState.Choices -> Column {
                    Text("This page declares more than one feed — pick one:", style = MaterialTheme.typography.bodySmall)
                    state.candidates.forEach { feed ->
                        TextButton(onClick = { vm.addCandidate(feed) }) {
                            Text((feed.title ?: feed.url) + "  ·  ${feed.type.name.lowercase()}")
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun StarterRow(def: ServiceDef, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(def.title, style = MaterialTheme.typography.bodyLarge)
            def.url?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add ${def.title}") }
    }
}

@Composable
private fun BuilderRow(def: ServiceDef, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                Text(def.title, style = MaterialTheme.typography.bodyLarge)
                if (def.requiresKey) Text(
                    "free key required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            def.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        // Concrete-url builders (Google News example) can be one-click added; keyed
        // ones open their configuration in a later iteration.
        if (!def.requiresKey && def.url != null) {
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add ${def.title}") }
        }
    }
}

@Composable
private fun SourceRow(source: Source, health: SourceHealth?, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                // Merges title/url/health with the switch state into one announcement
                // ("<title>, <kind> · <url>, switch, on/off") instead of a bare,
                // unlabeled "Switch" (brief §P7 a11y pass).
                .toggleable(value = source.enabled, onValueChange = onToggle, role = Role.Switch),
        ) {
            Text(source.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${source.kind.key} · ${source.url}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (health != null) HealthLine(health)
        }
        Switch(checked = source.enabled, onCheckedChange = null)
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${source.title}") }
    }
}

/** Local source-health monitor (brief §P6): descriptive, never alarmist — shapes and counts, not warnings. */
@Composable
private fun HealthLine(health: SourceHealth) {
    val label = when (health.status) {
        HealthStatus.UNKNOWN -> "not fetched yet"
        HealthStatus.OK -> "fetched ${relativeTime(health.lastFetchAt)}"
        HealthStatus.STALE -> "stale — last fetched ${relativeTime(health.lastFetchAt)}"
        HealthStatus.RATE_LIMITED -> "rate-limited — ${health.lastError}"
        HealthStatus.FAILING -> "${health.consecutiveFailures} failed fetch(es) — ${health.lastError}"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (health.status == HealthStatus.OK || health.status == HealthStatus.UNKNOWN) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

/** Coarse relative-time label. Never a precise duration on read events elsewhere (brief §3) — this is fetch metadata, not usage data, but stays just as coarse. */
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

@Composable
private fun CatalogueCard(vm: SourcesViewModel) {
    val savedUrl by vm.catalogueUrl.collectAsStateWithLifecycle()
    val lastRefreshedAt by vm.catalogueLastRefreshedAt.collectAsStateWithLifecycle()
    var urlInput by remember(savedUrl) { mutableStateOf(savedUrl.orEmpty()) }
    val refreshState = vm.catalogueRefreshState

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Tokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
            Text("Catalogue", style = MaterialTheme.typography.titleMedium)
            Text(
                "Optionally point this at a catalogue.json to refresh starters and free-tier " +
                    "limits without an app update. Nothing is fetched unless you tap refresh — " +
                    "there is no default URL, since this build can't verify one is live.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Catalogue URL") },
                placeholder = { Text("https://…/catalogue.json") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.setCatalogueUrl(urlInput) }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                Button(
                    onClick = {
                        vm.setCatalogueUrl(urlInput)
                        vm.refreshCatalogue()
                    },
                    enabled = urlInput.isNotBlank() && refreshState !is CatalogueRefreshUiState.Loading,
                ) { Text(if (refreshState is CatalogueRefreshUiState.Loading) "…" else "Refresh") }
                if (savedUrl != null) {
                    OutlinedButton(onClick = {
                        urlInput = ""
                        vm.clearCatalogue()
                    }) { Text("Use built-in starters") }
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
}
