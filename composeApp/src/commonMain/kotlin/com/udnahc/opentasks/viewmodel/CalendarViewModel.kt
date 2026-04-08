package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.data.model.toCalendarTask
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(
    observeAllTasks: ObserveAllTasksUseCase,
    observeTasksByDay: ObserveTasksByDayUseCase,
    observeAllCountdowns: ObserveAllCountdownsUseCase,
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = combine(
        observeAllTasks(),
        observeAllCountdowns(),
    ) { taskList, countdowns ->
        taskList + countdowns.map { it.toCalendarTask() }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasksByDay: StateFlow<Map<Long, List<Task>>> = combine(
        observeTasksByDay(),
        observeAllCountdowns(),
    ) { tasksMap, countdowns ->
        val merged = tasksMap.toMutableMap()
        for (countdown in countdowns) {
            val dk = dayKey(countdown.targetDate)
            val existing = merged[dk].orEmpty()
            merged[dk] = existing + countdown.toCalendarTask()
        }
        merged
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleComplete(task: Task) {
        if (task.isCountdownItem) return
        viewModelScope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task) }
    }
}
