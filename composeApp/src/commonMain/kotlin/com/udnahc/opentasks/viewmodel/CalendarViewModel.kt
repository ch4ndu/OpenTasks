package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(
    observeAllTasks: ObserveAllTasksUseCase,
    observeTasksByDay: ObserveTasksByDayUseCase,
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasksByDay: StateFlow<Map<Long, List<Task>>> = observeTasksByDay()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleComplete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task) }
    }
}
