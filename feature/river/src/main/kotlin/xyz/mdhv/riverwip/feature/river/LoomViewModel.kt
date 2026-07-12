package xyz.mdhv.riverwip.feature.river

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
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.WeekBucketing
import xyz.mdhv.riverwip.model.WeeklyAggregate

/**
 * The day loom's state: day-grained aggregates (computed on demand — the
 * persisted roll-up stays weekly per the brief), the selected day, and the
 * header facts (source count, region). The loom always shows the full supply
 * from the enabled source-set; the standing topic filter never hides supply —
 * omission is the subject, not something to filter away.
 */
class LoomViewModel(
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
    sourceRepository: SourceRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _days = MutableStateFlow<List<WeeklyAggregate>>(emptyList())
    /** One aggregate per day with any data, ascending by day. */
    val days: StateFlow<List<WeeklyAggregate>> = _days

    val enabledSourceCount: StateFlow<Int> = sourceRepository.observeEnabledCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    val filter: StateFlow<ReaderFilter> = settingsRepository.observeFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReaderFilter())

    /** Selected day start (millis). Null = the latest day with data (today, once anything flowed). */
    var selectedDayStart: Long? by mutableStateOf(null)
        private set

    var showDatePicker: Boolean by mutableStateOf(false)
        private set

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _days.value = weeklyAggregateRepository.dailyAggregates() }
    }

    fun selectDay(dayStartMillis: Long) {
        selectedDayStart = WeekBucketing.periodStart(dayStartMillis, periodDays = 1)
        showDatePicker = false
    }

    /**
     * Explicit day navigation (owner's #8: a tap-to-open month grid wasn't
     * discoverable as "date navigation"). Steps by whole calendar days,
     * bounded so it can never move past today.
     */
    fun stepDay(deltaDays: Int, days: List<WeeklyAggregate>) {
        val today = WeekBucketing.periodStart(System.currentTimeMillis(), periodDays = 1)
        val current = selectedDayStart ?: days.lastOrNull()?.weekStart ?: today
        val stepped = WeekBucketing.periodStart(
            current + deltaDays * MILLIS_PER_DAY,
            periodDays = 1,
        )
        selectedDayStart = stepped.coerceAtMost(today)
    }

    /** Whether [stepDay] can still move forward from the currently-shown day. */
    fun canStepForward(days: List<WeeklyAggregate>): Boolean {
        val today = WeekBucketing.periodStart(System.currentTimeMillis(), periodDays = 1)
        val current = selectedDayStart ?: days.lastOrNull()?.weekStart ?: today
        return current < today
    }

    fun setDatePickerVisible(visible: Boolean) {
        showDatePicker = visible
    }

    /** The aggregate to weave: the selected day, or the latest day with data, or an empty day. */
    fun aggregateFor(days: List<WeeklyAggregate>): WeeklyAggregate? {
        val selected = selectedDayStart ?: return days.lastOrNull()
        return days.firstOrNull { it.weekStart == selected }
            ?: WeeklyAggregate(selected, emptyMap(), emptyMap(), emptyMap())
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
    }

    class Factory(
        private val weeklyAggregateRepository: WeeklyAggregateRepository,
        private val sourceRepository: SourceRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoomViewModel(weeklyAggregateRepository, sourceRepository, settingsRepository) as T
    }
}
