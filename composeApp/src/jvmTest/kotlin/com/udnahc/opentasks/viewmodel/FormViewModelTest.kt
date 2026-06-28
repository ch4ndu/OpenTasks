package com.udnahc.opentasks.viewmodel

import app.cash.turbine.test
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.PendingTaskImageHandoff
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.notification.NotificationScheduler
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
            deleteTaskAction = DeleteTaskAction(taskRepository, attachmentRepository, attachmentFileStorage, scheduler),
            observeTaskImagesUseCase = ObserveTaskImagesUseCase(attachmentRepository),
            addTaskImageAction = AddTaskImageAction(attachmentRepository, attachmentFileStorage),
            removeTaskImageAction = RemoveTaskImageAction(attachmentRepository, attachmentFileStorage),
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
        viewModel.saveEvents.test {
            viewModel.saveNewTask(formData)
            advanceUntilIdle()
            assertEquals(formData, (awaitItem() as TaskFormSaveEvent.Saved).formData)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("New", taskRepository.inserted.single().title)

        viewModel.addCategory("Projects")
        viewModel.deleteTask(testTask(id = "task"))
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

        viewModel.saveEvents.test {
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
        createViewModel.saveEvents.test {
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

        viewModel.saveEvents.test {
            viewModel.saveExistingTask(existingTask, TaskFormData(title = "Updated", content = ""))
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

        createViewModel.saveEvents.test {
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

        editViewModel.saveEvents.test {
            editViewModel.saveExistingTask(existingTask, editFormData)
            editViewModel.saveExistingTask(existingTask, editFormData)
            assertTrue(editViewModel.isSaving.value)
            advanceUntilIdle()

            assertEquals(1, editTaskRepository.updated.size)
            assertFalse(editViewModel.isSaving.value)
            assertTrue(awaitItem() is TaskFormSaveEvent.Saved)
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

        viewModel.deleteTask(task)
        advanceUntilIdle()

        assertTrue(viewModel.pendingImages.value.isEmpty())
        assertTrue(taskRepository.updated.single().isDeleted)
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

        var completed = 0
        viewModel.addCountdown(testCountdown(id = "new")) { completed += 1 }
        viewModel.updateCountdown(countdown.copy(title = "Updated")) { completed += 1 }
        viewModel.deleteCountdown(countdown) { completed += 1 }
        advanceUntilIdle()

        assertEquals("new", repository.inserted.single().id)
        assertEquals("Updated", repository.updated.first().title)
        assertEquals("countdown", repository.updated.last().id)
        assertTrue(repository.updated.last().isDeleted)
        assertEquals(3, completed)
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
            deleteTaskAction = DeleteTaskAction(taskRepository, attachmentRepository, attachmentFileStorage, scheduler),
            observeTaskImagesUseCase = ObserveTaskImagesUseCase(attachmentRepository),
            addTaskImageAction = AddTaskImageAction(attachmentRepository, attachmentFileStorage),
            removeTaskImageAction = RemoveTaskImageAction(attachmentRepository, attachmentFileStorage),
            addCategoryAction = AddCategoryAction(categoryRepository),
            pendingTaskImageHandoff = pendingTaskImageHandoff,
            ioDispatcher = dispatcher,
        )
    }
}
