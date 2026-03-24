package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForPriorityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MatrixViewModel(
    observeTasksByPriority: ObserveTasksByPriorityUseCase,
    observeTasksForPriority: ObserveTasksForPriorityUseCase,
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
) : ViewModel() {

    private val _selectedPriority = MutableStateFlow(TaskPriority.HIGH)

    val tasksByPriority: StateFlow<Map<TaskPriority, List<Task>>> = observeTasksByPriority()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksForSelectedPriority: StateFlow<List<Task>> = observeTasksForPriority(_selectedPriority)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPriority(priority: TaskPriority) { _selectedPriority.value = priority }

    fun toggleComplete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task) }
    }
}
