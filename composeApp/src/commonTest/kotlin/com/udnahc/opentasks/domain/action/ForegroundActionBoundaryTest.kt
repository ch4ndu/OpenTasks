package com.udnahc.opentasks.domain.action

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryGuard
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.AccountSessionFreshness
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.FakeAccountRepository
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.auth.WidgetFakeAccountStateStore
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderRequest
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.action.task.ImportCsvTasksAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.TaskWriteIntent
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import com.udnahc.opentasks.testutil.FakeTagRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.FakeNoteRepository
import com.udnahc.opentasks.testutil.testNote
import com.udnahc.opentasks.testutil.testTask
import com.udnahc.opentasks.viewmodel.ForegroundMutationLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundActionBoundaryTest {
    @Test
    fun taskActionHoldsAccountBoundaryThroughWriteReadAndSchedule() = runTest {
        val fixture = fixture()
        val sharedLocalId = "shared-local-id"
        val repository = FakeTaskRepository(
            listOf(
                testTask(
                    id = sharedLocalId,
                    deadline = FUTURE_UTC_MILLIS,
                    dateReminders = "0",
                ),
            ),
        )
        val events = mutableListOf<String>()
        val scheduler = BlockingReminderScheduler(
            accountId = { fixture.repository.sessionState.value.accountIdOrUnknown() },
            events = events,
        )
        val schedule = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = repository,
            nowUtcMillisProvider = { 0L },
        )
        val action = UpdateTaskAction(
            repository = repository,
            scheduleTaskRemindersAction = schedule,
            accountBoundaryExecutor = fixture.executor,
        )

        val actionJob = async(start = CoroutineStart.UNDISPATCHED) {
            action(sharedLocalId, TaskWriteIntent.ToggleStar)
        }
        scheduler.cancellationEntered.await()

        val transitionJob = async {
            fixture.mutationGate.withExclusive {
                events += "account-b:cancel-source-reminders"
                fixture.repository.publishState(destinationState())
                fixture.stateStore.setBinding(destinationBinding())
                repository.replaceTasks(
                    listOf(testTask(id = sharedLocalId, title = "Account B task")),
                )
            }
        }
        yield()

        assertFalse(transitionJob.isCompleted)
        assertFalse(actionJob.isCompleted)
        assertEquals(1, repository.mutateExistingCalls)
        assertEquals(1, repository.getTaskByIdUtcCalls)

        scheduler.releaseCancellation.complete(Unit)
        actionJob.await()
        transitionJob.await()

        assertEquals(
            listOf(
                "account-a:cancel:$sharedLocalId",
                "account-a:stop:$sharedLocalId",
                "account-a:schedule:$sharedLocalId",
                "account-b:cancel-source-reminders",
            ),
            events,
        )
        assertTrue(repository.updated.single().isStarred)
    }

    @Test
    fun starredActionRejectsAStaleForegroundBoundaryBeforeMutation() = runTest {
        val fixture = fixture()
        val repository = FakeTaskRepository(listOf(testTask(id = "task")))
        fixture.repository.publishState(destinationState())

        assertFailsWith<AccountBoundaryRejectedException> {
            ToggleTaskStarredAction(repository, fixture.executor)("task")
        }
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun categoryActionRejectsAStaleForegroundBoundaryBeforeMutation() = runTest {
        val fixture = fixture()
        val repository = FakeCategoryRepository()
        fixture.repository.publishState(destinationState())

        assertFailsWith<AccountBoundaryRejectedException> {
            AddCategoryAction(repository, fixture.executor)("Work")
        }
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun foregroundMutationLauncherRejectsBoundaryChangesWithoutUncaughtFailure() = runTest {
        val fixture = fixture()
        var writes = 0
        var rejected = 0
        var failures = 0
        val launcher = ForegroundMutationLauncher(
            fixture.executor,
            this,
            StandardTestDispatcher(testScheduler),
        )

        launcher.launch(
            onBoundaryRejected = { rejected += 1 },
            onFailure = { failures += 1 },
        ) { writes += 1 }
        fixture.repository.publishState(destinationState())
        fixture.stateStore.setBinding(destinationBinding())
        advanceUntilIdle()

        assertEquals(0, writes)
        assertEquals(1, rejected)
        assertEquals(0, failures)
    }

    @Test
    fun foregroundMutationLauncherRoutesOrdinaryFailureToCallback() = runTest {
        var failures = 0
        val launcher = ForegroundMutationLauncher(
            null,
            this,
            StandardTestDispatcher(testScheduler),
        )

        launcher.launch(
            onFailure = { failures += 1 },
        ) { error("mutation failed") }
        advanceUntilIdle()

        assertEquals(1, failures)
    }

    @Test
    fun foregroundMutationLauncherRethrowsCancellationWithoutFailureCallbacks() = runTest {
        var rejected = 0
        var failures = 0
        val launcher = ForegroundMutationLauncher(
            null,
            this,
            StandardTestDispatcher(testScheduler),
        )

        launcher.launch(
            onBoundaryRejected = { rejected += 1 },
            onFailure = { failures += 1 },
        ) {
            throw CancellationException("cancelled")
        }
        advanceUntilIdle()

        assertEquals(0, rejected)
        assertEquals(0, failures)
    }

    @Test
    fun countdownActionHoldsAccountBoundaryThroughIosQueueReplacement() = runTest {
        val fixture = fixture()
        val sharedLocalId = "shared-local-id"
        val countdownRepository = FakeCountdownRepository()
        val taskRepository = FakeTaskRepository()
        val events = mutableListOf<String>()
        val scheduler = BlockingReminderScheduler(
            accountId = { fixture.repository.sessionState.value.accountIdOrUnknown() },
            events = events,
            blockCancellation = false,
            blockReplacement = true,
        )
        val scheduleTask = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = taskRepository,
            nowUtcMillisProvider = { 0L },
        )
        val scheduleCountdown = ScheduleCountdownRemindersAction(
            scheduler = scheduler,
            countdownRepository = countdownRepository,
            nowUtcMillisProvider = { 0L },
        )
        val rebuild = RebuildReminderQueueAction(
            taskRepository = taskRepository,
            countdownRepository = countdownRepository,
            scheduleTaskRemindersAction = scheduleTask,
            scheduleCountdownRemindersAction = scheduleCountdown,
            scheduler = scheduler,
            pendingQueueLimit = { 60 },
        )
        val action = AddCountdownAction(
            repository = countdownRepository,
            scheduleCountdownRemindersAction = scheduleCountdown,
            rebuildReminderQueueAction = rebuild,
            accountBoundaryExecutor = fixture.executor,
        )

        val actionJob = async(start = CoroutineStart.UNDISPATCHED) {
            action(
                testCountdown(
                    id = sharedLocalId,
                    targetDate = FUTURE_UTC_MILLIS,
                    reminders = "0",
                ),
            )
        }
        scheduler.replacementEntered.await()

        val transitionJob = async {
            fixture.mutationGate.withExclusive {
                events += "account-b:cancel-source-reminders"
                fixture.repository.publishState(destinationState())
                fixture.stateStore.setBinding(destinationBinding())
                countdownRepository.update(
                    testCountdown(id = sharedLocalId, title = "Account B countdown"),
                )
            }
        }
        yield()

        assertFalse(transitionJob.isCompleted)
        assertFalse(actionJob.isCompleted)
        assertEquals(1, countdownRepository.inserted.size)

        scheduler.releaseReplacement.complete(Unit)
        actionJob.await()
        transitionJob.await()

        assertEquals(
            listOf(
                "account-a:replace:countdown_$sharedLocalId",
                "account-b:cancel-source-reminders",
            ),
            events,
        )
    }

    @Test
    fun boundedAfterRecordChangeUsesFullQueueRebuildInsteadOfDirectScheduling() = runTest {
        val taskRepository = FakeTaskRepository()
        val countdownRepository = FakeCountdownRepository(
            listOf(
                testCountdown(
                    id = "bounded-countdown",
                    targetDate = FUTURE_UTC_MILLIS,
                    reminders = "0",
                ),
            ),
        )
        val events = mutableListOf<String>()
        val scheduler = BlockingReminderScheduler(
            accountId = { "account-a" },
            events = events,
            blockCancellation = false,
        )
        val scheduleTask = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = taskRepository,
            nowUtcMillisProvider = { 0L },
        )
        val scheduleCountdown = ScheduleCountdownRemindersAction(
            scheduler = scheduler,
            countdownRepository = countdownRepository,
            nowUtcMillisProvider = { 0L },
        )
        val rebuild = RebuildReminderQueueAction(
            taskRepository = taskRepository,
            countdownRepository = countdownRepository,
            scheduleTaskRemindersAction = scheduleTask,
            scheduleCountdownRemindersAction = scheduleCountdown,
            scheduler = scheduler,
            pendingQueueLimit = { 60 },
        )
        var directScheduleCalls = 0

        assertEquals(
            null,
            rebuild.afterRecordChangeResult {
                directScheduleCalls += 1
            },
        )
        assertEquals(0, directScheduleCalls)
        assertEquals(
            listOf("account-a:replace:countdown_bounded-countdown"),
            events,
        )
    }

    @Test
    fun staleTaskActionRejectsBeforeAccountBReadsWritesOrReminderSideEffects() = runTest {
        val fixture = fixture()
        val sharedLocalId = "shared-local-id"
        val repository = FakeTaskRepository(listOf(testTask(id = sharedLocalId)))
        val events = mutableListOf<String>()
        val scheduler = BlockingReminderScheduler(
            accountId = { fixture.repository.sessionState.value.accountIdOrUnknown() },
            events = events,
            blockCancellation = false,
        )
        val schedule = ScheduleTaskRemindersAction(scheduler, repository)
        val action = UpdateTaskAction(
            repository = repository,
            scheduleTaskRemindersAction = schedule,
            accountBoundaryExecutor = fixture.executor,
        )
        val gateEntered = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        val gateHolder = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.mutationGate.withExclusive {
                gateEntered.complete(Unit)
                releaseGate.await()
            }
        }
        gateEntered.await()

        val actionJob = async(start = CoroutineStart.UNDISPATCHED) {
            assertFailsWith<AccountBoundaryRejectedException> {
                action(sharedLocalId, TaskWriteIntent.ToggleStar)
            }
        }
        fixture.repository.publishState(destinationState())
        fixture.stateStore.setBinding(destinationBinding())
        repository.replaceTasks(listOf(testTask(id = sharedLocalId, title = "Account B task")))

        releaseGate.complete(Unit)
        gateHolder.join()
        actionJob.await()

        assertEquals(0, repository.mutateExistingCalls)
        assertEquals(0, repository.getTaskByIdUtcCalls)
        assertTrue(repository.updated.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun capturedNoteMutationRejectsAfterQueuedAccountEpochChangeBeforeAccountBReadsOrWrites() = runTest {
        val fixture = fixture()
        val sharedLocalId = "shared-local-id"
        val repository = FakeNoteRepository(
            listOf(testNote(id = sharedLocalId, title = "Account B note")),
        )
        val expectedBoundary = fixture.executor.captureForegroundBoundary()
            ?: error("Expected Account A to be authenticated")
        val gateEntered = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        val gateHolder = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.mutationGate.withExclusive {
                gateEntered.complete(Unit)
                releaseGate.await()
            }
        }
        gateEntered.await()

        var accountBReads = 0
        var accountBWrites = 0
        val mutationJob = async(start = CoroutineStart.UNDISPATCHED) {
            assertFailsWith<AccountBoundaryRejectedException> {
                fixture.executor.withForegroundBoundary(expectedBoundary) {
                    accountBReads += 1
                    repository.getNoteById(sharedLocalId)?.let { note ->
                        accountBWrites += 1
                        UpdateNoteAction(repository)(note.copy(title = "stale update"))
                    }
                }
            }
        }
        fixture.repository.publishState(destinationState())
        fixture.stateStore.setBinding(destinationBinding())

        releaseGate.complete(Unit)
        gateHolder.join()
        mutationJob.await()

        assertEquals(0, accountBReads)
        assertEquals(0, accountBWrites)
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun everyInventoriedForegroundActionRejectsBeforeWorkWhenSignedOut() = runTest {
        val fixture = fixture(AccountSessionState.SignedOut)
        val taskRepository = FakeTaskRepository(listOf(testTask(id = "shared-local-id")))
        val countdownRepository = FakeCountdownRepository()
        val categoryRepository = FakeCategoryRepository()
        val tagRepository = FakeTagRepository()
        val scheduler = BlockingReminderScheduler(
            accountId = { fixture.repository.sessionState.value.accountIdOrUnknown() },
            events = mutableListOf(),
            blockCancellation = false,
        )
        val scheduleTask = ScheduleTaskRemindersAction(scheduler, taskRepository)
        val scheduleCountdown = ScheduleCountdownRemindersAction(scheduler, countdownRepository)
        val rebuild = RebuildReminderQueueAction(
            taskRepository,
            countdownRepository,
            scheduleTask,
            scheduleCountdown,
            scheduler,
        )
        val addTask = AddTaskAction(taskRepository, scheduleTask, accountBoundaryExecutor = fixture.executor)
        val updateTask = UpdateTaskAction(taskRepository, scheduleTask, accountBoundaryExecutor = fixture.executor)
        val toggleTask = ToggleTaskCompleteAction(taskRepository, scheduleTask, accountBoundaryExecutor = fixture.executor)
        val updateStatus = UpdateTaskStatusAction(taskRepository, scheduleTask, accountBoundaryExecutor = fixture.executor)
        val deleteTask = DeleteTaskAction(
            taskRepository,
            FakeAttachmentFileStorage(),
            scheduleTask,
            mutationGate = fixture.mutationGate,
            accountBoundaryExecutor = fixture.executor,
        )
        val importCsv = ImportCsvTasksAction(
            taskRepository,
            categoryRepository,
            scheduleTask,
            accountBoundaryExecutor = fixture.executor,
        )
        val importCalendar = ImportCalendarEventsAction(
            taskRepository,
            categoryRepository,
            tagRepository,
            AddTagAction(tagRepository),
            scheduleTask,
            accountBoundaryExecutor = fixture.executor,
        )
        val addCountdown = AddCountdownAction(
            countdownRepository,
            scheduleCountdown,
            rebuild,
            fixture.executor,
        )
        val updateCountdown = UpdateCountdownAction(
            countdownRepository,
            scheduleCountdown,
            accountBoundaryExecutor = fixture.executor,
        )
        val deleteCountdown = DeleteCountdownAction(
            countdownRepository,
            scheduleCountdown,
            accountBoundaryExecutor = fixture.executor,
        )
        val task = testTask(id = "shared-local-id")
        val countdown = testCountdown(id = "shared-local-id")

        assertFailsWith<AccountBoundaryRejectedException> { addTask("title", "content") }
        assertFailsWith<AccountBoundaryRejectedException> {
            updateTask(task.id, TaskWriteIntent.ToggleStar)
        }
        assertFailsWith<AccountBoundaryRejectedException> { toggleTask(task.id) }
        assertFailsWith<AccountBoundaryRejectedException> {
            updateStatus(task.id, TaskStatus.DONE)
        }
        assertFailsWith<AccountBoundaryRejectedException> { deleteTask(task.id) }
        assertFailsWith<AccountBoundaryRejectedException> { importCsv(emptyList()) }
        assertFailsWith<AccountBoundaryRejectedException> { importCalendar(emptyList()) }
        assertFailsWith<AccountBoundaryRejectedException> { addCountdown(countdown) }
        assertFailsWith<AccountBoundaryRejectedException> { updateCountdown(countdown) }
        assertFailsWith<AccountBoundaryRejectedException> { deleteCountdown(countdown) }

        assertTrue(taskRepository.inserted.isEmpty())
        assertTrue(taskRepository.updated.isEmpty())
        assertEquals(0, taskRepository.mutateExistingCalls)
        assertTrue(countdownRepository.inserted.isEmpty())
        assertTrue(countdownRepository.updated.isEmpty())
    }

    @Test
    fun taskActionPropagatesCancellationAndReleasesBoundaryForQueuedTransition() = runTest {
        val fixture = fixture()
        val sharedLocalId = "shared-local-id"
        val repository = FakeTaskRepository(
            listOf(
                testTask(
                    id = sharedLocalId,
                    deadline = FUTURE_UTC_MILLIS,
                    dateReminders = "0",
                ),
            ),
        )
        val cancellation = CancellationException("cancelled by reminder scheduler")
        val events = mutableListOf<String>()
        val scheduler = BlockingReminderScheduler(
            accountId = { fixture.repository.sessionState.value.accountIdOrUnknown() },
            events = events,
            cancellationFailure = cancellation,
        )
        val action = UpdateTaskAction(
            repository = repository,
            scheduleTaskRemindersAction = ScheduleTaskRemindersAction(
                scheduler = scheduler,
                taskRepository = repository,
                nowUtcMillisProvider = { 0L },
            ),
            accountBoundaryExecutor = fixture.executor,
        )
        val propagated = CompletableDeferred<CancellationException>()
        val actionJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                action(sharedLocalId, TaskWriteIntent.ToggleStar)
                error("Expected reminder cancellation to escape UpdateTaskAction")
            } catch (error: CancellationException) {
                propagated.complete(error)
            }
        }
        scheduler.cancellationEntered.await()

        val transitionJob = async {
            fixture.mutationGate.withExclusive {
                events += "account-b:transition"
                fixture.repository.publishState(destinationState())
                fixture.stateStore.setBinding(destinationBinding())
            }
        }
        yield()

        assertFalse(transitionJob.isCompleted)
        scheduler.releaseCancellation.complete(Unit)
        actionJob.join()
        transitionJob.await()

        val propagatedCancellation = propagated.await()
        assertEquals(cancellation.message, propagatedCancellation.message)
        assertTrue(
            propagatedCancellation === cancellation || propagatedCancellation.cause === cancellation,
        )
        assertEquals(
            listOf(
                "account-a:cancel:$sharedLocalId",
                "account-b:transition",
            ),
            events,
        )
    }

    private fun fixture(
        state: AccountSessionState = authenticatedState(),
    ): ActionBoundaryFixture {
        val mutationGate = MutexAccountMutationGate()
        val stateStore = WidgetFakeAccountStateStore(binding)
        val repository = FakeAccountRepository(state, mutationGate)
        return ActionBoundaryFixture(
            repository = repository,
            stateStore = stateStore,
            mutationGate = mutationGate,
            executor = AccountBoundaryExecutor(
                accountRepository = repository,
                accountBoundaryGuard = AccountBoundaryGuard(stateStore),
                mutationGate = mutationGate,
            ),
        )
    }

    private fun authenticatedState() = AccountSessionState.Authenticated(
        account = AuthenticatedAccount("account-a"),
        binding = binding,
        freshness = AccountSessionFreshness.ONLINE,
    )

    private fun destinationState() = AccountSessionState.Authenticated(
        account = AuthenticatedAccount("account-b"),
        binding = destinationBinding(),
        freshness = AccountSessionFreshness.ONLINE,
    )

    private fun destinationBinding() = binding.copy(accountId = "account-b", boundaryEpoch = 8L)

    private data class ActionBoundaryFixture(
        val repository: FakeAccountRepository,
        val stateStore: WidgetFakeAccountStateStore,
        val mutationGate: MutexAccountMutationGate,
        val executor: AccountBoundaryExecutor,
    )

    private class BlockingReminderScheduler(
        private val accountId: () -> String,
        private val events: MutableList<String>,
        private var blockCancellation: Boolean = true,
        private val blockReplacement: Boolean = false,
        private val cancellationFailure: CancellationException? = null,
    ) : ReminderScheduler {
        val cancellationEntered = CompletableDeferred<Unit>()
        val releaseCancellation = CompletableDeferred<Unit>()
        val replacementEntered = CompletableDeferred<Unit>()
        val releaseReplacement = CompletableDeferred<Unit>()

        override suspend fun schedule(request: ReminderRequest) {
            events += "${accountId()}:schedule:${request.eventId}"
        }

        override suspend fun cancel(semanticKey: String) = Unit

        override suspend fun cancelPendingReminders(eventId: String) = Unit

        override suspend fun cancelReminders(eventId: String) {
            events += "${accountId()}:cancel:$eventId"
            if (blockCancellation) {
                blockCancellation = false
                cancellationEntered.complete(Unit)
                releaseCancellation.await()
            }
            cancellationFailure?.let { throw it }
        }

        override suspend fun cancelAll(eventId: String) = Unit

        override suspend fun startOngoing(identity: ReminderIdentity, title: String) = Unit

        override suspend fun stopOngoing(eventId: String) {
            events += "${accountId()}:stop:$eventId"
        }

        override suspend fun cancelAllAccountReminders() = Unit

        override suspend fun replacePendingReminders(requests: List<ReminderRequest>) {
            events += "${accountId()}:replace:${requests.joinToString { it.eventId }}"
            if (blockReplacement) {
                replacementEntered.complete(Unit)
                releaseReplacement.await()
            }
        }
    }

    private fun AccountSessionState.accountIdOrUnknown(): String =
        (this as? AccountSessionState.Authenticated)?.account?.accountId ?: "signed-out"

    companion object {
        private const val FUTURE_UTC_MILLIS = 4_000_000_000_000L

        private val binding = CacheBinding(
            canonicalEndpoint = "https://tasks.example.com",
            serverInstanceId = "server",
            accountId = "account-a",
            capabilityVersion = 2,
            boundaryEpoch = 7L,
        )
    }
}
