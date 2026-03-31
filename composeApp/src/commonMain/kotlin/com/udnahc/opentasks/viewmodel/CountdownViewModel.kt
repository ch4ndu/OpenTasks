package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CountdownViewModel(
    observeAllCountdowns: ObserveAllCountdownsUseCase,
    private val deleteCountdownAction: DeleteCountdownAction,
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<CountdownType?>(null) // null = "All"

    val allCountdowns: StateFlow<List<Countdown>> = observeAllCountdowns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCountdowns: StateFlow<List<Countdown>> = combine(
        allCountdowns,
        _selectedFilter,
    ) { list, filter ->
        if (filter == null) list else list.filter { it.countdownType == filter }
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
