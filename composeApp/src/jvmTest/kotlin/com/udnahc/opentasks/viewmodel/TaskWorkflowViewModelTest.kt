package com.udnahc.opentasks.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.udnahc.opentasks.data.auth.*
import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.TaskMutationResult
import com.udnahc.opentasks.domain.action.task.*
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.task.*
import com.udnahc.opentasks.testutil.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TaskWorkflowViewModelTest : MainDispatcherRule() {
    @Test
    fun doneRejectsDuplicateWhileSuspendedAndRepeatedDoneKeepsHistoryIdempotent() = runTest(dispatcher) {
        val task = testTask(id = "done", categoryId = AppConstants.DEFAULT_INBOX_ID)
        val stored = FakeTaskRepository(listOf(task))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val history = mutableListOf<TaskCompletion>()
        val repository = object : TaskRepository by stored {
            override suspend fun mutateExisting(id: String, completion: ((Task, Task) -> TaskCompletion?)?, transform: (Task) -> Task?): CommittedMutation<TaskMutationResult> {
                entered.complete(Unit)
                release.await()
                return stored.mutateExisting(id, null) { previous ->
                    transform(previous)?.also { next -> completion?.invoke(previous, next)?.let(history::add) }
                }
            }
        }
        val viewModel = weekly(repository)
        viewModel.state.test {
            awaitMatching { !it.loading && it.rows.isNotEmpty() }
            viewModel.pendingIds.test {
                assertTrue(awaitItem().isEmpty())
                viewModel.complete(task)
                awaitMatching { task.id in it }
                entered.await()
                viewModel.complete(task)
                assertEquals(0, stored.mutateExistingCalls)
                release.complete(Unit)
                awaitMatching { it.isEmpty() }
                assertEquals(TaskStatus.DONE, stored.tasks.single().status)
                assertEquals(1, history.size)
                viewModel.complete(task)
                awaitMatching { task.id in it }
                awaitMatching { it.isEmpty() }
                assertEquals(TaskStatus.DONE, stored.tasks.single().status)
                assertEquals(1, history.size)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun doneClearsPendingAfterFailureAndBoundaryRejectionAndUsesPersistedRecurringOccurrence() = runTest(dispatcher) {
        val stale = testTask(id = "recurring", categoryId = AppConstants.DEFAULT_INBOX_ID)
        val persisted = stale.copy(recurrenceType = RecurrenceType.DAILY, deadline = 5_000L)
        val repository = FakeTaskRepository(listOf(persisted))
        val (executor, account) = boundary()
        val viewModel = weekly(repository, executor)
        viewModel.state.test {
            awaitMatching { !it.loading && it.rows.isNotEmpty() }
            repository.mutationError = IllegalStateException("Injected write failure")
            viewModel.complete(stale)
            viewModel.failure.test {
                awaitMatching { it != null }
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(viewModel.pendingIds.value.isEmpty())
            repository.mutationError = null
            viewModel.completion.taskPendingSeriesChoice.test {
                viewModel.complete(stale)
                awaitMatching { it?.expectedOccurrence == 5_000L }
                viewModel.pendingIds.test {
                    awaitMatching { it.isEmpty() }
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
            account.publishState(AccountSessionState.SignedOut)
            viewModel.complete(stale)
            assertTrue(viewModel.pendingIds.value.isEmpty())
            assertEquals(TaskStatus.TODO, repository.tasks.single().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun bulkBusySurvivesSuspendedMutationAndPublishesPartialResult() = runTest(dispatcher) {
        val first = testTask(id = "first")
        val second = testTask(id = "second")
        val stored = FakeTaskRepository(listOf(first, second))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = object : TaskRepository by stored {
            override suspend fun mutateExisting(id: String, completion: ((Task, Task) -> TaskCompletion?)?, transform: (Task) -> Task?): CommittedMutation<TaskMutationResult> {
                entered.complete(Unit)
                release.await()
                if (id == second.id) error("Injected row failure")
                return stored.mutateExisting(id, completion, transform)
            }
        }
        val categories = FakeCategoryRepository()
        val viewModel = BulkTasksViewModel(null, ObserveAllTasksUseCase(repository), ObserveAllCategoriesUseCase(categories), LocalDaySignal(), PlainTaskDueTextProvider, BulkUpdateTasksAction(repository, categories), boundary().first)
        viewModel.state.test {
            awaitMatching { !it.loading && it.rows.size == 2 }
            viewModel.selectAll()
            awaitMatching { it.selected.size == 2 }
            viewModel.apply(BulkTaskChange.Shortlist(true))
            entered.await()
            assertTrue(viewModel.busy.value)
            viewModel.clear()
            viewModel.apply(BulkTaskChange.Shortlist(false))
            assertEquals(setOf(first.id, second.id), viewModel.state.value.selected)
            release.complete(Unit)
            viewModel.result.test {
                val result = checkNotNull(awaitMatching { it != null })
                assertEquals(setOf(first.id), result.succeeded)
                assertEquals(setOf(second.id), result.failed)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.busy.test {
                awaitMatching { !it }
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(stored.tasks.single { it.id == first.id }.isShortlisted)
            awaitMatching { it.selected == setOf(second.id) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun weekly(repository: TaskRepository, executor: AccountBoundaryExecutor = boundary().first): WeeklyReviewViewModel {
        val reminders = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        return WeeklyReviewViewModel(ObserveAllTasksUseCase(repository), ObserveAllCategoriesUseCase(FakeCategoryRepository()), LocalDaySignal(), PlainTaskDueTextProvider, ToggleTaskCompleteAction(repository, reminders), SetTaskShortlistedAction(repository), executor, UpdateTaskStatusAction(repository, reminders))
    }

    private fun boundary(): Pair<AccountBoundaryExecutor, FakeAccountRepository> {
        val gate = MutexAccountMutationGate()
        val binding = CacheBinding("", "", LOCAL_CACHE_OWNER_ID, 0, 1L, CacheMode.LOCAL_ONLY)
        val account = FakeAccountRepository(AccountSessionState.LocalOnly(binding), gate)
        return AccountBoundaryExecutor(account, AccountBoundaryGuard(WidgetFakeAccountStateStore(binding)), gate) to account
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        repeat(20) { val item = awaitItem(); if (predicate(item)) return item }
        error("No matching workflow state")
    }
}
