package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
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
    observeAllCategories: ObserveAllCategoriesUseCase,
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val addTaskAction: AddTaskAction,
    private val addCategoryAction: AddCategoryAction,
) : ViewModel() {

    private val _selectedPriority = MutableStateFlow(TaskPriority.HIGH)

    val tasksByPriority: StateFlow<Map<TaskPriority, List<Task>>> = observeTasksByPriority()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksForSelectedPriority: StateFlow<List<Task>> = observeTasksForPriority(_selectedPriority)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPriority(priority: TaskPriority) { _selectedPriority.value = priority }

    fun toggleComplete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { toggleTaskCompleteAction(task) }
    }

    fun addTask(
        title: String,
        content: String,
        priority: TaskPriority = TaskPriority.NONE,
        deadline: Long? = null,
        notifyBeforeValue: Int = 0,
        notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        categoryId: String = "00000000-0000-0000-0000-000000000001",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            addTaskAction(
                title = title,
                content = content,
                priority = priority,
                deadline = deadline,
                notifyBeforeValue = notifyBeforeValue,
                notifyBeforeUnit = notifyBeforeUnit,
                recurrenceType = recurrenceType,
                categoryId = categoryId,
            )
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) { addCategoryAction(name) }
    }
}
