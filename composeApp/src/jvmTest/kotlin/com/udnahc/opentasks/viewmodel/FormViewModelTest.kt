package com.udnahc.opentasks.viewmodel

import app.cash.turbine.test
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.attachment.AddTaskImageAction
import com.udnahc.opentasks.domain.action.attachment.RemoveTaskImageAction
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.attachment.PendingTaskImageHandoff
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImagesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveCountdownByIdUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FormViewModelTest : MainDispatcherRule() {
    @Test
    fun taskFormViewModelLoadsEditTaskAndEmitsSaveEvents() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository(listOf(testTask(id = "task", title = "Old")))
        val categoryRepository = FakeCategoryRepository(
            listOf(
                testCategory(id = "inbox", name = "Inbox"),
                testCategory(id = "work", name = "Work"),
            )
        )
        val attachmentRepository = FakeAttachmentRepository()
        val attachmentFileStorage = FakeAttachmentFileStorage()
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        val viewModel = TaskFormViewModel(
            observeTaskByIdUseCase = ObserveTaskByIdUseCase(taskRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(categoryRepository),
            addTaskAction = AddTaskAction(taskRepository, scheduler),
            updateTaskAction = UpdateTaskAction(taskRepository, scheduler),
            deleteTaskAction = DeleteTaskAction(taskRepository, attachmentFileStorage, scheduler, mutationGate = MutexAccountMutationGate()),
            observeTaskImagesUseCase = ObserveTaskImagesUseCase(attachmentRepository),
            addTaskImageAction = AddTaskImageAction(attachmentRepository, attachmentFileStorage, MutexAccountMutationGate()),
            removeTaskImageAction = RemoveTaskImageAction(attachmentRepository, attachmentFileStorage, MutexAccountMutationGate()),
            addCategoryAction = AddCategoryAction(categoryRepository),
            ioDispatcher = dispatcher,
        )

        viewModel.setTaskId("task")
        viewModel.editTask.test {
            assertEquals("task", awaitMatching { it?.id == "task" }?.id)
            cancelAndIgnoreRemainingEvents()
        }

        val formData = TaskFormData(
            title = "New",
            content = "Body",
            priority = TaskPriority.MEDIUM,
            categoryId = "inbox",
        )
        viewModel.saveEvent.test {
            assertEquals(null, awaitItem())
            viewModel.saveNewTask(formData)
            advanceUntilIdle()
            assertEquals(formData, (awaitItem() as TaskFormSaveEvent.Saved).formData)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("New", taskRepository.inserted.single().title)

        viewModel.addCategory("Projects")
        viewModel.deleteTask("task")
        advanceUntilIdle()
        assertEquals("Projects", categoryRepository.inserted.single().name)
        assertTrue(taskRepository.updated.last().isDeleted)

        viewModel.setCategorySearchQuery("wo")
        viewModel.filteredCategories.test {
            assertEquals(listOf("work"), awaitMatching { it.map { category -> category.id } == listOf("work") }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskFormViewModelKeepsFailedImagesPendingAfterNewTaskPartialSuccess() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val attachmentRepository = FakeAttachmentRepository()
        val viewModel = taskFormViewModel(
            taskRepository = taskRepository,
            attachmentRepository = attachmentRepository,
        )
        val goodImage = PickedImage("good.jpg", ByteArray(16), id = "good")
        val largeImage = PickedImage(
            "large.jpg",
            ByteArray(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt() + 1),
            id = "large",
        )
        viewModel.addPendingImage(goodImage)
        viewModel.addPendingImage(largeImage)

        viewModel.saveEvent.test {
            assertEquals(null, awaitItem())
            viewModel.saveNewTask(TaskFormData(title = "New", content = ""))
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is TaskFormSaveEvent.TaskCreatedWithImageError)
            assertEquals(taskRepository.inserted.single().id, event.taskId)
            assertEquals(listOf("large"), event.failedImages.map { it.id })
            assertEquals(1, attachmentRepository.inserted.size)
            assertEquals(listOf("large"), viewModel.pendingImages.value.map { it.id })
            assertFalse(viewModel.isSaving.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskFormViewModelTransfersFailedCreateImagesToEditTaskOnce() = runTest(dispatcher) {
        val handoff = PendingTaskImageHandoff()
        val taskRepository = FakeTaskRepository()
        val attachmentRepository = FakeAttachmentRepository()
        val createViewModel = taskFormViewModel(
            taskRepository = taskRepository,
            attachmentRepository = attachmentRepository,
            pendingTaskImageHandoff = handoff,
        )
        val failedImage = PickedImage(
            "large.jpg",
            ByteArray(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt() + 1),
            id = "large",
        )
        createViewModel.addPendingImage(failedImage)

        lateinit var taskId: String
        createViewModel.saveEvent.test {
            assertEquals(null, awaitItem())
            createViewModel.saveNewTask(TaskFormData(title = "New", content = ""))
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is TaskFormSaveEvent.TaskCreatedWithImageError)
            taskId = event.taskId
            cancelAndIgnoreRemainingEvents()
        }

        val firstEditViewModel = taskFormViewModel(
            taskRepository = taskRepository,
            attachmentRepository = attachmentRepository,
            pendingTaskImageHandoff = handoff,
        )
        firstEditViewModel.setTaskId(taskId)
        advanceUntilIdle()

        assertEquals(listOf("large"), firstEditViewModel.pendingImages.value.map { it.id })

        val secondEditViewModel = taskFormViewModel(
            taskRepository = taskRepository,
            attachmentRepository = attachmentRepository,
            pendingTaskImageHandoff = handoff,
        )
        secondEditViewModel.setTaskId(taskId)
        advanceUntilIdle()

        assertTrue(secondEditViewModel.pendingImages.value.isEmpty())
    }

    @Test
    fun taskFormViewModelKeepsFailedImagesPendingAfterEditPartialSuccess() = runTest(dispatcher) {
        val existingTask = testTask(id = "task", title = "Old")
        val taskRepository = FakeTaskRepository(listOf(existingTask))
        val attachmentRepository = FakeAttachmentRepository()
        val viewModel = taskFormViewModel(
            taskRepository = taskRepository,
            attachmentRepository = attachmentRepository,
        )
        val goodImage = PickedImage("good.jpg", ByteArray(16), id = "good")
        val largeImage = PickedImage(
            "large.jpg",
            ByteArray(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt() + 1),
            id = "large",
        )
        viewModel.addPendingImage(goodImage)
        viewModel.addPendingImage(largeImage)

        viewModel.saveEvent.test {
            assertEquals(null, awaitItem())
            viewModel.saveExistingTask(existingTask.id, TaskFormData(title = "Updated", content = ""))
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is TaskFormSaveEvent.ImagesFailed)
            assertEquals(listOf("large"), event.failedImages.map { it.id })
            assertEquals("Updated", taskRepository.updated.single().title)
            assertEquals(1, attachmentRepository.inserted.size)
            assertEquals(listOf("large"), viewModel.pendingImages.value.map { it.id })
            assertFalse(viewModel.isSaving.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskFormViewModelIgnoresDuplicateSavesWhileInFlight() = runTest(dispatcher) {
        val createTaskRepository = FakeTaskRepository()
        val createViewModel = taskFormViewModel(taskRepository = createTaskRepository)
        val createFormData = TaskFormData(title = "New", content = "")

        createViewModel.saveEvent.test {
            assertEquals(null, awaitItem())
            createViewModel.saveNewTask(createFormData)
            createViewModel.saveNewTask(createFormData)
            assertTrue(createViewModel.isSaving.value)
            advanceUntilIdle()

            assertEquals(1, createTaskRepository.inserted.size)
            assertFalse(createViewModel.isSaving.value)
            assertTrue(awaitItem() is TaskFormSaveEvent.Saved)
            cancelAndIgnoreRemainingEvents()
        }

        val existingTask = testTask(id = "task", title = "Old")
        val editTaskRepository = FakeTaskRepository(listOf(existingTask))
        val editViewModel = taskFormViewModel(taskRepository = editTaskRepository)
        val editFormData = TaskFormData(title = "Updated", content = "")

        editViewModel.saveEvent.test {
            assertEquals(null, awaitItem())
            editViewModel.saveExistingTask(existingTask.id, editFormData)
            editViewModel.saveExistingTask(existingTask.id, editFormData)
            assertTrue(editViewModel.isSaving.value)
            advanceUntilIdle()

            assertEquals(1, editTaskRepository.updated.size)
            assertFalse(editViewModel.isSaving.value)
            assertTrue(awaitItem() is TaskFormSaveEvent.Saved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskFormSaveResultSurvivesCollectorRecreationAndIsConsumedOnce() = runTest(dispatcher) {
        val viewModel = taskFormViewModel()
        val form = TaskFormData(title = "Durable", content = "")

        viewModel.saveNewTask(form)
        advanceUntilIdle()

        viewModel.saveEvent.test {
            assertEquals(form, (awaitItem() as TaskFormSaveEvent.Saved).formData)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.saveEvent.test {
            assertEquals(form, (awaitItem() as TaskFormSaveEvent.Saved).formData)
            cancelAndIgnoreRemainingEvents()
        }

        val event = viewModel.saveEvent.value
        assertTrue(event is TaskFormSaveEvent.Saved)
        assertTrue(viewModel.consumeSaveEvent(event))
        assertFalse(viewModel.consumeSaveEvent(event))
        assertEquals(null, viewModel.saveEvent.value)

        viewModel.saveEvent.test {
            assertEquals(null, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskFormViewModelRemovesPendingImagesByStableId() = runTest(dispatcher) {
        val viewModel = taskFormViewModel()
        val first = PickedImage("same.jpg", ByteArray(8), id = "first")
        val second = PickedImage("same.jpg", ByteArray(8), id = "second")

        viewModel.addPendingImage(first)
        viewModel.addPendingImage(second)
        viewModel.removePendingImage(first)

        assertEquals(listOf("second"), viewModel.pendingImages.value.map { it.id })
        assertFalse(viewModel.pendingImages.value.any { it.id == "first" })
    }

    @Test
    fun taskFormViewModelDiscardsPendingImages() = runTest(dispatcher) {
        val viewModel = taskFormViewModel()
        viewModel.addPendingImage(PickedImage("first.jpg", ByteArray(8), id = "first"))
        viewModel.addPendingImage(PickedImage("second.jpg", ByteArray(8), id = "second"))

        viewModel.discardPendingImages()

        assertTrue(viewModel.pendingImages.value.isEmpty())
    }

    @Test
    fun taskFormViewModelDiscardsPendingImagesWhenDeletingTask() = runTest(dispatcher) {
        val task = testTask(id = "task")
        val taskRepository = FakeTaskRepository(listOf(task))
        val viewModel = taskFormViewModel(taskRepository = taskRepository)
        viewModel.addPendingImage(PickedImage("first.jpg", ByteArray(8), id = "first"))
        viewModel.addPendingImage(PickedImage("second.jpg", ByteArray(8), id = "second"))

        viewModel.deleteTask(task.id)
        advanceUntilIdle()

        assertTrue(viewModel.pendingImages.value.isEmpty())
        assertTrue(taskRepository.updated.single().isDeleted)
    }

    @Test
    fun recurringFormCompletionKeepsPendingDraftAcrossDismissalFailureRetryAndDuplicateConfirmation() = runTest(dispatcher) {
        val deadline = 1_778_918_400_000L
        val task = testTask(
            id = "recurring-form",
            deadline = deadline,
            recurrenceType = RecurrenceType.DAILY,
        )
        val repository = FakeTaskRepository(listOf(task))
        val viewModel = taskFormViewModel(taskRepository = repository)
        val form = TaskFormData(
            title = "Typed title",
            content = "Typed body",
            deadline = deadline,
            recurrence = RecurrenceType.DAILY,
            status = TaskStatus.DONE,
        )

        viewModel.saveExistingTask(task.id, form)
        advanceUntilIdle()
        assertEquals(PendingFormCompletion(task.id, form, deadline), viewModel.pendingFormCompletion.value)
        assertEquals(form, viewModel.retainedFormDraft.value)
        assertEquals(null, viewModel.saveEvent.value)
        assertTrue(repository.updated.isEmpty())

        viewModel.dismissPendingFormCompletion()
        assertEquals(null, viewModel.pendingFormCompletion.value)
        assertEquals(form, viewModel.retainedFormDraft.value)

        viewModel.saveExistingTask(task.id, form)
        advanceUntilIdle()
        repository.mutationError = IllegalStateException("temporary database failure")
        viewModel.confirmPendingFormOccurrence()
        advanceUntilIdle()
        assertEquals(PendingFormCompletion(task.id, form, deadline), viewModel.pendingFormCompletion.value)
        assertEquals(form, viewModel.retainedFormDraft.value)
        assertTrue(repository.updated.isEmpty())

        repository.mutationError = null
        viewModel.confirmPendingFormOccurrence()
        viewModel.confirmPendingFormOccurrence()
        advanceUntilIdle()
        assertEquals(null, viewModel.pendingFormCompletion.value)
        assertEquals(null, viewModel.retainedFormDraft.value)
        assertEquals(1, repository.updated.size)
        assertEquals(deadline + MILLIS_PER_DAY, repository.tasks.single().deadline)
    }

    @Test
    fun recurringFormCompletionRetainsFailedImagesAndDraftAfterCoreMutation() = runTest(dispatcher) {
        val deadline = 1_778_918_400_000L
        val task = testTask(id = "recurring-image", deadline = deadline, recurrenceType = RecurrenceType.DAILY)
        val repository = FakeTaskRepository(listOf(task))
        val storage = FakeAttachmentFileStorage().apply {
            storePickedImageError = IllegalStateException("image storage unavailable")
        }
        val viewModel = taskFormViewModel(taskRepository = repository, attachmentFileStorage = storage)
        val form = TaskFormData(
            title = "Updated",
            content = "",
            deadline = deadline,
            recurrence = RecurrenceType.DAILY,
            status = TaskStatus.DONE,
        )
        val image = PickedImage("retry.jpg", ByteArray(16), id = "retry")
        viewModel.addPendingImage(image)

        viewModel.saveExistingTask(task.id, form)
        advanceUntilIdle()
        viewModel.confirmPendingFormOccurrence()
        advanceUntilIdle()

        assertEquals(null, viewModel.pendingFormCompletion.value)
        assertEquals(form, viewModel.retainedFormDraft.value)
        assertEquals(listOf(image), viewModel.pendingImages.value)
        assertEquals(deadline + MILLIS_PER_DAY, repository.tasks.single().deadline)
    }

    @Test
    fun missingRecurringFormTaskRetainsTheSubmittedDraftForAResubmission() = runTest(dispatcher) {
        val viewModel = taskFormViewModel()
        val form = TaskFormData(
            title = "Still typed",
            content = "",
            deadline = 1_778_918_400_000L,
            recurrence = RecurrenceType.DAILY,
            status = TaskStatus.DONE,
        )

        viewModel.saveExistingTask("missing", form)
        advanceUntilIdle()

        assertEquals(form, viewModel.retainedFormDraft.value)
        assertEquals(null, viewModel.pendingFormCompletion.value)
        assertFalse(viewModel.isSaving.value)
    }

    @Test
    fun recurringFormCompletionReplaysAcrossCollectorRecreationAndSeriesConfirmationIsIdempotent() = runTest(dispatcher) {
        val deadline = 1_778_918_400_000L
        val task = testTask(id = "series-form", deadline = deadline, recurrenceType = RecurrenceType.DAILY)
        val repository = FakeTaskRepository(listOf(task))
        val viewModel = taskFormViewModel(taskRepository = repository)
        val form = TaskFormData(
            title = "Complete the series",
            content = "",
            deadline = deadline,
            recurrence = RecurrenceType.DAILY,
            status = TaskStatus.DONE,
        )
        val expected = PendingFormCompletion(task.id, form, deadline)

        viewModel.saveExistingTask(task.id, form)
        advanceUntilIdle()

        viewModel.pendingFormCompletion.test {
            assertEquals(expected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.pendingFormCompletion.test {
            assertEquals(expected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.confirmPendingFormSeries()
        viewModel.confirmPendingFormSeries()
        advanceUntilIdle()

        assertEquals(null, viewModel.pendingFormCompletion.value)
        assertEquals(TaskStatus.DONE, repository.tasks.single().status)
        assertEquals(1, repository.updated.size)
    }

    @Test
    fun staleRecurringFormCompletionClearsChoiceAndRetainsDraftWithOneResult() = runTest(dispatcher) {
        val deadline = 1_778_918_400_000L
        val task = testTask(id = "stale-form", deadline = deadline, recurrenceType = RecurrenceType.DAILY)
        val repository = FakeTaskRepository(listOf(task))
        val viewModel = taskFormViewModel(taskRepository = repository)
        val form = TaskFormData(
            title = "Keep this draft",
            content = "",
            deadline = deadline,
            recurrence = RecurrenceType.DAILY,
            status = TaskStatus.DONE,
        )

        viewModel.saveExistingTask(task.id, form)
        advanceUntilIdle()
        repository.replaceTasks(listOf(task.copy(deadline = deadline + MILLIS_PER_DAY)))

        viewModel.confirmPendingFormOccurrence()
        advanceUntilIdle()

        assertEquals(null, viewModel.pendingFormCompletion.value)
        assertEquals(form, viewModel.retainedFormDraft.value)
        val event = viewModel.saveEvent.value
        assertTrue(event is TaskFormSaveEvent.StaleOccurrence)
        assertEquals(form, event.formData)
        viewModel.consumeSaveEvent(event)
        assertEquals(null, viewModel.saveEvent.value)
    }

    @Test
    fun countdownFormViewModelLoadsAndWritesCountdowns() = runTest(dispatcher) {
        val countdown = testCountdown(id = "countdown", title = "Launch")
        val repository = FakeCountdownRepository(listOf(countdown))
        val scheduler = ScheduleCountdownRemindersAction(NotificationScheduler(), repository)
        val viewModel = CountdownFormViewModel(
            addCountdownAction = AddCountdownAction(repository, scheduler),
            updateCountdownAction = UpdateCountdownAction(repository, scheduler),
            deleteCountdownAction = DeleteCountdownAction(repository, scheduler),
            observeCountdownByIdUseCase = ObserveCountdownByIdUseCase(repository),
            ioDispatcher = dispatcher,
        )

        viewModel.setCountdownId("countdown")
        viewModel.editCountdown.test {
            assertEquals("Launch", awaitMatching { it?.id == "countdown" }?.title)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.addCountdown(testCountdown(id = "new"))
        viewModel.updateCountdown(countdown.copy(title = "Updated"))
        viewModel.deleteCountdown(countdown)
        advanceUntilIdle()

        assertEquals("new", repository.inserted.single().id)
        assertEquals("Updated", repository.updated.first().title)
        assertEquals("countdown", repository.updated.last().id)
        assertTrue(repository.updated.last().isDeleted)
    }

    private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        repeat(20) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching item emitted")
    }

    private fun taskFormViewModel(
        taskRepository: FakeTaskRepository = FakeTaskRepository(),
        categoryRepository: FakeCategoryRepository = FakeCategoryRepository(),
        attachmentRepository: FakeAttachmentRepository = FakeAttachmentRepository(),
        attachmentFileStorage: FakeAttachmentFileStorage = FakeAttachmentFileStorage(),
        pendingTaskImageHandoff: PendingTaskImageHandoff = PendingTaskImageHandoff(),
    ): TaskFormViewModel {
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        return TaskFormViewModel(
            observeTaskByIdUseCase = ObserveTaskByIdUseCase(taskRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(categoryRepository),
            addTaskAction = AddTaskAction(taskRepository, scheduler),
            updateTaskAction = UpdateTaskAction(taskRepository, scheduler),
            deleteTaskAction = DeleteTaskAction(taskRepository, attachmentFileStorage, scheduler, mutationGate = MutexAccountMutationGate()),
            observeTaskImagesUseCase = ObserveTaskImagesUseCase(attachmentRepository),
            addTaskImageAction = AddTaskImageAction(attachmentRepository, attachmentFileStorage, MutexAccountMutationGate()),
            removeTaskImageAction = RemoveTaskImageAction(attachmentRepository, attachmentFileStorage, MutexAccountMutationGate()),
            addCategoryAction = AddCategoryAction(categoryRepository),
            pendingTaskImageHandoff = pendingTaskImageHandoff,
            ioDispatcher = dispatcher,
        )
    }
}
