package com.udnahc.opentasks.viewmodel

import app.cash.turbine.test
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveCountdownByIdUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import com.udnahc.opentasks.testutil.FakeCategoryRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FormViewModelTest : MainDispatcherRule() {
    @Test
    fun taskFormViewModelLoadsEditTaskAndEmitsSaveEvents() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository(listOf(testTask(id = "task", title = "Old")))
        val categoryRepository = FakeCategoryRepository(listOf(testCategory(id = "inbox", name = "Inbox")))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        val viewModel = TaskFormViewModel(
            observeTaskByIdUseCase = ObserveTaskByIdUseCase(taskRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(categoryRepository),
            addTaskAction = AddTaskAction(taskRepository, scheduler),
            updateTaskAction = UpdateTaskAction(taskRepository, scheduler),
            deleteTaskAction = DeleteTaskAction(taskRepository, scheduler),
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
        assertEquals("Updated", repository.updated.single().title)
        assertEquals("countdown", repository.deleted.single().id)
        assertEquals(3, completed)
    }

    private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        repeat(20) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching item emitted")
    }
}
