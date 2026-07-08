package xyz.mdhv.riverwip.feature.river

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.model.WeeklyAggregate

/**
 * The river's data + selection state (brief §P4). Layout geometry itself is
 * pure and lives in `:core:model`'s `RiverLayout`; this just holds the
 * chronological aggregates and which week (if any) is sliced open.
 */
class RiverViewModel(
    weeklyAggregateRepository: WeeklyAggregateRepository,
    sourceRepository: SourceRepository,
) : ViewModel() {

    val aggregates: StateFlow<List<WeeklyAggregate>> = weeklyAggregateRepository.observeAggregates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** The honest denominator for this surface's copy. */
    val enabledSourceCount: StateFlow<Int> = sourceRepository.observeEnabledCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    /** Which week column is sliced open for the cross-section panel, if any. */
    var selectedWeekIndex: Int? by mutableStateOf(null)
        private set

    fun selectWeek(index: Int) {
        selectedWeekIndex = index
    }

    fun clearSelection() {
        selectedWeekIndex = null
    }

    class Factory(
        private val weeklyAggregateRepository: WeeklyAggregateRepository,
        private val sourceRepository: SourceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RiverViewModel(weeklyAggregateRepository, sourceRepository) as T
    }
}
