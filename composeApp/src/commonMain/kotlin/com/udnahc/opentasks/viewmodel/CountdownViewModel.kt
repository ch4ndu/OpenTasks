package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.countdown.CountdownOccurrence
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.countdown.isCountdownVisibleInList
import com.udnahc.opentasks.domain.usecase.countdown.projectCountdownOccurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CountdownViewModel(
    observeAllCountdowns: ObserveAllCountdownsUseCase,
    private val deleteCountdownAction: DeleteCountdownAction,
    localDaySignal: LocalDaySignal = LocalDaySignal(),
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<CountdownType?>(null) // null = "All"

    val allCountdowns: StateFlow<List<Countdown>> = observeAllCountdowns()
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

    val selectedFilter: StateFlow<CountdownType?> = _selectedFilter

    fun selectFilter(type: CountdownType?) {
        _selectedFilter.value = type
    }

    fun deleteCountdown(countdown: Countdown) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteCountdownAction(countdown)
        }
    }
}
