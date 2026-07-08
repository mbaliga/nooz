package xyz.mdhv.riverwip.feature.sources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.model.FeedAutodiscovery
import xyz.mdhv.riverwip.model.ServiceDef
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceHealth

/** Transient state for the add-by-URL flow. Never persisted. */
sealed interface AddUiState {
    data object Idle : AddUiState
    data object Loading : AddUiState
    data class Added(val title: String) : AddUiState
    data class Choices(val candidates: List<FeedAutodiscovery.DiscoveredFeed>) : AddUiState
    data class Error(val message: String) : AddUiState
}

/** Result of the last manual catalogue refresh (brief §P6). Never fetched automatically. */
sealed interface CatalogueRefreshUiState {
    data object Idle : CatalogueRefreshUiState
    data object Loading : CatalogueRefreshUiState
    data class Refreshed(val serviceCount: Int) : CatalogueRefreshUiState
    data class Error(val message: String) : CatalogueRefreshUiState
}

class SourcesViewModel(
    private val repo: SourceRepository,
    private val catalogueRepo: CatalogueRepository,
) : ViewModel() {

    val sources: StateFlow<List<Source>> =
        repo.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val enabledCount: StateFlow<Int> =
        repo.observeEnabledCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    /** Local source-health monitor (brief §P6), keyed by source id for easy row lookup. */
    val health: StateFlow<Map<String, SourceHealth>> =
        repo.observeHealth()
            .map { list -> list.associateBy { it.sourceId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * Concrete, one-click starters grouped by region (brief §P1) — from the
     * built-in verified seed until/unless the user loads a remote catalogue
     * (brief §P6), which transparently takes over the same shape.
     */
    val startersByRegion: StateFlow<Map<String, List<ServiceDef>>> =
        catalogueRepo.observeCatalogue()
            .map { cat -> cat.services.filter { it.url != null }.groupBy { it.region ?: "other" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** Query-builder and keyed starters (Google News, GDELT, Mastodon, Guardian, Tier-B reference). */
    val builders: StateFlow<List<ServiceDef>> =
        catalogueRepo.observeCatalogue()
            .map { cat -> cat.services.filter { it.url == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val catalogueUrl: StateFlow<String?> =
        catalogueRepo.observeCatalogueUrl().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    val catalogueLastRefreshedAt: StateFlow<Long?> =
        catalogueRepo.observeLastRefreshedAt().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    var catalogueRefreshState: CatalogueRefreshUiState by mutableStateOf(CatalogueRefreshUiState.Idle)
        private set

    fun setCatalogueUrl(url: String) {
        viewModelScope.launch { catalogueRepo.setCatalogueUrl(url) }
    }

    fun refreshCatalogue() {
        catalogueRefreshState = CatalogueRefreshUiState.Loading
        viewModelScope.launch {
            catalogueRefreshState = when (val r = catalogueRepo.refresh()) {
                is CatalogueRepository.RefreshResult.Success ->
                    CatalogueRefreshUiState.Refreshed(r.catalogue.services.size)
                is CatalogueRepository.RefreshResult.Failed -> CatalogueRefreshUiState.Error(r.reason)
            }
        }
    }

    /** Reverts to the built-in verified starters, discarding any cached remote catalogue. */
    fun clearCatalogue() {
        catalogueRefreshState = CatalogueRefreshUiState.Idle
        viewModelScope.launch { catalogueRepo.clearCatalogue() }
    }

    var addState: AddUiState by mutableStateOf(AddUiState.Idle)
        private set

    fun resetAddState() { addState = AddUiState.Idle }

    fun addByUrl(url: String) {
        addState = AddUiState.Loading
        viewModelScope.launch {
            addState = when (val r = repo.addByUrl(url)) {
                is SourceRepository.AddResult.Added -> AddUiState.Added(r.source.title)
                is SourceRepository.AddResult.NeedsChoice -> AddUiState.Choices(r.candidates)
                is SourceRepository.AddResult.Failed -> AddUiState.Error(r.reason)
            }
        }
    }

    fun addCandidate(feed: FeedAutodiscovery.DiscoveredFeed) {
        viewModelScope.launch {
            val r = repo.addResolvedFeed(feed.url, feed.title ?: feed.url)
            addState = AddUiState.Added(r.source.title)
        }
    }

    fun addStarter(def: ServiceDef) {
        viewModelScope.launch {
            addState = when (val r = repo.addStarter(def)) {
                is SourceRepository.AddResult.Added -> AddUiState.Added(r.source.title)
                is SourceRepository.AddResult.Failed -> AddUiState.Error(r.reason)
                is SourceRepository.AddResult.NeedsChoice -> AddUiState.Idle
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) { viewModelScope.launch { repo.setEnabled(id, enabled) } }

    fun remove(id: String) { viewModelScope.launch { repo.remove(id) } }

    fun importOpml(xml: String, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.importOpml(xml)) }
    }

    suspend fun exportOpml(): String = repo.exportOpml()

    class Factory(
        private val repo: SourceRepository,
        private val catalogueRepo: CatalogueRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SourcesViewModel(repo, catalogueRepo) as T
    }
}
