package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImageSummariesUseCase
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testTask
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuadrantReadStateTest : MainDispatcherRule() {
    @Test
    fun classicListTaskFailureRecoversWhileDetailRemainsSubscribed() = runTest(dispatcher) {
        assertTaskFailureRecovers(DetailMode.CLASSIC_LIST)
    }

    @Test
    fun classicBoardTaskRetryDoesNotStartCategoryReads() = runTest(dispatcher) {
        assertTaskFailureRecovers(DetailMode.CLASSIC_BOARD)
    }

    @Test
    fun reimaginedTaskRetryDoesNotStartCategoryReads() = runTest(dispatcher) {
        assertTaskFailureRecovers(DetailMode.REIMAGINED)
    }

    @Test
    fun classicListCategoryFailureRetainsRowsAndNamesUntilRequiredRetryCompletes() = runTest(dispatcher) {
        val task = testTask(priority = TaskPriority.HIGH)
        val category = testCategory(id = task.categoryId, name = "Original")
        val categoryFailure = CompletableDeferred<Unit>()
        val finishRetry = CompletableDeferred<Unit>()
        val subscriptions = AtomicInteger()
        val categories = object : CategoryRepository by FakeCategoryRepository() {
            override fun getAllCategories() = flow {
                if (subscriptions.incrementAndGet() == 1) {
                    emit(listOf(category))
                    categoryFailure.await()
                    error("Injected category read failure")
                }
                finishRetry.await()
                emit(listOf(category.copy(name = "Recovered")))
                awaitCancellation()
            }
        }
        val viewModel = matrix(FakeTaskRepository(listOf(task)), categories)
        val rows = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.categorizedTasks.collect() }
        val names = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.categoryNames.collect() }
        try {
            viewModel.classicQuadrantListLoadState.test {
                awaitState(MatrixLoadState.READY)
                viewModel.categorizedTasks.first { it.flatMap { group -> group.tasks } == listOf(task) }
                viewModel.categoryNames.first { it[category.id] == "Original" }

                categoryFailure.complete(Unit)
                awaitState(MatrixLoadState.ERROR)
                assertEquals(MatrixLoadState.READY, viewModel.loadState.value)
                assertTrue(viewModel.categoryNamesReadFailed.value)
                assertEquals(listOf(task), viewModel.categorizedTasks.value.flatMap { it.tasks })
                assertEquals("Original", viewModel.categoryNames.value[category.id])

                viewModel.retryLoad()
                awaitState(MatrixLoadState.LOADING)
                viewModel.loadState.first { it == MatrixLoadState.READY }
                assertEquals(MatrixLoadState.LOADING, viewModel.classicQuadrantListLoadState.value)
                assertEquals("Original", viewModel.categoryNames.value[category.id])
                finishRetry.complete(Unit)
                awaitState(MatrixLoadState.READY)
                viewModel.categoryNames.first { it[category.id] == "Recovered" }
                assertFalse(viewModel.categoryNamesReadFailed.value)
                assertEquals(2, subscriptions.get())
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            rows.cancel()
            names.cancel()
            viewModel.viewModelScope.cancel()
        }
    }

    private suspend fun TestScope.assertTaskFailureRecovers(mode: DetailMode) {
        val task = testTask(priority = TaskPriority.HIGH)
        val recovered = task.copy(title = "Recovered", pbId = "remote", isSynced = true)
        val failure = CompletableDeferred<Unit>()
        val taskSubscriptions = AtomicInteger()
        val categorySubscriptions = AtomicInteger()
        val repository = object : TaskRepository by FakeTaskRepository() {
            override fun getAllTasks() = flow {
                if (taskSubscriptions.incrementAndGet() == 1) {
                    emit(listOf(task))
                    failure.await()
                    error("Injected task read failure")
                }
                emit(listOf(recovered))
                awaitCancellation()
            }
        }
        val categories = object : CategoryRepository by FakeCategoryRepository() {
            override fun getAllCategories() = flow<List<Category>> {
                categorySubscriptions.incrementAndGet()
                emit(listOf(testCategory(id = task.categoryId)))
                awaitCancellation()
            }
        }
        val viewModel = matrix(repository, categories)
        if (mode == DetailMode.CLASSIC_BOARD) viewModel.setViewMode(TaskListViewMode.BOARD)
        val detailRows = when (mode) {
            DetailMode.CLASSIC_LIST -> viewModel.categorizedTasks.map { groups -> groups.flatMap { it.tasks } }
            DetailMode.CLASSIC_BOARD -> viewModel.tasksByStatus.map { groups -> groups.values.flatten() }
            DetailMode.REIMAGINED -> viewModel.tasksByPriority.map { it[TaskPriority.HIGH].orEmpty() }
        }
        val rows = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detailRows.collect() }
        val names = if (mode == DetailMode.CLASSIC_LIST) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.categoryNames.collect() }
        } else null
        val status = if (mode == DetailMode.CLASSIC_LIST) viewModel.classicQuadrantListLoadState else viewModel.loadState
        try {
            status.test {
                awaitState(MatrixLoadState.READY)
                assertEquals(listOf(task), detailRows.first { it.isNotEmpty() })
                failure.complete(Unit)
                awaitState(MatrixLoadState.ERROR)
                assertEquals(listOf(task), detailRows.first())
                assertEquals(if (mode == DetailMode.CLASSIC_LIST) 1 else 0, categorySubscriptions.get())

                viewModel.retryLoad()
                awaitState(MatrixLoadState.READY)
                assertEquals(listOf(recovered), detailRows.first { it == listOf(recovered) })
                assertEquals(2, taskSubscriptions.get())
                assertEquals(if (mode == DetailMode.CLASSIC_LIST) 2 else 0, categorySubscriptions.get())
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            rows.cancel()
            names?.cancel()
            viewModel.viewModelScope.cancel()
        }
    }

    private fun matrix(tasks: TaskRepository, categories: CategoryRepository): MatrixViewModel {
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), tasks)
        return MatrixViewModel(
            observeTasksByPriority = ObserveTasksByPriorityUseCase(tasks),
            observeAllCategories = ObserveAllCategoriesUseCase(categories),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(tasks, scheduler),
            toggleTaskStarredAction = ToggleTaskStarredAction(tasks),
            updateTaskStatusAction = UpdateTaskStatusAction(tasks, scheduler),
            observeTaskImageSummaries = ObserveTaskImageSummariesUseCase(FakeAttachmentRepository()),
            localDaySignal = LocalDaySignal(currentDate = { LocalDate(2026, 10, 29) }),
        )
    }

    private suspend fun ReceiveTurbine<MatrixLoadState>.awaitState(expected: MatrixLoadState) {
        while (awaitItem() != expected) Unit
    }

    private enum class DetailMode { CLASSIC_LIST, CLASSIC_BOARD, REIMAGINED }
}
