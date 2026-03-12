package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    private val repository: TaskRepository
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(
        title: String,
        content: String,
        deadline: Long? = null,
        notifyBeforeValue: Int = 0,
        notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int = 0,
        isUrgent: Boolean = false,
        isImportant: Boolean = false
    ) {
        val now = utcNow()
        viewModelScope.launch {
            repository.insert(
                Task(
                    title = title,
                    content = content,
                    deadline = deadline,
                    notifyBeforeValue = notifyBeforeValue,
                    notifyBeforeUnit = notifyBeforeUnit,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    isUrgent = isUrgent,
                    isImportant = isImportant,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.update(task.copy(updatedAt = utcNow()))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.delete(task)
        }
    }
}
