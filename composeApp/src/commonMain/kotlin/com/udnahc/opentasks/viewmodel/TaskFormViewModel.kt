package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.attachment.PendingTaskImageHandoff
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.domain.action.attachment.AddTaskImageAction
import com.udnahc.opentasks.domain.action.attachment.RemoveTaskImageAction
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImagesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("TaskFormViewModel")

sealed class TaskFormSaveEvent {
    data class Saved(val formData: TaskFormData) : TaskFormSaveEvent()
    data class TaskCreatedWithImageError(
        val taskId: String,
        val formData: TaskFormData,
        val failedImages: List<PickedImage>,
        val error: Throwable,
    ) : TaskFormSaveEvent()

    data class ImagesFailed(
        val formData: TaskFormData,
        val failedImages: List<PickedImage>,
        val error: Throwable,
    ) : TaskFormSaveEvent()

    data class Error(val error: Throwable) : TaskFormSaveEvent()
}

class TaskFormViewModel(
    private val observeTaskByIdUseCase: ObserveTaskByIdUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    private val addTaskAction: AddTaskAction,
    private val updateTaskAction: UpdateTaskAction,
    private val deleteTaskAction: DeleteTaskAction,
    private val observeTaskImagesUseCase: ObserveTaskImagesUseCase,
    private val addTaskImageAction: AddTaskImageAction,
    private val removeTaskImageAction: RemoveTaskImageAction,
    private val addCategoryAction: AddCategoryAction,
    private val pendingTaskImageHandoff: PendingTaskImageHandoff = PendingTaskImageHandoff(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _taskId = MutableStateFlow<String?>(null)
    private val _pendingImages = MutableStateFlow<List<PickedImage>>(emptyList())
    private val _saveEvents = MutableSharedFlow<TaskFormSaveEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val saveEvents: SharedFlow<TaskFormSaveEvent> = _saveEvents.asSharedFlow()
    val pendingImages: StateFlow<List<PickedImage>> = _pendingImages
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    @OptIn(ExperimentalCoroutinesApi::class)
    val editTask: StateFlow<Task?> = _taskId
        .flatMapLatest { id ->
            if (id != null) observeTaskByIdUseCase(id) else flowOf(null)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val editTaskImages = _taskId
        .flatMapLatest { id ->
            if (id != null) observeTaskImagesUseCase(id) else flowOf(emptyList())
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        if (_taskId.value == taskId) return
        _taskId.value = taskId
        viewModelScope.launch {
            val images = pendingTaskImageHandoff.take(taskId)
            if (_taskId.value == taskId) {
                _pendingImages.value = images
            }
        }
    }

    fun setCategorySearchQuery(query: String) {
        _categorySearchQuery.value = query
    }

    fun addPendingImage(image: PickedImage) {
        _pendingImages.update { images -> images + image }
    }

    fun removePendingImage(image: PickedImage) {
        _pendingImages.update { images -> images.filterNot { it.id == image.id } }
    }

    fun discardPendingImages() {
        _pendingImages.value = emptyList()
    }

    fun saveNewTask(formData: TaskFormData) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch(ioDispatcher) {
            try {
                val task = addTaskAction(
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
                val imageResult = savePendingImages(task.id)
                if (imageResult.failedImages.isEmpty()) {
                    _saveEvents.emit(TaskFormSaveEvent.Saved(formData))
                } else {
                    pendingTaskImageHandoff.put(task.id, imageResult.failedImages)
                    _saveEvents.emit(
                        TaskFormSaveEvent.TaskCreatedWithImageError(
                            taskId = task.id,
                            formData = formData,
                            failedImages = imageResult.failedImages,
                            error = imageResult.error,
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Failed to save new task" }
                _saveEvents.emit(TaskFormSaveEvent.Error(e))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveExistingTask(
        existingTask: Task,
        formData: TaskFormData
    ) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
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
                val imageResult = savePendingImages(existingTask.id)
                if (imageResult.failedImages.isEmpty()) {
                    _saveEvents.emit(TaskFormSaveEvent.Saved(formData))
                } else {
                    _saveEvents.emit(
                        TaskFormSaveEvent.ImagesFailed(
                            formData = formData,
                            failedImages = imageResult.failedImages,
                            error = imageResult.error,
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Failed to save existing task ${existingTask.id}" }
                _saveEvents.emit(TaskFormSaveEvent.Error(e))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteTask(task: Task) {
        discardPendingImages()
        viewModelScope.launch(ioDispatcher) { deleteTaskAction(task) }
    }

    fun removeTaskImage(attachment: com.udnahc.opentasks.data.model.Attachment) {
        viewModelScope.launch(ioDispatcher) { removeTaskImageAction(attachment) }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(ioDispatcher) { addCategoryAction(name) }
    }

    private fun TaskFormData.notifyBeforeUnit(): NotifyBeforeUnit =
        if (reminderDays > 0) NotifyBeforeUnit.DAYS else NotifyBeforeUnit.NONE

    private suspend fun savePendingImages(taskId: String): ImageSaveResult {
        val pending = _pendingImages.value
        if (pending.isEmpty()) return ImageSaveResult(emptyList(), IllegalStateException("No pending images"))

        val failed = mutableListOf<PickedImage>()
        var firstError: Throwable? = null
        for (image in pending) {
            runCatching { addTaskImageAction(taskId, image) }
                .onFailure { error ->
                    if (firstError == null) firstError = error
                    failed += image
                }
        }
        _pendingImages.value = failed
        return ImageSaveResult(
            failedImages = failed,
            error = firstError ?: IllegalStateException("No image save failure"),
        )
    }

    private data class ImageSaveResult(
        val failedImages: List<PickedImage>,
        val error: Throwable,
    )
}
