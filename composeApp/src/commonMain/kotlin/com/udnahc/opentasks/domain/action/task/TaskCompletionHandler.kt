package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TaskCompletionHandler(
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val scope: CoroutineScope,
) {
    private val _taskPendingSeriesChoice = MutableStateFlow<Task?>(null)
    val taskPendingSeriesChoice: StateFlow<Task?> = _taskPendingSeriesChoice.asStateFlow()

    fun toggleComplete(task: Task) {
        if (task.status != TaskStatus.DONE && task.recurrenceType != RecurrenceType.NONE && task.deadline != null) {
            _taskPendingSeriesChoice.value = task
        } else {
            scope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task) }
        }
    }

    fun completeOccurrence() {
        val task = _taskPendingSeriesChoice.value ?: return
        _taskPendingSeriesChoice.value = null
        scope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task, completeSeries = false) }
    }

    fun completeSeries() {
        val task = _taskPendingSeriesChoice.value ?: return
        _taskPendingSeriesChoice.value = null
        scope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task, completeSeries = true) }
    }

    fun dismissSeriesChoice() {
        _taskPendingSeriesChoice.value = null
    }
}
