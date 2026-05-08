package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("TaskFormViewModel")

sealed class TaskFormSaveEvent {
    data class Saved(val formData: TaskFormData) : TaskFormSaveEvent()
    data class Error(val error: Throwable) : TaskFormSaveEvent()
}

class TaskFormViewModel(
    private val observeTaskByIdUseCase: ObserveTaskByIdUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    private val addTaskAction: AddTaskAction,
    private val updateTaskAction: UpdateTaskAction,
    private val deleteTaskAction: DeleteTaskAction,
    private val addCategoryAction: AddCategoryAction,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _taskId = MutableStateFlow<String?>(null)
    private val _saveEvents = MutableSharedFlow<TaskFormSaveEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val saveEvents: SharedFlow<TaskFormSaveEvent> = _saveEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val editTask: StateFlow<Task?> = _taskId
        .flatMapLatest { id ->
            if (id != null) observeTaskByIdUseCase(id) else flowOf(null)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<Category>> = observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _categorySearchQuery = MutableStateFlow("")
    val categorySearchQuery: StateFlow<String> = _categorySearchQuery

    val filteredCategories: StateFlow<List<Category>> =
        combine(categories, _categorySearchQuery) { categories, query ->
            if (query.isBlank()) categories
            else categories.filter { it.name.contains(query, ignoreCase = true) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTaskId(taskId: String) {
        _taskId.value = taskId
    }

    fun setCategorySearchQuery(query: String) {
        _categorySearchQuery.value = query
    }

    fun saveNewTask(formData: TaskFormData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                addTaskAction(
                    title = formData.title,
                    content = formData.content,
                    subtasks = formData.subtasks,
                    priority = formData.priority,
                    deadline = formData.deadline,
                    endDeadline = formData.endDeadline,
                    isAllDay = formData.isAllDay,
                    notifyBeforeValue = formData.reminderDays,
                    notifyBeforeUnit = formData.notifyBeforeUnit(),
                    recurrenceType = formData.recurrence,
                    categoryId = formData.categoryId,
                    section = formData.section,
                    location = formData.location,
                    url = formData.url,
                    organizer = formData.organizer,
                    eventStatus = formData.eventStatus,
                    attendees = formData.attendees,
                    durationReminders = formData.durationReminders,
                    dateReminders = formData.dateReminders,
                )
                _saveEvents.emit(TaskFormSaveEvent.Saved(formData))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Failed to save new task" }
                _saveEvents.emit(TaskFormSaveEvent.Error(e))
            }
        }
    }

    fun saveExistingTask(existingTask: Task, formData: TaskFormData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                updateTaskAction(
                    existingTask.copy(
                        title = formData.title,
                        content = formData.content,
                        subtasks = formData.subtasks,
                        priority = formData.priority,
                        deadline = formData.deadline,
                        endDeadline = formData.endDeadline,
                        isAllDay = formData.isAllDay,
                        notifyBeforeValue = formData.reminderDays,
                        notifyBeforeUnit = formData.notifyBeforeUnit(),
                        recurrenceType = formData.recurrence,
                        categoryId = formData.categoryId,
                        section = formData.section,
                        status = formData.status,
                        location = formData.location,
                        url = formData.url,
                        organizer = formData.organizer,
                        eventStatus = formData.eventStatus,
                        attendees = formData.attendees,
                        durationReminders = formData.durationReminders,
                        dateReminders = formData.dateReminders,
                    )
                )
                _saveEvents.emit(TaskFormSaveEvent.Saved(formData))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Failed to save existing task ${existingTask.id}" }
                _saveEvents.emit(TaskFormSaveEvent.Error(e))
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch(ioDispatcher) { deleteTaskAction(task) }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(ioDispatcher) { addCategoryAction(name) }
    }

    private fun TaskFormData.notifyBeforeUnit(): NotifyBeforeUnit =
        if (reminderDays > 0) NotifyBeforeUnit.DAYS else NotifyBeforeUnit.NONE
}
