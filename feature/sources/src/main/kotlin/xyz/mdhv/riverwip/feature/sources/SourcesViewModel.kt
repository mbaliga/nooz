package xyz.mdhv.riverwip.feature.sources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.model.FeedAutodiscovery
import xyz.mdhv.riverwip.model.ServiceDef
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.Starters

/** Transient state for the add-by-URL flow. Never persisted. */
sealed interface AddUiState {
    data object Idle : AddUiState
    data object Loading : AddUiState
    data class Added(val title: String) : AddUiState
    data class Choices(val candidates: List<FeedAutodiscovery.DiscoveredFeed>) : AddUiState
    data class Error(val message: String) : AddUiState
}

class SourcesViewModel(private val repo: SourceRepository) : ViewModel() {

    val sources: StateFlow<List<Source>> =
        repo.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val enabledCount: StateFlow<Int> =
        repo.observeEnabledCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    /** Verified concrete starters, grouped by region (brief §P1). */
    val startersByRegion: Map<String, List<ServiceDef>> = Starters.feedsByRegion()

    /** Builder / keyed starters (Google News, GDELT, Mastodon, Guardian). */
    val builders: List<ServiceDef> = Starters.builders + Starters.tierBReference

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

    class Factory(private val repo: SourceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SourcesViewModel(repo) as T
    }
}
