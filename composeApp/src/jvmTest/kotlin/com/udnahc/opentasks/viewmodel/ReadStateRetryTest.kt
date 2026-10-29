package com.udnahc.opentasks.viewmodel

import app.cash.turbine.test
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImageSummariesUseCase
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReadStateRetryTest : MainDispatcherRule() {
    @Test
    fun countdownInitialReadFailureRecoversWithExplicitRetry() = runTest(dispatcher) {
        val countdown = testCountdown(id = "recovered")
        var subscriptions = 0
        val repository = object : CountdownRepository by FakeCountdownRepository() {
            override fun getAllCountdowns() = flow {
                subscriptions += 1
                if (subscriptions == 1) error("Injected read failure")
                emit(listOf(countdown))
            }
        }
        val viewModel = CountdownViewModel(ObserveAllCountdownsUseCase(repository), LocalDaySignal())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.allCountdowns.collect() }

        viewModel.loadState.test {
            while (awaitItem() != CountdownLoadState.ERROR) Unit
            assertTrue(viewModel.allCountdowns.value.isEmpty())
            viewModel.retryLoad()
            while (awaitItem() != CountdownLoadState.READY) Unit
            assertEquals(listOf(countdown), viewModel.allCountdowns.first { it.isNotEmpty() })
            assertEquals(2, subscriptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun matrixInitialReadFailureRecoversWithExplicitRetry() = runTest(dispatcher) {
        val task = testTask(id = "recovered", priority = TaskPriority.HIGH)
        var subscriptions = 0
        val repository = object : TaskRepository by FakeTaskRepository() {
            override fun getAllTasks() = flow {
                subscriptions += 1
                if (subscriptions == 1) error("Injected read failure")
                emit(listOf(task))
            }
        }
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val viewModel = MatrixViewModel(
            observeTasksByPriority = ObserveTasksByPriorityUseCase(repository),
            observeAllCategories = ObserveAllCategoriesUseCase(FakeCategoryRepository()),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(repository, scheduler),
            toggleTaskStarredAction = ToggleTaskStarredAction(repository),
            updateTaskStatusAction = UpdateTaskStatusAction(repository, scheduler),
            observeTaskImageSummaries = ObserveTaskImageSummariesUseCase(FakeAttachmentRepository()),
            localDaySignal = LocalDaySignal(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.priorityProjections.collect() }

        viewModel.loadState.test {
            while (awaitItem() != MatrixLoadState.ERROR) Unit
            assertTrue(viewModel.priorityProjections.value.values.all { it.visibleTasks.isEmpty() })
            viewModel.retryLoad()
            while (awaitItem() != MatrixLoadState.READY) Unit
            assertEquals(2, subscriptions)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.priorityProjections.test {
            var projection = awaitItem()
            while (projection[TaskPriority.HIGH]?.visibleTasks?.isEmpty() != false) projection = awaitItem()
            assertEquals(listOf(task), projection.getValue(TaskPriority.HIGH).visibleTasks)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
