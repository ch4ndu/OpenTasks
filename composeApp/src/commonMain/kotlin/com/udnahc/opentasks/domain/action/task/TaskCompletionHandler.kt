package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskCompletionChoice(
    val taskId: String,
    val expectedOccurrence: Long,
)

class TaskCompletionHandler(
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val scope: CoroutineScope,
) {
    private val _taskPendingSeriesChoice = MutableStateFlow<TaskCompletionChoice?>(null)
    val taskPendingSeriesChoice: StateFlow<TaskCompletionChoice?> = _taskPendingSeriesChoice.asStateFlow()

    fun toggleComplete(
        taskId: String,
        status: TaskStatus,
        recurrenceType: RecurrenceType,
        occurrenceDeadlineLocalMillis: Long?,
    ) {
        if (status != TaskStatus.DONE && recurrenceType != RecurrenceType.NONE && occurrenceDeadlineLocalMillis != null) {
            _taskPendingSeriesChoice.value = TaskCompletionChoice(taskId, occurrenceDeadlineLocalMillis)
        } else {
            scope.launch(Dispatchers.IO) { toggleTaskCompleteAction(taskId) }
        }
    }

    fun completeOccurrence() {
        val pending = _taskPendingSeriesChoice.value ?: return
        _taskPendingSeriesChoice.value = null
        scope.launch(Dispatchers.IO) {
            toggleTaskCompleteAction(
                pending.taskId,
                occurrenceDeadlineLocalMillis = pending.expectedOccurrence,
            )
        }
    }

    fun completeSeries() {
        val pending = _taskPendingSeriesChoice.value ?: return
        _taskPendingSeriesChoice.value = null
        scope.launch(Dispatchers.IO) {
            toggleTaskCompleteAction(
                pending.taskId,
                completeSeries = true,
                occurrenceDeadlineLocalMillis = pending.expectedOccurrence,
            )
        }
    }

    fun dismissSeriesChoice() {
        _taskPendingSeriesChoice.value = null
    }
}
