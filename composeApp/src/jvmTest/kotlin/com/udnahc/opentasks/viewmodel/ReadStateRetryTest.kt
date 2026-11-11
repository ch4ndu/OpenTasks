package com.udnahc.opentasks.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.udnahc.opentasks.data.auth.*
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskCompletion
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.repository.AttachmentRepository
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.CompletionRepository
import com.udnahc.opentasks.data.repository.NoteRepository
import com.udnahc.opentasks.domain.action.settings.*
import com.udnahc.opentasks.domain.action.task.*
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.domain.usecase.settings.*
import com.udnahc.opentasks.domain.usecase.task.*
import com.udnahc.opentasks.testutil.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.atomic.AtomicInteger
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
    @Test
    fun weeklyFailureAfterReadyRetriesWithoutResettingStep() = runTest(dispatcher) {
        val fail = CompletableDeferred<Unit>()
        val subscriptions = AtomicInteger()
        val task = testTask(id = "weekly", categoryId = AppConstants.DEFAULT_INBOX_ID, priority = TaskPriority.HIGH)
        val repository = object : TaskRepository by FakeTaskRepository(listOf(task)) {
            override fun getAllTasks() = flow {
                val attempt = subscriptions.incrementAndGet()
                emit(listOf(task))
                if (attempt == 1) { fail.await(); error("Injected task failure") }
                awaitCancellation()
            }
        }
        val reminders = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val vm = WeeklyReviewViewModel(ObserveAllTasksUseCase(repository), ObserveAllCategoriesUseCase(FakeCategoryRepository()), LocalDaySignal(), PlainTaskDueTextProvider, ToggleTaskCompleteAction(repository, reminders), SetTaskShortlistedAction(repository), boundary(), UpdateTaskStatusAction(repository, reminders))
        vm.state.test {
            awaitMatching { !it.loading && it.rows.isNotEmpty() }
            vm.selectStep(ReviewStep.IMPORTANT)
            awaitMatching { it.step == ReviewStep.IMPORTANT }
            fail.complete(Unit)
            awaitMatching { it.error }
            vm.complete(task)
            assertTrue(vm.pendingIds.value.isEmpty())
            vm.retryLoad()
            val recovered = awaitMatching { !it.loading && !it.error }
            assertEquals(ReviewStep.IMPORTANT, recovered.step)
            assertEquals(task, recovered.rows.single().task)
            assertEquals(2, subscriptions.get())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun bulkCategoryFailureRetriesWithoutLosingSelection() = runTest(dispatcher) {
        val fail = CompletableDeferred<Unit>()
        val subscriptions = AtomicInteger()
        val task = testTask(id = "bulk")
        val repository = FakeTaskRepository(listOf(task))
        val categories = object : CategoryRepository by FakeCategoryRepository() {
            override fun getAllCategories() = flow {
                val attempt = subscriptions.incrementAndGet()
                emit(emptyList<Category>())
                if (attempt == 1) { fail.await(); error("Injected category failure") }
                awaitCancellation()
            }
        }
        val vm = BulkTasksViewModel(null, ObserveAllTasksUseCase(repository), ObserveAllCategoriesUseCase(categories), LocalDaySignal(), PlainTaskDueTextProvider, BulkUpdateTasksAction(repository, categories), boundary())
        vm.state.test {
            awaitMatching { !it.loading && it.rows.isNotEmpty() }
            vm.toggle(task.id)
            awaitMatching { task.id in it.selected }
            fail.complete(Unit)
            awaitMatching { it.error }
            vm.apply(BulkTaskChange.Shortlist(true))
            assertTrue(!vm.busy.value)
            vm.retryLoad()
            val recovered = awaitMatching { !it.loading && !it.error }
            assertEquals(setOf(task.id), recovered.selected)
            assertEquals(2, subscriptions.get())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noteReadFailureIsNotMissingAndRetryWaitsForSuccessfulNull() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val subscriptions = AtomicInteger()
        val notes = object : NoteRepository by FakeNoteRepository() {
            override fun observeNoteById(id: String) = flow<Note?> {
                if (subscriptions.incrementAndGet() == 1) error("Injected note failure")
                release.await()
                emit(null)
            }
        }
        val vm = NoteTasksViewModel("missing", ObserveNoteByIdUseCase(notes), ObserveAllTasksUseCase(FakeTaskRepository()), ObserveAllCategoriesUseCase(FakeCategoryRepository()), PlainTaskDueTextProvider, LocalDaySignal())
        vm.state.test {
            awaitMatching { it.error }
            vm.retryLoad()
            awaitMatching { it.loading && !it.error }
            release.complete(Unit)
            val ready = awaitMatching { !it.loading && !it.error }
            assertEquals(null, ready.note)
            assertEquals(2, subscriptions.get())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun logbookHistoryAndTaskFailuresBothRecoverPreservingFilters() = runTest(dispatcher) {
        for (historyFails in listOf(true, false)) {
            val subscriptions = AtomicInteger()
            val taskReads = object : TaskRepository by FakeTaskRepository() {
                override fun getAllTasks() = flow<List<Task>> {
                    if (!historyFails && subscriptions.incrementAndGet() == 1) error("Injected task failure")
                    emit(emptyList())
                }
            }
            val history = object : CompletionRepository {
                override fun observeCompletions() = flow<List<TaskCompletion>> {
                    if (historyFails && subscriptions.incrementAndGet() == 1) error("Injected history failure")
                    emit(emptyList())
                }
            }
            val vm = LogbookViewModel(ObserveCompletionsUseCase(history), ObserveAllTasksUseCase(taskReads), LocalDaySignal(), EnglishDateTimeFormatter)
            vm.setPeriod(LogbookPeriod.ALL)
            vm.setCategory("retained")
            vm.state.test {
                awaitMatching { it.error }
                vm.retryLoad()
                val ready = awaitMatching { !it.loading && !it.error }
                assertEquals(LogbookPeriod.ALL, ready.period)
                assertEquals("retained", ready.categoryId)
                assertTrue(ready.rows.isEmpty())
                assertEquals(2, subscriptions.get())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun taskListWaitsForEachRequiredSourceAndRecoversAfterReadyFailure() = runTest(dispatcher) {
        for (source in listOf("tasks", "category", "sort", "mode", "images")) {
            val release = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val fail = CompletableDeferred<Unit>()
            val retryRelease = CompletableDeferred<Unit>()
            val subscriptions = AtomicInteger()
            val task = testTask(id = "list", categoryId = "category")
            val repository = object : TaskRepository by FakeTaskRepository(listOf(task)) {
                override fun getAllTasks() = flow {
                    val attempt = if (source == "tasks") subscriptions.incrementAndGet() else 0
                    if (attempt == 1) { started.complete(Unit); release.await() } else if (attempt > 1) retryRelease.await()
                    emit(listOf(task))
                    if (attempt == 1) { fail.await(); error("Injected task failure") }
                    awaitCancellation()
                }
            }
            val categories = object : CategoryRepository by FakeCategoryRepository() {
                override fun getAllCategories() = flow {
                    val attempt = if (source == "category") subscriptions.incrementAndGet() else 0
                    if (attempt == 1) { started.complete(Unit); release.await() } else if (attempt > 1) retryRelease.await()
                    emit(listOf(testCategory(id = "category", name = "Retained title")))
                    if (attempt == 1) { fail.await(); error("Injected title failure") }
                    awaitCancellation()
                }
            }
            val settings = object : AppSettingsRepository by FakeAppSettingsRepository() {
                override fun observeValue(key: String) = flow<String?> {
                    val required = (source == "sort" && key == AppConstants.SettingsKeys.TASK_LIST_SORT_OPTION) ||
                        (source == "mode" && key == AppConstants.SettingsKeys.TASK_LIST_VIEW_MODE)
                    val attempt = if (required) subscriptions.incrementAndGet() else 0
                    if (attempt == 1) { started.complete(Unit); release.await() } else if (attempt > 1) retryRelease.await()
                    emit(null)
                    if (attempt == 1) { fail.await(); error("Injected preference failure") }
                    awaitCancellation()
                }
            }
            val attachments = object : AttachmentRepository by FakeAttachmentRepository() {
                override fun observeTaskImageSummaries() = flow<List<AttachmentSummary>> {
                    val attempt = if (source == "images") subscriptions.incrementAndGet() else 0
                    if (attempt == 1) { started.complete(Unit); release.await() } else if (attempt > 1) retryRelease.await()
                    emit(emptyList())
                    if (attempt == 1) { fail.await(); error("Injected image failure") }
                    awaitCancellation()
                }
            }
            val vm = taskList(repository, categories, settings, attachments)
            vm.loadState.test {
                awaitMatching { it == TaskListLoadState.LOADING }
                started.await()
                assertEquals(TaskListLoadState.LOADING, vm.loadState.value, source)
                release.complete(Unit)
                awaitMatching { it == TaskListLoadState.READY }
                fail.complete(Unit)
                awaitMatching { it == TaskListLoadState.ERROR }
                assertEquals("Retained title", vm.categoryName.value)
                vm.retryLoad()
                awaitMatching { it == TaskListLoadState.LOADING }
                retryRelease.complete(Unit)
                awaitMatching { it == TaskListLoadState.READY }
                assertEquals(2, subscriptions.get(), source)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun listOnlyFailureDoesNotBlockBoardAndBoardCancelsRetriedAttachmentRead() = runTest(dispatcher) {
        val subscriptions = AtomicInteger()
        val retryStarted = CompletableDeferred<Unit>()
        val retryCancelled = CompletableDeferred<Unit>()
        val attachments = object : AttachmentRepository by FakeAttachmentRepository() {
            override fun observeTaskImageSummaries() = flow<List<AttachmentSummary>> {
                if (subscriptions.incrementAndGet() == 1) error("Injected list-only failure")
                retryStarted.complete(Unit)
                try { awaitCancellation() } finally { retryCancelled.complete(Unit) }
            }
        }
        val categories = object : CategoryRepository by FakeCategoryRepository() {
            override fun getAllCategories() = error("Non-category filter must not read category titles")
        }
        val vm = taskList(FakeTaskRepository(), categories, FakeAppSettingsRepository(), attachments, TaskListFilter.Shortlist)
        vm.loadState.test {
            awaitMatching { it == TaskListLoadState.ERROR }
            vm.setViewMode(TaskListViewMode.BOARD)
            awaitMatching { it == TaskListLoadState.READY }
            assertEquals(1, subscriptions.get())
            vm.setViewMode(TaskListViewMode.LIST)
            retryStarted.await()
            awaitMatching { it == TaskListLoadState.LOADING }
            vm.setViewMode(TaskListViewMode.BOARD)
            awaitMatching { it == TaskListLoadState.READY }
            retryCancelled.await()
            assertEquals(2, subscriptions.get())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun taskList(repository: TaskRepository, categories: CategoryRepository, settings: AppSettingsRepository, attachments: AttachmentRepository, filter: TaskListFilter = TaskListFilter.Category("category")): TaskListViewModel {
        val day = LocalDaySignal()
        val reminders = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        return TaskListViewModel(filter, ObserveTasksForFilterUseCase(repository, day), ObserveAllCategoriesUseCase(categories), ToggleTaskCompleteAction(repository, reminders), ToggleTaskStarredAction(repository), ObserveTaskSortOptionUseCase(settings), SaveTaskSortOptionAction(settings), ObserveTaskListViewModeUseCase(settings), SaveTaskListViewModeAction(settings), UpdateTaskStatusAction(repository, reminders), ObserveTaskImageSummariesUseCase(attachments), day)
    }

    private fun boundary(): AccountBoundaryExecutor {
        val gate = MutexAccountMutationGate()
        val binding = CacheBinding("", "", LOCAL_CACHE_OWNER_ID, 0, 1L, CacheMode.LOCAL_ONLY)
        return AccountBoundaryExecutor(FakeAccountRepository(AccountSessionState.LocalOnly(binding), gate), AccountBoundaryGuard(WidgetFakeAccountStateStore(binding)), gate)
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        repeat(30) { val item = awaitItem(); if (predicate(item)) return item }
        error("No matching read state")
    }

}
