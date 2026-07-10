package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.NotificationDeepLinkEvent
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.formatTimeFromLocalMillis
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.action.task.DismissTaskNotificationAction
import com.udnahc.opentasks.domain.action.task.MarkTaskNotificationDoneAction
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaskNotificationUiState(
    val event: NotificationDeepLinkEvent? = null,
    val task: Task? = null,
    val taskTitle: String = "",
    val notificationTimeText: String = "",
    val dueText: String = "",
    val isBusy: Boolean = false,
)

class TaskNotificationViewModel(
    private val observeTaskById: ObserveTaskByIdUseCase,
    private val markTaskNotificationDoneAction: MarkTaskNotificationDoneAction,
    private val dismissTaskNotificationAction: DismissTaskNotificationAction,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _event = MutableStateFlow<NotificationDeepLinkEvent?>(null)
    private val _isBusy = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val task = _event
        .flatMapLatest { event ->
            if (event != null) observeTaskById(event.eventId) else flowOf(null)
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<TaskNotificationUiState> =
        combine(_event, task, _isBusy) { event, task, isBusy ->
            TaskNotificationUiState(
                event = event,
                task = task,
                taskTitle = task?.title.orEmpty(),
                notificationTimeText = event?.notificationAtUtcMillis.formatUtcDateTime(),
                dueText = task.formatDueText(),
                isBusy = isBusy,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskNotificationUiState())

    fun setNotificationEvent(event: NotificationDeepLinkEvent) {
        _event.value = event
    }

    fun clearNotificationEvent() {
        _event.value = null
    }

    fun markDone(onComplete: () -> Unit) {
        val event = _event.value ?: return
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                withContext(ioDispatcher) {
                    markTaskNotificationDoneAction(
                        taskId = event.eventId,
                        occurrenceDeadlineUtcMillis = event.occurrenceDeadlineUtcMillis,
                    )
                }
                onComplete()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun gotIt(onComplete: () -> Unit) {
        val event = _event.value ?: return
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                withContext(ioDispatcher) {
                    dismissTaskNotificationAction(event.eventId)
                }
                onComplete()
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun Long?.formatUtcDateTime(): String {
        val utcMillis = this ?: return ""
        val localMillis = utcToLocal(utcMillis)
        return "${formatDateShort(localMillis)}, ${formatTimeFromLocalMillis(localMillis)}"
    }

    private fun Task?.formatDueText(): String {
        val task = this ?: return ""
        val deadline = task.deadline ?: return ""
        return if (task.isAllDay) {
            formatDateShort(deadline)
        } else {
            "${formatDateShort(deadline)}, ${formatTimeFromLocalMillis(deadline)}"
        }
    }
}
