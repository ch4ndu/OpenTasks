package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.data.model.CountingMode
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.countdown.CountdownOccurrence
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.countdown.isCountdownVisibleInList
import com.udnahc.opentasks.domain.usecase.countdown.projectCountdownOccurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class CountdownRelativeLabel { TODAY, SINCE, UNTIL, AGO, LEFT }

data class ReimaginedCountdownItem(
    val occurrence: CountdownOccurrence,
    val amount: String,
    val relativeLabel: CountdownRelativeLabel,
    val dateText: String,
)

enum class CountdownLoadState { LOADING, READY, ERROR }

class CountdownViewModel(
    observeAllCountdowns: ObserveAllCountdownsUseCase,
    localDaySignal: LocalDaySignal,
    private val dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
) : ViewModel() {

    private val readGeneration = MutableStateFlow(0L)
    private val _loadState = MutableStateFlow(CountdownLoadState.LOADING)
    val loadState: StateFlow<CountdownLoadState> = _loadState

    fun retryLoad() { _loadState.value = CountdownLoadState.LOADING; readGeneration.value += 1L }

    private val _selectedFilter = MutableStateFlow<CountdownType?>(null) // null = "All"

    @OptIn(ExperimentalCoroutinesApi::class)
    val allCountdowns: StateFlow<List<Countdown>> = readGeneration.flatMapLatest {
        observeAllCountdowns()
            .onEach { _loadState.value = CountdownLoadState.READY }
            .catch { error ->
                if (error is CancellationException) throw error
                _loadState.value = CountdownLoadState.ERROR
            }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasStoredCountdowns: StateFlow<Boolean> = allCountdowns
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val visibleCountdownItems: StateFlow<List<CountdownOccurrence>> = combine(
        allCountdowns,
        _selectedFilter,
        localDaySignal.dates,
    ) { list, filter, today ->
        list.asSequence()
            .filter { filter == null || it.countdownType == filter }
            .filter { isCountdownVisibleInList(it, today) }
            .map { projectCountdownOccurrence(it, today) }
            .toList()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reimaginedCountdownItems = visibleCountdownItems.map { occurrences ->
        occurrences.sortedWith(compareBy({ abs(it.daysUntil) }, { it.countdown.id })).map { item ->
            val up = item.countdown.countingMode == CountingMode.COUNT_UP
            val days = item.daysUntil
            ReimaginedCountdownItem(
                item, abs(days).toString(),
                when {
                    days == 0 -> CountdownRelativeLabel.TODAY
                    up && days < 0 -> CountdownRelativeLabel.SINCE
                    up -> CountdownRelativeLabel.UNTIL
                    days < 0 -> CountdownRelativeLabel.AGO
                    else -> CountdownRelativeLabel.LEFT
                },
                dateTimeFormatter.formatDateWithYear(item.effectiveTargetDate),
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedFilter: StateFlow<CountdownType?> = _selectedFilter

    fun selectFilter(type: CountdownType?) {
        _selectedFilter.value = type
    }
}
