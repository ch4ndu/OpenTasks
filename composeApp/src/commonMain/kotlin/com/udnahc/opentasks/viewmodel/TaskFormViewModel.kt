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
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskFormViewModel(
    private val observeTaskByIdUseCase: ObserveTaskByIdUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    private val addTaskAction: AddTaskAction,
    private val updateTaskAction: UpdateTaskAction,
    private val deleteTaskAction: DeleteTaskAction,
    private val addCategoryAction: AddCategoryAction,
) : ViewModel() {

    private val _taskId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val editTask: StateFlow<Task?> = _taskId
        .flatMapLatest { id ->
            if (id != null) observeTaskByIdUseCase(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<Category>> = observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTaskId(taskId: String) {
        _taskId.value = taskId
    }

    fun addTask(
        title: String,
        content: String,
        priority: TaskPriority = TaskPriority.NONE,
        deadline: Long? = null,
        endDeadline: Long? = null,
        isAllDay: Boolean = false,
        notifyBeforeValue: Int = 0,
        notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        categoryId: String = "00000000-0000-0000-0000-000000000001",
        location: String = "",
        url: String = "",
        organizer: String = "",
        eventStatus: String = "",
        attendees: String = "",
        durationReminders: String = "",
        dateReminders: String = "",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            addTaskAction(
                title = title,
                content = content,
                priority = priority,
                deadline = deadline,
                endDeadline = endDeadline,
                isAllDay = isAllDay,
                notifyBeforeValue = notifyBeforeValue,
                notifyBeforeUnit = notifyBeforeUnit,
                recurrenceType = recurrenceType,
                categoryId = categoryId,
                location = location,
                url = url,
                organizer = organizer,
                eventStatus = eventStatus,
                attendees = attendees,
                durationReminders = durationReminders,
                dateReminders = dateReminders,
            )
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { updateTaskAction(task) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { deleteTaskAction(task) }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) { addCategoryAction(name) }
    }
}
