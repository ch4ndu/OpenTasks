package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.countdown.CountdownOccurrence
import com.udnahc.opentasks.domain.usecase.countdown.ObserveCountdownByIdUseCase
import com.udnahc.opentasks.domain.usecase.countdown.projectCountdownOccurrence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CountdownFormViewModel(
    private val addCountdownAction: AddCountdownAction,
    private val updateCountdownAction: UpdateCountdownAction,
    private val deleteCountdownAction: DeleteCountdownAction,
    private val observeCountdownByIdUseCase: ObserveCountdownByIdUseCase,
    localDaySignal: LocalDaySignal = LocalDaySignal(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _countdownId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val editCountdown: StateFlow<Countdown?> = _countdownId.flatMapLatest { id ->
        if (id != null) observeCountdownByIdUseCase(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val detailCountdown: StateFlow<CountdownOccurrence?> = combine(
        editCountdown,
        localDaySignal.dates,
    ) { countdown, today ->
        countdown?.let { projectCountdownOccurrence(it, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setCountdownId(id: String) {
        _countdownId.value = id
    }

    fun addCountdown(countdown: Countdown) {
        viewModelScope.launch(ioDispatcher) {
            addCountdownAction(countdown)
        }
    }

    fun updateCountdown(countdown: Countdown) {
        viewModelScope.launch(ioDispatcher) {
            updateCountdownAction(countdown)
        }
    }

    fun deleteCountdown(countdown: Countdown) {
        viewModelScope.launch(ioDispatcher) {
            deleteCountdownAction(countdown)
        }
    }
}
