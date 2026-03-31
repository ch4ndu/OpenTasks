package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.usecase.countdown.ObserveCountdownByIdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CountdownFormViewModel(
    private val addCountdownAction: AddCountdownAction,
    private val updateCountdownAction: UpdateCountdownAction,
    private val deleteCountdownAction: DeleteCountdownAction,
    private val observeCountdownByIdUseCase: ObserveCountdownByIdUseCase,
) : ViewModel() {

    private val _countdownId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val editCountdown: StateFlow<Countdown?> = _countdownId.flatMapLatest { id ->
        if (id != null) observeCountdownByIdUseCase(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setCountdownId(id: String) {
        _countdownId.value = id
    }

    fun addCountdown(countdown: Countdown, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            addCountdownAction(countdown)
            onComplete()
        }
    }

    fun updateCountdown(countdown: Countdown, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            updateCountdownAction(countdown)
            onComplete()
        }
    }

    fun deleteCountdown(countdown: Countdown, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteCountdownAction(countdown)
            onComplete()
        }
    }
}
