package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForCategoryUseCase
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
    observeTasksForCategory: ObserveTasksForCategoryUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val addCategoryAction: AddCategoryAction,
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow("00000000-0000-0000-0000-000000000001")
    val selectedCategoryId: StateFlow<String> = _selectedCategoryId

    val tasksForSelectedCategory: StateFlow<List<Task>> = observeTasksForCategory(_selectedCategoryId)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasksForSelectedCategory: StateFlow<List<Task>> = tasksForSelectedCategory
        .map { tasks -> tasks.filter { !it.isCompleted } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasksForSelectedCategory: StateFlow<List<Task>> = tasksForSelectedCategory
        .map { tasks -> tasks.filter { it.isCompleted } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(categoryId: String) { _selectedCategoryId.value = categoryId }

    fun toggleComplete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task) }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) { addCategoryAction(name) }
    }
}
