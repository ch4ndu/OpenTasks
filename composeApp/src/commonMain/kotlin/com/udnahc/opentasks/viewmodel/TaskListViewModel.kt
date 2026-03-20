package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskList
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.tasklist.AddTaskListAction
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForListUseCase
import com.udnahc.opentasks.domain.usecase.tasklist.ObserveAllTaskListsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskListViewModel(
    observeTasksForList: ObserveTasksForListUseCase,
    observeAllTaskLists: ObserveAllTaskListsUseCase,
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val addTaskListAction: AddTaskListAction,
) : ViewModel() {

    private val _selectedListId = MutableStateFlow(1L)
    val selectedListId: StateFlow<Long> = _selectedListId

    val tasksForSelectedList: StateFlow<List<Task>> = observeTasksForList(_selectedListId)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasksForSelectedList: StateFlow<List<Task>> = tasksForSelectedList
        .map { tasks -> tasks.filter { !it.isCompleted } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasksForSelectedList: StateFlow<List<Task>> = tasksForSelectedList
        .map { tasks -> tasks.filter { it.isCompleted } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskLists: StateFlow<List<TaskList>> = observeAllTaskLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectList(listId: Long) { _selectedListId.value = listId }

    fun toggleComplete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task) }
    }

    fun addList(name: String) {
        viewModelScope.launch(Dispatchers.IO) { addTaskListAction(name) }
    }
}
