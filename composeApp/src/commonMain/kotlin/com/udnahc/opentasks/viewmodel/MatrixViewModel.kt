package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForPriorityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MatrixViewModel(
    observeTasksByPriority: ObserveTasksByPriorityUseCase,
    observeTasksForPriority: ObserveTasksForPriorityUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val toggleTaskStarredAction: ToggleTaskStarredAction,
    private val updateTaskStatusAction: UpdateTaskStatusAction,
) : ViewModel() {

    private val _selectedPriority = MutableStateFlow(TaskPriority.HIGH)
    private val completionHandler = TaskCompletionHandler(toggleTaskCompleteAction, viewModelScope)
    val taskPendingSeriesChoice: StateFlow<Task?> = completionHandler.taskPendingSeriesChoice

    val categoryNames: StateFlow<Map<String, String>> = observeAllCategories()
        .map { cats -> cats.associate { it.id to it.name } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksByPriority: StateFlow<Map<TaskPriority, List<Task>>> = observeTasksByPriority()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksForSelectedPriority: StateFlow<List<Task>> = observeTasksForPriority(_selectedPriority)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasksByStatus: StateFlow<Map<TaskStatus, List<Task>>> =
        tasksForSelectedPriority.map { tasks ->
            TaskStatus.entries.associateWith { status ->
                tasks.filter { it.status == status }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun selectPriority(priority: TaskPriority) { _selectedPriority.value = priority }

    fun toggleComplete(task: Task) = completionHandler.toggleComplete(task)
    fun completeOccurrence() = completionHandler.completeOccurrence()
    fun completeSeries() = completionHandler.completeSeries()
    fun dismissSeriesChoice() = completionHandler.dismissSeriesChoice()

    fun updateTaskStatus(task: Task, newStatus: TaskStatus) {
        viewModelScope.launch(Dispatchers.IO) { updateTaskStatusAction(task, newStatus) }
    }

    fun toggleStar(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { toggleTaskStarredAction(task) }
    }
}
