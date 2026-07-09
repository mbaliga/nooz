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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.model.DwellBucket
import xyz.mdhv.riverwip.model.Item

/** The reader's article state, once a reading session has begun. */
sealed interface ArticleUiState {
    data object Loading : ArticleUiState
    data class Loaded(val paragraphs: List<String>, val fromCache: Boolean) : ArticleUiState
    /** Extraction failed or yielded nothing — fall back to the feed's own summary, if any. */
    data class Fallback(val summary: String?) : ArticleUiState
}

/** Outcome of the last manual/auto refresh, so the reader can report it honestly (brief §3: nothing silent). */
data class RefreshResult(
    val newItems: Int,
    val sourcesTried: Int,
    val sourcesFailed: Int,
    val error: String? = null,
) {
    val allFailed: Boolean get() = error != null || (sourcesTried > 0 && sourcesFailed == sourcesTried)
}

class ReaderViewModel(
    private val itemRepository: ItemRepository,
    private val sourceRepository: SourceRepository,
    private val articleRepository: ArticleRepository,
    private val readEventRepository: ReadEventRepository,
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
) : ViewModel() {

    /** The stream, from the user's currently enabled sources only (brief §1). */
    val items: StateFlow<List<Item>> = itemRepository.observeItemsForEnabledSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** The honest denominator for this surface's copy. */
    val enabledSourceCount: StateFlow<Int> = sourceRepository.observeEnabledCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    private val _isRefreshing = MutableStateFlow(false)
    /** True while a fetch is in flight, so the UI can show progress instead of a bare empty state. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _lastRefresh = MutableStateFlow<RefreshResult?>(null)
    val lastRefresh: StateFlow<RefreshResult?> = _lastRefresh

    init {
        // Fetch once, automatically, the first time the reader is opened with
        // sources added but nothing yet ingested — so adding sources and coming
        // back to the reader "just works" instead of waiting on the background
        // cadence (WorkManager's minimum period is 15 min and its first run is
        // delayed). `.first { it }` suspends until that condition first holds,
        // then completes — it never re-fires on its own.
        viewModelScope.launch {
            combine(enabledSourceCount, items) { count, list -> count > 0 && list.isEmpty() }
                .first { it }
            if (_lastRefresh.value == null) refresh()
        }
    }

    /**
     * Fetch every enabled source now, ingest, and roll the river's aggregates
     * forward — the same work the background [xyz.mdhv.riverwip.data.work.FetchWorker]
     * does, but on demand with visible progress. Per-source failures are isolated
     * (one dead feed never blocks the rest).
     */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val outcomes = itemRepository.fetchAndIngestAllEnabled()
                weeklyAggregateRepository.recompute()
                _lastRefresh.value = RefreshResult(
                    newItems = outcomes.sumOf { it.newItemCount },
                    sourcesTried = outcomes.size,
                    sourcesFailed = outcomes.count { !it.succeeded },
                )
            } catch (e: Exception) {
                _lastRefresh.value = RefreshResult(0, 0, 0, error = e.message ?: e.javaClass.simpleName)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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
        private val weeklyAggregateRepository: WeeklyAggregateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(
                itemRepository,
                sourceRepository,
                articleRepository,
                readEventRepository,
                weeklyAggregateRepository,
            ) as T
    }
}
