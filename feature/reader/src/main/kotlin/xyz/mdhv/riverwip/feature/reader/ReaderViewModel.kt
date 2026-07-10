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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.DayLoomLayout
import xyz.mdhv.riverwip.model.DwellBucket
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Starters
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.WeekBucketing

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
    settingsRepository: SettingsRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val allItems: StateFlow<List<Item>> = itemRepository.observeItemsForEnabledSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** The standing region + topics filter (set on the globe, persisted). */
    val filter: StateFlow<ReaderFilter> = settingsRepository.observeFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReaderFilter())

    /**
     * The stand's stream: enabled sources, then the standing filter. Region
     * matches a source's declared starter region (URL/OPML additions carry no
     * declared region and count as global); topics match the item's dominant
     * topic.
     */
    val items: StateFlow<List<Item>> = combine(allItems, filter) { list, f ->
        list.filter { item ->
            f.includesSource(Starters.regionBySourceId[item.sourceId])
                && f.matchesTopic(Classifier.dominantTopic(item.topics))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** The honest denominator for this surface's copy. */
    val enabledSourceCount: StateFlow<Int> = sourceRepository.observeEnabledCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    /** Source id → title, for the "Author | Source" bylines (owner's Stand/Paper mocks). */
    val sourceTitles: StateFlow<Map<String, String>> = sourceRepository.observeSources()
        .map { list -> list.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * Today's supply compressed into the day bar (the loom, folded flat):
     * per-topic shares of everything that flowed today from the region's
     * source-set. Topics are never filtered out of the bar — supply is the
     * subject.
     */
    val todayMix: StateFlow<List<Pair<Topic, Double>>> = combine(allItems, filter) { list, f ->
        val dayStart = WeekBucketing.periodStart(clock(), periodDays = 1)
        val counts = HashMap<String, Int>()
        for (item in list) {
            if (item.publishedAt < dayStart) continue
            if (!f.includesSource(Starters.regionBySourceId[item.sourceId])) continue
            counts.merge(Classifier.dominantTopic(item.topics).key, 1, Int::plus)
        }
        DayLoomLayout.dayMix(counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    /** True while a fetch is in flight, so the UI can show progress instead of a bare empty state. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _lastRefresh = MutableStateFlow<RefreshResult?>(null)
    val lastRefresh: StateFlow<RefreshResult?> = _lastRefresh

    init {
        // Fetch once, automatically, the first time the reader is opened with
        // sources added but nothing yet ingested — so adding sources and coming
        // back to the stand "just works" instead of waiting on the background
        // cadence. `.first { it }` suspends until the condition first holds.
        viewModelScope.launch {
            combine(enabledSourceCount, allItems) { count, list -> count > 0 && list.isEmpty() }
                .first { it }
            if (_lastRefresh.value == null) refresh()
        }
    }

    /**
     * Fetch every enabled source now, ingest, and roll the aggregates forward —
     * the same work the background FetchWorker does, but on demand with visible
     * progress. Per-source failures are isolated.
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
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(
                itemRepository,
                sourceRepository,
                articleRepository,
                readEventRepository,
                weeklyAggregateRepository,
                settingsRepository,
            ) as T
    }
}
