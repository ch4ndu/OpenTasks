package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
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
import com.udnahc.opentasks.domain.attachment.PendingTaskImageHandoff
import com.udnahc.opentasks.domain.action.task.FormCompletionScope
import com.udnahc.opentasks.domain.action.task.TaskWriteIntent
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImagesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import com.udnahc.opentasks.data.repository.PostCommitWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("TaskFormViewModel")

sealed class TaskFormSaveEvent {
    data class Saved(
        val formData: TaskFormData,
        val postCommitWarning: PostCommitWarning? = null,
    ) : TaskFormSaveEvent()
    data class TaskCreatedWithImageError(
        val taskId: String,
        val formData: TaskFormData,
        val failedImages: List<PickedImage>,
        val error: Throwable,
        val postCommitWarning: PostCommitWarning? = null,
    ) : TaskFormSaveEvent()

    data class ImagesFailed(
        val formData: TaskFormData,
        val failedImages: List<PickedImage>,
        val error: Throwable,
        val postCommitWarning: PostCommitWarning? = null,
    ) : TaskFormSaveEvent()

    data class Error(val error: Throwable) : TaskFormSaveEvent()
    data class DeleteError(val error: Throwable) : TaskFormSaveEvent()
    data class StaleOccurrence(val formData: TaskFormData) : TaskFormSaveEvent()
    data object Missing : TaskFormSaveEvent()
    data class Deleted(val postCommitWarning: PostCommitWarning? = null) : TaskFormSaveEvent()
}

sealed interface TaskFormDestinationState {
    data object Loading : TaskFormDestinationState
    data class Ready(val task: Task) : TaskFormDestinationState
    data object Missing : TaskFormDestinationState
}

data class PendingFormCompletion(
    val taskId: String,
    val formData: TaskFormData,
    val expectedOccurrence: Long,
)

class TaskFormViewModel(
    private val observeTaskByIdUseCase: ObserveTaskByIdUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    private val addTaskAction: AddTaskAction,
    private val updateTaskAction: UpdateTaskAction,
    private val deleteTaskAction: DeleteTaskAction,
    private val observeTaskImagesUseCase: ObserveTaskImagesUseCase,
    private val addTaskImageAction: AddTaskImageAction,
    private val removeTaskImageAction: RemoveTaskImageAction,
    addCategoryAction: AddCategoryAction,
    private val pendingTaskImageHandoff: PendingTaskImageHandoff = PendingTaskImageHandoff(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) : ViewModel() {

    private val _taskId = MutableStateFlow<String?>(null)
    private val _pendingImages = MutableStateFlow<List<PickedImage>>(emptyList())
    private val _saveEvent = MutableStateFlow<TaskFormSaveEvent?>(null)
    val saveEvent: StateFlow<TaskFormSaveEvent?> = _saveEvent
    val pendingImages: StateFlow<List<PickedImage>> = _pendingImages
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving
    private val _pendingFormCompletion = MutableStateFlow<PendingFormCompletion?>(null)
    val pendingFormCompletion: StateFlow<PendingFormCompletion?> = _pendingFormCompletion
    private val _retainedFormDraft = MutableStateFlow<TaskFormData?>(null)
    val retainedFormDraft: StateFlow<TaskFormData?> = _retainedFormDraft
    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        viewModelScope,
        ioDispatcher,
    )
    private val categoryPicker = CategoryPickerDelegate(
        observeAllCategories,
        addCategoryAction,
        viewModelScope,
        accountBoundaryExecutor,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val destinationState: StateFlow<TaskFormDestinationState> = _taskId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(TaskFormDestinationState.Loading)
            } else {
                observeTaskByIdUseCase(id)
                    .map { task ->
                        task?.let(TaskFormDestinationState::Ready)
                            ?: TaskFormDestinationState.Missing
                    }
                    .onStart { emit(TaskFormDestinationState.Loading) }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TaskFormDestinationState.Loading,
        )

    val editTask: StateFlow<Task?> = destinationState
        .map { state -> (state as? TaskFormDestinationState.Ready)?.task }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val editTaskImages = _taskId
        .flatMapLatest { id ->
            if (id != null) observeTaskImagesUseCase(id) else flowOf(emptyList())
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories = categoryPicker.categories
    val categorySearchQuery = categoryPicker.categorySearchQuery
    val filteredCategories = categoryPicker.filteredCategories

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

    /** Claims and clears the current event so only one collector performs its effect. */
    fun consumeSaveEvent(event: TaskFormSaveEvent): Boolean =
        _saveEvent.compareAndSet(expect = event, update = null)

    fun setCategorySearchQuery(query: String) {
        categoryPicker.setCategorySearchQuery(query)
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
        mutationLauncher.launch(
            onBoundaryRejected = { handleMutationFailure(AccountBoundaryRejectedException()) },
            onFailure = ::handleMutationFailure,
        ) {
            try {
                val committed = addTaskAction(
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
                val task = committed.value
                val imageResult = savePendingImages(task.id)
                if (imageResult.failedImages.isEmpty()) {
                    publishSaveEvent(TaskFormSaveEvent.Saved(formData, committed.postCommitWarning))
                } else {
                    pendingTaskImageHandoff.put(task.id, imageResult.failedImages)
                    publishSaveEvent(
                        TaskFormSaveEvent.TaskCreatedWithImageError(
                            taskId = task.id,
                            formData = formData,
                            failedImages = imageResult.failedImages,
                            error = imageResult.error,
                            postCommitWarning = committed.postCommitWarning,
                        )
                    )
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveExistingTask(taskId: String, formData: TaskFormData) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
        _retainedFormDraft.value = formData
        mutationLauncher.launch(
            onBoundaryRejected = { handleMutationFailure(AccountBoundaryRejectedException()) },
            onFailure = ::handleMutationFailure,
        ) {
            try {
                val committed = updateTaskAction(taskId, TaskWriteIntent.FormUpdate(formData))
                when (val result = committed.value) {
                    is TaskWriteResult.CompletionChoiceRequired -> {
                        _pendingFormCompletion.value = PendingFormCompletion(
                            taskId = taskId,
                            formData = formData,
                            expectedOccurrence = result.expectedOccurrence,
                        )
                    }
                    is TaskWriteResult.Updated -> finishExistingTaskSave(
                        taskId,
                        formData,
                        committed.postCommitWarning,
                    )
                    TaskWriteResult.StaleOccurrence -> publishSaveEvent(TaskFormSaveEvent.StaleOccurrence(formData))
                    TaskWriteResult.Missing -> publishSaveEvent(TaskFormSaveEvent.Missing)
                    TaskWriteResult.NoOp -> publishSaveEvent(TaskFormSaveEvent.Saved(formData, committed.postCommitWarning))
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun confirmPendingFormOccurrence() = confirmPendingFormCompletion(FormCompletionScope.OCCURRENCE)

    fun confirmPendingFormSeries() = confirmPendingFormCompletion(FormCompletionScope.SERIES)

    fun dismissPendingFormCompletion() {
        _pendingFormCompletion.value = null
    }

    private fun confirmPendingFormCompletion(scope: FormCompletionScope) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
        val pending = _pendingFormCompletion.value
        if (pending == null) {
            _isSaving.value = false
            return
        }
        mutationLauncher.launch(
            onBoundaryRejected = { handleMutationFailure(AccountBoundaryRejectedException()) },
            onFailure = ::handleMutationFailure,
        ) {
            try {
                val committed = updateTaskAction(
                    pending.taskId,
                    TaskWriteIntent.ApplyFormAndComplete(
                        pending.formData,
                        pending.expectedOccurrence,
                        scope,
                    ),
                )
                when (committed.value) {
                    is TaskWriteResult.Updated -> {
                        _pendingFormCompletion.value = null
                        finishExistingTaskSave(
                            pending.taskId,
                            pending.formData,
                            committed.postCommitWarning,
                        )
                    }
                    TaskWriteResult.StaleOccurrence -> {
                        _pendingFormCompletion.value = null
                        publishSaveEvent(TaskFormSaveEvent.StaleOccurrence(pending.formData))
                    }
                    TaskWriteResult.Missing -> {
                        _pendingFormCompletion.value = null
                        publishSaveEvent(TaskFormSaveEvent.Missing)
                    }
                    else -> Unit
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteTask(taskId: String) {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return
        mutationLauncher.launch(
            onBoundaryRejected = { handleDeleteMutationFailure(AccountBoundaryRejectedException()) },
            onFailure = ::handleDeleteMutationFailure,
        ) {
            try {
                val committed = deleteTaskAction(taskId)
                when (committed.value) {
                    is TaskWriteResult.Updated,
                    TaskWriteResult.Missing,
                    -> {
                        discardPendingImages()
                        _pendingFormCompletion.value = null
                        _retainedFormDraft.value = null
                        publishSaveEvent(TaskFormSaveEvent.Deleted(committed.postCommitWarning))
                    }
                    else -> handleDeleteMutationFailure(
                        IllegalStateException("Task could not be deleted"),
                    )
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun removeTaskImage(attachment: com.udnahc.opentasks.data.model.Attachment) {
        mutationLauncher.launch { removeTaskImageAction(attachment) }
    }

    fun addCategory(name: String) {
        categoryPicker.addCategory(name)
    }

    private fun TaskFormData.notifyBeforeUnit(): NotifyBeforeUnit =
        if (reminderDays > 0) NotifyBeforeUnit.DAYS else NotifyBeforeUnit.NONE

    private suspend fun savePendingImages(taskId: String): ImageSaveResult {
        val pending = _pendingImages.value
        if (pending.isEmpty()) return ImageSaveResult(emptyList(), IllegalStateException("No pending images"))

        val failed = mutableListOf<PickedImage>()
        var firstError: Throwable? = null
        for (image in pending) {
            currentCoroutineContext().ensureActive()
            try {
                addTaskImageAction(taskId, image)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
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

    private suspend fun finishExistingTaskSave(
        taskId: String,
        formData: TaskFormData,
        postCommitWarning: PostCommitWarning?,
    ) {
        val imageResult = savePendingImages(taskId)
        if (imageResult.failedImages.isEmpty()) {
            _retainedFormDraft.value = null
            publishSaveEvent(TaskFormSaveEvent.Saved(formData, postCommitWarning))
        } else {
            publishSaveEvent(
                TaskFormSaveEvent.ImagesFailed(
                    formData = formData,
                    failedImages = imageResult.failedImages,
                    error = imageResult.error,
                    postCommitWarning = postCommitWarning,
                )
            )
        }
    }

    private data class ImageSaveResult(
        val failedImages: List<PickedImage>,
        val error: Throwable,
    )

    private fun publishSaveEvent(event: TaskFormSaveEvent) {
        _saveEvent.value = event
    }

    private fun handleMutationFailure(error: Throwable) {
        log.e { "Task form mutation failed" }
        _isSaving.value = false
        publishSaveEvent(TaskFormSaveEvent.Error(error))
    }

    private fun handleDeleteMutationFailure(error: Throwable) {
        log.e { "Task form delete failed" }
        _isSaving.value = false
        publishSaveEvent(TaskFormSaveEvent.DeleteError(error))
    }
}
