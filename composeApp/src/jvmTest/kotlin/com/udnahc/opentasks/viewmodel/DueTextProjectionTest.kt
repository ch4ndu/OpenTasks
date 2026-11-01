package com.udnahc.opentasks.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.action.settings.SaveTaskListViewModeAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskSortOptionAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImageSummariesUseCase
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskListViewModeUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForFilterUseCase
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DueTextProjectionTest : MainDispatcherRule() {
    private val task = testTask(id = "dated", priority = TaskPriority.HIGH, categoryId = "inbox", deadline = 100L)

    @Test
    fun reimaginedOverviewReusesLabelsButPublishesCurrentTasksAndReformatsForContextOrDeadline() = runTest(dispatcher) {
        val repository = FakeTaskRepository(listOf(task))
        val signal = daySignal()
        val provider = CountingDueTextProvider()
        val viewModel = matrix(repository, signal, provider)
        viewModel.reimaginedState.test {
            awaitMatching { it.dueText[task.id] == "first:100" }
            assertEquals(1, provider.matrixCalls.get())

            val acknowledged = task.copy(pbId = "remote", isSynced = true)
            repository.replaceTasks(listOf(acknowledged))
            val current = awaitMatching { it.projections[TaskPriority.HIGH]?.visibleTasks?.singleOrNull()?.isSynced == true }
            assertEquals(acknowledged, current.projections.getValue(TaskPriority.HIGH).visibleTasks.single())
            assertEquals(1, provider.matrixCalls.get())

            provider.context.set("second")
            signal.refresh()
            awaitMatching { it.dueText[task.id] == "second:100" }
            assertEquals(2, provider.matrixCalls.get())

            repository.replaceTasks(listOf(acknowledged.copy(deadline = 200L)))
            awaitMatching { it.dueText[task.id] == "second:200" }
            assertEquals(3, provider.matrixCalls.get())

            repository.replaceTasks(emptyList())
            assertEquals(emptyMap(), awaitMatching { it.totalCount == 0 }.dueText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun classicOverviewAndSelectedQuadrantRefreshOnSameDayFormattingChange() = runTest(dispatcher) {
        val repository = FakeTaskRepository(listOf(task))
        val signal = daySignal()
        val provider = CountingDueTextProvider()
        val viewModel = matrix(repository, signal, provider)
        viewModel.taskDueTextById.test {
            val overview = this
            awaitMatching { it[task.id] == "first:100" }
            viewModel.selectedPriorityTaskDueTextById.test {
                awaitMatching { it[task.id] == "first:100" }
                provider.context.set("second")
                signal.refresh()
                overview.awaitMatching { it[task.id] == "second:100" }
                awaitMatching { it[task.id] == "second:100" }
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun listAndBoardRefreshContextWithoutTaskOrDayEmission() = runTest(dispatcher) {
        val repository = FakeTaskRepository(listOf(task))
        val signal = daySignal()
        val provider = CountingDueTextProvider()
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val settings = FakeAppSettingsRepository()
        val viewModel = TaskListViewModel(
            TaskListFilter.Category("inbox"), ObserveTasksForFilterUseCase(repository, signal),
            ObserveAllCategoriesUseCase(FakeCategoryRepository()), ToggleTaskCompleteAction(repository, scheduler),
            ToggleTaskStarredAction(repository), ObserveTaskSortOptionUseCase(settings), SaveTaskSortOptionAction(settings),
            ObserveTaskListViewModeUseCase(settings), SaveTaskListViewModeAction(settings),
            UpdateTaskStatusAction(repository, scheduler), ObserveTaskImageSummariesUseCase(FakeAttachmentRepository()),
            signal, provider,
        )
        viewModel.taskDueTextById.test {
            val list = this
            awaitMatching { it[task.id] == "list:first:100" }
            viewModel.boardTaskDueTextById.test {
                awaitMatching { it[task.id] == "first:100" }
                provider.context.set("second")
                signal.refresh()
                list.awaitMatching { it[task.id] == "list:second:100" }
                awaitMatching { it[task.id] == "second:100" }
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun daySignal() = LocalDaySignal(currentDate = { LocalDate(2026, 10, 29) })

    private fun matrix(repository: FakeTaskRepository, signal: LocalDaySignal, provider: TaskDueTextProvider): MatrixViewModel {
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        return MatrixViewModel(
            ObserveTasksByPriorityUseCase(repository), ObserveAllCategoriesUseCase(FakeCategoryRepository()),
            ToggleTaskCompleteAction(repository, scheduler), ToggleTaskStarredAction(repository),
            UpdateTaskStatusAction(repository, scheduler), ObserveTaskImageSummariesUseCase(FakeAttachmentRepository()),
            signal, provider,
        )
    }

    private class CountingDueTextProvider : TaskDueTextProvider {
        val context = AtomicReference("first")
        val matrixCalls = AtomicInteger()
        override val formattingContextKey: String get() = context.get()
        override suspend fun listDueText(task: Task) = "list:${context.get()}:${task.deadline}"
        override suspend fun matrixDueText(task: Task): String {
            matrixCalls.incrementAndGet()
            return "${context.get()}:${task.deadline}"
        }
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        repeat(20) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching projection emitted")
    }
}
