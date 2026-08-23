package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.PostCommitWarning
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
import org.lighthousegames.logging.logging

private val log = logging("CountdownFormViewModel")

sealed interface CountdownMutationEvent {
    data class Saved(
        val countdown: Countdown,
        val postCommitWarning: PostCommitWarning? = null,
    ) : CountdownMutationEvent

    data class Deleted(
        val countdown: Countdown,
        val postCommitWarning: PostCommitWarning? = null,
    ) : CountdownMutationEvent

    data class Failed(val error: Throwable) : CountdownMutationEvent
}

class CountdownFormViewModel(
    private val addCountdownAction: AddCountdownAction,
    private val updateCountdownAction: UpdateCountdownAction,
    private val deleteCountdownAction: DeleteCountdownAction,
    private val observeCountdownByIdUseCase: ObserveCountdownByIdUseCase,
    localDaySignal: LocalDaySignal,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) : ViewModel() {

    private val _countdownId = MutableStateFlow<String?>(null)
    private val _mutationEvent = MutableStateFlow<CountdownMutationEvent?>(null)
    val mutationEvent: StateFlow<CountdownMutationEvent?> = _mutationEvent
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving
    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        viewModelScope,
        ioDispatcher,
    )

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

    fun consumeMutationEvent(event: CountdownMutationEvent): Boolean =
        _mutationEvent.compareAndSet(expect = event, update = null)

    fun addCountdown(countdown: Countdown) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
        mutationLauncher.launch(
            onBoundaryRejected = { handleMutationFailure(AccountBoundaryRejectedException()) },
            onFailure = ::handleMutationFailure,
        ) {
            try {
                val committed = addCountdownAction(countdown)
                _mutationEvent.value = CountdownMutationEvent.Saved(
                    committed.value,
                    committed.postCommitWarning,
                )
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateCountdown(countdown: Countdown) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
        mutationLauncher.launch(
            onBoundaryRejected = { handleMutationFailure(AccountBoundaryRejectedException()) },
            onFailure = ::handleMutationFailure,
        ) {
            try {
                val committed = updateCountdownAction(countdown)
                _mutationEvent.value = CountdownMutationEvent.Saved(
                    committed.value,
                    committed.postCommitWarning,
                )
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteCountdown(countdown: Countdown) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
        mutationLauncher.launch(
            onBoundaryRejected = { handleMutationFailure(AccountBoundaryRejectedException()) },
            onFailure = ::handleMutationFailure,
        ) {
            try {
                val committed = deleteCountdownAction(countdown)
                _mutationEvent.value = CountdownMutationEvent.Deleted(
                    committed.value,
                    committed.postCommitWarning,
                )
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun handleMutationFailure(error: Throwable) {
        log.e(error) { "Countdown mutation failed" }
        _isSaving.value = false
        _mutationEvent.value = CountdownMutationEvent.Failed(error)
    }
}
