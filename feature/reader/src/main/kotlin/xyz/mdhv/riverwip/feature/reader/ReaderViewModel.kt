package xyz.mdhv.riverwip.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.model.DwellBucket
import xyz.mdhv.riverwip.model.Item

/** The reader's article state, once a reading session has begun. */
sealed interface ArticleUiState {
    data object Loading : ArticleUiState
    data class Loaded(val paragraphs: List<String>, val fromCache: Boolean) : ArticleUiState
    /** Extraction failed or yielded nothing — fall back to the feed's own summary, if any. */
    data class Fallback(val summary: String?) : ArticleUiState
}

class ReaderViewModel(
    private val itemRepository: ItemRepository,
    private val sourceRepository: SourceRepository,
    private val articleRepository: ArticleRepository,
    private val readEventRepository: ReadEventRepository,
) : ViewModel() {

    /** The stream, from the user's currently enabled sources only (brief §1). */
    val items: StateFlow<List<Item>> = itemRepository.observeItemsForEnabledSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** The honest denominator for this surface's copy. */
    val enabledSourceCount: StateFlow<Int> = sourceRepository.observeEnabledCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    var selectedItem: Item? by mutableStateOf(null)
        private set

    private val _articleState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val articleState: StateFlow<ArticleUiState> = _articleState

    /** Open an item: record a coarse read event (brief §3 — glance/partial/read, never a precise duration) and load its text. */
    fun openItem(item: Item, viaRiver: Boolean = false) {
        selectedItem = item
        _articleState.value = ArticleUiState.Loading
        viewModelScope.launch {
            readEventRepository.record(item.id, DwellBucket.GLANCE, viaRiver)
            val text = articleRepository.textFor(item.id, item.canonicalUrl)
            _articleState.value = if (text != null) {
                ArticleUiState.Loaded(text.paragraphs, text.fromCache)
            } else {
                ArticleUiState.Fallback(item.summary)
            }
        }
    }

    /** The user read past the initial glance — upgrade the dwell bucket (still coarse, never a duration). */
    fun markPartiallyRead(item: Item, viaRiver: Boolean = false) {
        viewModelScope.launch { readEventRepository.record(item.id, DwellBucket.PARTIAL, viaRiver) }
    }

    fun markFullyRead(item: Item, viaRiver: Boolean = false) {
        viewModelScope.launch { readEventRepository.record(item.id, DwellBucket.READ, viaRiver) }
    }

    /**
     * Closing after the text loaded successfully upgrades the dwell bucket to
     * READ — a coarse v1 heuristic (brief §3: buckets only, never a precise
     * duration or scroll-position tracking).
     */
    fun closeItem() {
        val item = selectedItem
        if (item != null && _articleState.value is ArticleUiState.Loaded) {
            markFullyRead(item)
        }
        selectedItem = null
    }

    class Factory(
        private val itemRepository: ItemRepository,
        private val sourceRepository: SourceRepository,
        private val articleRepository: ArticleRepository,
        private val readEventRepository: ReadEventRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(itemRepository, sourceRepository, articleRepository, readEventRepository) as T
    }
}
