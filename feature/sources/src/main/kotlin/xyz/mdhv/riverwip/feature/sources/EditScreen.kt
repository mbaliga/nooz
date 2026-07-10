package xyz.mdhv.riverwip.feature.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.feature.river.CrossSectionPanel
import xyz.mdhv.riverwip.model.GlobeModel
import xyz.mdhv.riverwip.model.HealthStatus
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.ServiceDef
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.toSourceOrNull

private enum class EditTab { SOURCES, REGION_TOPICS }

/**
 * Nooz EDIT (owner's mocks + flow map): two tabs. Sources — one-click starters
 * with check-circle toggles and add-by-URL with an honest error state; Region &
 * Topics — the globe, the topic chips, and the metrics block (coverage,
 * breadth, per-topic over/under — every number tap-explained). DONE saves the
 * filter draft and returns to the Stand.
 */
@Composable
fun EditScreen(
    vm: SourcesViewModel,
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    startOnRegionTab: Boolean = false,
) {
    var tab by rememberSaveable { mutableStateOf(if (startOnRegionTab) EditTab.REGION_TOPICS else EditTab.SOURCES) }
    val savedFilter by vm.filter.collectAsStateWithLifecycle()

    // Filter draft: seeded from the saved filter, committed on DONE.
    var draftRegionKey by rememberSaveable(savedFilter.region.key) { mutableStateOf(savedFilter.region.key) }
    val topicsSaver = remember {
        listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    }
    var draftTopics by rememberSaveable(savedFilter.topicKeys, stateSaver = topicsSaver) {
        mutableStateOf(savedFilter.topicKeys)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Nooz", style = MaterialTheme.typography.headlineMedium)
            Text(
                "  EDIT",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
            TextButton(onClick = {
                vm.saveFilter(ReaderFilter(Region.fromKey(draftRegionKey), draftTopics))
                onDone()
            }) {
                Text("DONE", style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Spacing.md)) {
            TabLabel("Sources", tab == EditTab.SOURCES) { tab = EditTab.SOURCES }
            Spacer(Modifier.width(Tokens.Spacing.xl))
            TabLabel("Region & Topics", tab == EditTab.REGION_TOPICS) { tab = EditTab.REGION_TOPICS }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when (tab) {
            EditTab.SOURCES -> SourcesTab(vm)
            EditTab.REGION_TOPICS -> RegionTopicsTab(
                vm = vm,
                regionKey = draftRegionKey,
                topics = draftTopics,
                onRegion = { draftRegionKey = it.key },
                onTopics = { draftTopics = it },
            )
        }
    }
}

@Composable
private fun TabLabel(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { role = Role.Tab }
            .padding(vertical = Tokens.Spacing.xs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active) {
            Box(
                Modifier
                    .padding(top = Tokens.Spacing.xxs)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onBackground),
            )
        }
    }
}

// ---------------------------------------------------------------- Sources tab

@Composable
private fun SourcesTab(vm: SourcesViewModel) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val startersByRegion by vm.startersByRegion.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md),
    ) {
        val addedIds = remember(sources) { sources.map { it.id }.toSet() }
        for ((region, defs) in startersByRegion) {
            Row(
                Modifier.fillMaxWidth().padding(top = Tokens.Spacing.lg, bottom = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "One-click starters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    region.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (def in defs) {
                val id = remember(def) { def.toSourceOrNull(addedAt = 0L)?.id }
                StarterRow(
                    def = def,
                    added = id != null && id in addedIds,
                    unhealthy = id != null && (health[id]?.status == HealthStatus.FAILING || health[id]?.status == HealthStatus.RATE_LIMITED),
                    onToggle = { vm.toggleStarter(def) },
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.md),
            )
        }

        AddByUrlSection(vm)

        // Quiet utilities below the mock's fold: the user's data stays the user's.
        Row(
            Modifier.padding(vertical = Tokens.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
        ) {
            OpmlButtons(vm)
        }
        CatalogueCard(vm)
        Spacer(Modifier.height(Tokens.Spacing.xl))
    }
}

@Composable
private fun StarterRow(def: ServiceDef, added: Boolean, unhealthy: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (added) "Remove ${def.title}" else "Add ${def.title}", onClick = onToggle)
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(def.title, style = MaterialTheme.typography.titleMedium)
            val line = def.url ?: def.notes
            if (line != null) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (unhealthy) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "This source is failing to fetch",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = Tokens.Spacing.xs).size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (added) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (added) {
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

@Composable
private fun AddByUrlSection(vm: SourcesViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    val state = vm.addState

    Text(
        "Add by URL",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Tokens.Spacing.lg, bottom = Tokens.Spacing.xs),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = url,
            onValueChange = { url = it; vm.resetAddState() },
            textStyle = TextStyle.Default.merge(MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground)),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Feed or site URL" },
        )
        if (state is AddUiState.Error) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Couldn't add this URL",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        } else {
            IconButton(onClick = { if (url.isNotBlank()) vm.addByUrl(url) }, enabled = state !is AddUiState.Loading) {
                Icon(Icons.Filled.Add, contentDescription = "Add this URL")
            }
        }
    }
    HorizontalDivider(
        color = if (state is AddUiState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
    )
    when (state) {
        is AddUiState.Error -> Text(
            "Couldn't add: ${state.message}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Tokens.Spacing.xxs),
        )
        is AddUiState.Added -> Text(
            "Added “${state.title}”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Tokens.Spacing.xxs),
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
    Text(
        "Paste a feed or a site url - The app finds the feed; fetches only what you add.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Tokens.Spacing.xs),
    )
}

// --------------------------------------------------------- Region & Topics tab

@Composable
private fun RegionTopicsTab(
    vm: SourcesViewModel,
    regionKey: String,
    topics: Set<String>,
    onRegion: (Region) -> Unit,
    onTopics: (Set<String>) -> Unit,
) {
    val mixByRegion by vm.mixByRegion.collectAsStateWithLifecycle()
    val aggregates by vm.aggregates.collectAsStateWithLifecycle()

    val savedRegion = Region.fromKey(regionKey)
    var yaw by rememberSaveable(regionKey) {
        mutableDoubleStateOf(
            if (savedRegion == Region.GLOBAL) 20.0 else -(savedRegion.fromLon + savedRegion.toLon) / 2,
        )
    }
    var pitch by rememberSaveable { mutableDoubleStateOf(-10.0) }
    var bandHalf by rememberSaveable(regionKey) {
        mutableDoubleStateOf(if (savedRegion == Region.GLOBAL) 180.0 else 16.0)
    }

    val aimedRegion = if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) {
        Region.GLOBAL
    } else {
        Region.forLongitude(GlobeModel.centerLongitude(yaw))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md),
    ) {
        Text(
            "Drag to spin · pinch to widen · ring shows this region's topic mix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.sm),
        )
        GlobeCanvas(
            yaw = yaw,
            pitch = pitch,
            bandHalf = bandHalf,
            ringMix = mixByRegion[aimedRegion].orEmpty(),
            onSpin = { dYaw, dPitch ->
                yaw += dYaw
                pitch = (pitch + dPitch).coerceIn(-70.0, 70.0)
                onRegion(
                    if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) Region.GLOBAL
                    else Region.forLongitude(GlobeModel.centerLongitude(yaw)),
                )
            },
            onZoomBand = { factor ->
                bandHalf = (bandHalf * factor).coerceIn(8.0, 180.0)
                onRegion(
                    if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) Region.GLOBAL
                    else Region.forLongitude(GlobeModel.centerLongitude(yaw)),
                )
            },
            modifier = Modifier.padding(horizontal = Tokens.Spacing.xl),
        )
        Text(
            aimedRegion.label,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Tokens.Spacing.sm),
        )
        Text(
            if (aimedRegion == Region.GLOBAL) "full width" else "band ±${bandHalf.toInt()}°",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.sm), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = {
                yaw = 20.0
                pitch = -10.0
                bandHalf = 180.0
                onRegion(Region.GLOBAL)
            }) { Text("Reset to Global") }
        }

        // Topics: the filter's other half. Chips, not gestures — no hidden doors.
        Text(
            "Topics",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Tokens.Spacing.sm, bottom = Tokens.Spacing.xxs),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
        ) {
            FilterChip(selected = topics.isEmpty(), onClick = { onTopics(emptySet()) }, label = { Text("All") })
            for (topic in Topic.entries) {
                FilterChip(
                    selected = topic.key in topics,
                    onClick = {
                        onTopics(if (topic.key in topics) topics - topic.key else topics + topic.key)
                    },
                    label = { Text(topic.placeholderLabel) },
                )
            }
        }

        if (aggregates.isNotEmpty()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.md),
            )
            CrossSectionPanel(aggregates, aggregates.lastIndex)
        }
        Spacer(Modifier.height(Tokens.Spacing.xl))
    }
}
