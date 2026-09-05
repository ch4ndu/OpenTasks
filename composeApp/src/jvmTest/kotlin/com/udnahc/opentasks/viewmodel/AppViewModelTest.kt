package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModelStore
import com.udnahc.opentasks.SharedTaskPayload
import com.udnahc.opentasks.claimSharedIcsPayload
import com.udnahc.opentasks.claimSharedIcsPayloadForReview
import com.udnahc.opentasks.clearSharedTaskPayload
import com.udnahc.opentasks.completeSharedTaskReview
import com.udnahc.opentasks.deactivateSharedTaskIntake
import com.udnahc.opentasks.publishSharedTaskPayload
import com.udnahc.opentasks.sharedTaskIntakeStatus
import com.udnahc.opentasks.updateSharedTaskIntakeReadiness
import com.udnahc.opentasks.data.auth.AccountSessionFreshness
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryGuard
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.activeBindingOrNull
import com.udnahc.opentasks.data.auth.FakeAccountRepository
import com.udnahc.opentasks.data.auth.WidgetFakeAccountStateStore
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.usecase.task.ParseIcsUseCase
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeTagRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest : MainDispatcherRule() {
    @Test
    fun sharedIcsImportPublishesOneCommittedResultAndSuppressesDuplicateDispatch() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val fixture = fixture(taskRepository)
        val viewModel = fixture.viewModel
        val payload = SharedTaskPayload(id = 7L, icsContent = validIcs())

        viewModel.importSharedIcs(payload)
        viewModel.importSharedIcs(payload)
        advanceUntilIdle()

        val result = assertIs<SharedIcsImportResult.Success>(viewModel.sharedIcsImportResult.value)
        assertEquals(7L, result.payloadId)
        assertEquals(1, result.importedCount)
        assertEquals(1, taskRepository.inserted.size)
        assertTrue(viewModel.consumeSharedIcsImportResult(result))
        assertFalse(viewModel.consumeSharedIcsImportResult(result))
        assertNull(viewModel.sharedIcsImportResult.value)
    }

    @Test
    fun sharedIcsFailureRemainsPendingUntilTheHostConsumesIt() = runTest(dispatcher) {
        val viewModel = fixture(FakeTaskRepository()).viewModel
        val payload = SharedTaskPayload(id = 8L, icsContent = "not an ics document")

        viewModel.importSharedIcs(payload)
        advanceUntilIdle()

        val result = assertIs<SharedIcsImportResult.Failed>(viewModel.sharedIcsImportResult.value)
        assertEquals(payload.id, result.payloadId)
        assertTrue(viewModel.consumeSharedIcsImportResult(result))
        assertNull(viewModel.sharedIcsImportResult.value)
    }

    @Test
    fun sharedIcsBoundaryRejectionPublishesTheExistingImportFailureOutcome() = runTest(dispatcher) {
        val gate = MutexAccountMutationGate()
        val accountRepository = FakeAccountRepository(AccountSessionState.SignedOut, gate)
        val executor = AccountBoundaryExecutor(
            accountRepository = accountRepository,
            accountBoundaryGuard = AccountBoundaryGuard(WidgetFakeAccountStateStore(null)),
            mutationGate = gate,
        )
        val viewModel = AppViewModel(
            triggerSyncAction = triggerSyncAction(),
            parseIcsUseCase = ParseIcsUseCase(),
            importCalendarEventsAction = importCalendarEventsAction(
                taskRepository = FakeTaskRepository(),
                accountBoundaryExecutor = executor,
            ),
            accountBoundaryExecutor = executor,
            ioDispatcher = dispatcher,
        )

        viewModel.importSharedIcs(SharedTaskPayload(id = 9L, icsContent = validIcs()))

        val result = assertIs<SharedIcsImportResult.Failed>(viewModel.sharedIcsImportResult.value)
        assertEquals(9L, result.payloadId)
    }

    @Test
    fun distinctClaimedPayloadWaitsForTheFirstTerminalResultToBeConsumed() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val viewModel = fixture(taskRepository).viewModel

        viewModel.importSharedIcs(SharedTaskPayload(id = 10L, icsContent = validIcs("first")))
        viewModel.importSharedIcs(SharedTaskPayload(id = 11L, icsContent = validIcs("second")))
        advanceUntilIdle()

        val first = assertIs<SharedIcsImportResult.Success>(viewModel.sharedIcsImportResult.value)
        assertEquals(10L, first.payloadId)
        assertEquals(1, taskRepository.inserted.size)
        assertTrue(viewModel.consumeSharedIcsImportResult(first))
        advanceUntilIdle()

        val second = assertIs<SharedIcsImportResult.Success>(viewModel.sharedIcsImportResult.value)
        assertEquals(11L, second.payloadId)
        assertEquals(2, taskRepository.inserted.size)
        assertTrue(viewModel.consumeSharedIcsImportResult(second))
        assertNull(viewModel.sharedIcsImportResult.value)
    }

    @Test
    fun sharedIcsReviewStaysBusyThroughConfirmationAndTerminalResult() = runTest(dispatcher) {
        val payloadId = 90_201L
        val taskRepository = FakeTaskRepository()
        val viewModel = fixture(taskRepository).viewModel
        try {
            val claimed = claimIcsForReview(payloadId, "review-result")
            assertTrue(viewModel.requestSharedIcsImport(claimed))
            assertTrue(viewModel.isSharedIcsIntakeBusy.value)
            assertEquals(payloadId, sharedTaskIntakeStatus.value.activeReviewId)

            assertTrue(viewModel.confirmSharedIcsImport(payloadId))
            advanceUntilIdle()
            val result = assertIs<SharedIcsImportResult.Success>(viewModel.sharedIcsImportResult.value)
            assertTrue(viewModel.isSharedIcsIntakeBusy.value)
            assertEquals(payloadId, sharedTaskIntakeStatus.value.activeReviewId)

            assertTrue(viewModel.consumeSharedIcsImportResult(result))
            assertFalse(viewModel.isSharedIcsIntakeBusy.value)
            assertNull(sharedTaskIntakeStatus.value.activeReviewId)
        } finally {
            completeSharedTaskReview(payloadId)
            clearSharedTaskPayload(payloadId)
            deactivateSharedTaskIntake("shared-ics-account", 1L)
        }
    }

    @Test
    fun dismissingSharedIcsConfirmationReleasesItsReview() = runTest(dispatcher) {
        val payloadId = 90_202L
        val viewModel = fixture(FakeTaskRepository()).viewModel
        try {
            assertTrue(viewModel.requestSharedIcsImport(claimIcsForReview(payloadId, "dismiss")))
            assertEquals(payloadId, viewModel.sharedIcsImportConfirmation.value)

            assertTrue(viewModel.dismissSharedIcsImport(payloadId))
            assertFalse(viewModel.isSharedIcsIntakeBusy.value)
            assertNull(sharedTaskIntakeStatus.value.activeReviewId)
        } finally {
            completeSharedTaskReview(payloadId)
            clearSharedTaskPayload(payloadId)
            deactivateSharedTaskIntake("shared-ics-account", 1L)
        }
    }

    @Test
    fun clearingViewModelReleasesAClaimedConfirmationReview() = runTest(dispatcher) {
        val payloadId = 90_204L
        val viewModel = fixture(FakeTaskRepository()).viewModel
        var store: ViewModelStore? = null
        try {
            assertTrue(viewModel.requestSharedIcsImport(claimIcsForReview(payloadId, "clear-review")))
            store = ViewModelStore().also { it.put("shared-review", viewModel) }

            store.clear()

            assertFalse(viewModel.isSharedIcsIntakeBusy.value)
            assertNull(sharedTaskIntakeStatus.value.activeReviewId)
        } finally {
            store?.clear()
            completeSharedTaskReview(payloadId)
            clearSharedTaskPayload(payloadId)
            deactivateSharedTaskIntake("shared-ics-account", 1L)
        }
    }

    @Test
    fun cancelledSharedIcsImportReleasesItsReviewWithoutFailure() = runTest(dispatcher) {
        val payloadId = 90_203L
        val taskRepository = FakeTaskRepository().also {
            it.insertError = CancellationException("cancel shared import")
        }
        val viewModel = fixture(taskRepository).viewModel
        try {
            assertTrue(viewModel.importSharedIcs(claimIcsForReview(payloadId, "cancel-review")))
            advanceUntilIdle()

            assertNull(viewModel.sharedIcsImportResult.value)
            assertFalse(viewModel.isSharedIcsIntakeBusy.value)
            assertNull(sharedTaskIntakeStatus.value.activeReviewId)
        } finally {
            completeSharedTaskReview(payloadId)
            clearSharedTaskPayload(payloadId)
            deactivateSharedTaskIntake("shared-ics-account", 1L)
        }
    }

    @Test
    fun cancelledChildDoesNotPublishFailureAndAllowsTheNextClaimedPayload() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val fixture = fixture(taskRepository)
        taskRepository.insertError = CancellationException("cancel this import")

        fixture.viewModel.importSharedIcs(SharedTaskPayload(id = 12L, icsContent = validIcs("cancelled")))
        advanceUntilIdle()
        assertNull(fixture.viewModel.sharedIcsImportResult.value)

        taskRepository.insertError = null
        fixture.viewModel.importSharedIcs(SharedTaskPayload(id = 13L, icsContent = validIcs("retry")))
        advanceUntilIdle()
        val result = assertIs<SharedIcsImportResult.Success>(fixture.viewModel.sharedIcsImportResult.value)
        assertEquals(13L, result.payloadId)
    }

    @Test
    fun clearingAnEpochRetiresAnInFlightClaimedPayloadWithoutReplay() = runTest(dispatcher) {
        val payloadId = 90_101L
        val gate = MutexAccountMutationGate()
        val enteredGate = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        val gateHolder = launch {
            gate.withExclusive {
                enteredGate.complete(Unit)
                releaseGate.await()
            }
        }
        var store: ViewModelStore? = null
        try {
            enteredGate.await()
            publishSharedTaskPayload(payloadId, icsContent = validIcs("in-flight"))
            val claimed = assertNotNull(claimSharedIcsPayload(payloadId))
            assertNull(com.udnahc.opentasks.sharedTaskPayload.value)

            val taskRepository = FakeTaskRepository()
            val fixture = fixture(taskRepository, mutationGate = gate)
            fixture.viewModel.importSharedIcs(claimed)
            runCurrent()

            store = ViewModelStore().also { it.put("shared-ics-in-flight", fixture.viewModel) }
            store.clear()
            releaseGate.complete(Unit)
            gateHolder.join()
            advanceUntilIdle()

            assertTrue(taskRepository.inserted.isEmpty())
            assertNull(fixture.viewModel.sharedIcsImportResult.value)
            assertNull(com.udnahc.opentasks.sharedTaskPayload.value)
            assertNull(claimSharedIcsPayload(payloadId))

            val newEpochRepository = FakeTaskRepository()
            fixture(newEpochRepository).also {
                assertNull(claimSharedIcsPayload(payloadId))
                assertTrue(newEpochRepository.inserted.isEmpty())
            }
        } finally {
            releaseGate.complete(Unit)
            gateHolder.cancel()
            gateHolder.join()
            store?.clear()
            clearSharedTaskPayload(payloadId)
        }
    }

    @Test
    fun clearingAnEpochDiscardsAClaimedPendingSuccessWithoutGlobalReplay() = runTest(dispatcher) {
        val payloadId = 90_102L
        var store: ViewModelStore? = null
        try {
            publishSharedTaskPayload(payloadId, icsContent = validIcs("pending-result"))
            val claimed = assertNotNull(claimSharedIcsPayload(payloadId))
            assertNull(com.udnahc.opentasks.sharedTaskPayload.value)

            val taskRepository = FakeTaskRepository()
            val fixture = fixture(taskRepository)
            fixture.viewModel.importSharedIcs(claimed)
            advanceUntilIdle()
            assertIs<SharedIcsImportResult.Success>(fixture.viewModel.sharedIcsImportResult.value)

            store = ViewModelStore().also { it.put("shared-ics-pending-result", fixture.viewModel) }
            store.clear()

            assertNull(fixture.viewModel.sharedIcsImportResult.value)
            assertNull(com.udnahc.opentasks.sharedTaskPayload.value)
            assertNull(claimSharedIcsPayload(payloadId))

            val newEpochRepository = FakeTaskRepository()
            fixture(newEpochRepository).also {
                assertNull(claimSharedIcsPayload(payloadId))
                assertTrue(newEpochRepository.inserted.isEmpty())
            }
        } finally {
            store?.clear()
            clearSharedTaskPayload(payloadId)
        }
    }

    private data class Fixture(
        val viewModel: AppViewModel,
    )

    private fun fixture(
        taskRepository: FakeTaskRepository,
        state: AccountSessionState = authenticatedState(),
        mutationGate: MutexAccountMutationGate = MutexAccountMutationGate(),
    ): Fixture {
        val binding = state.activeBindingOrNull()
        val accountRepository = FakeAccountRepository(state, mutationGate)
        val executor = AccountBoundaryExecutor(
            accountRepository = accountRepository,
            accountBoundaryGuard = AccountBoundaryGuard(WidgetFakeAccountStateStore(binding)),
            mutationGate = mutationGate,
        )
        return Fixture(
            viewModel = AppViewModel(
                triggerSyncAction = triggerSyncAction(),
                parseIcsUseCase = ParseIcsUseCase(),
                importCalendarEventsAction = importCalendarEventsAction(taskRepository, executor),
                accountBoundaryExecutor = executor,
                ioDispatcher = dispatcher,
            ),
        )
    }

    private fun authenticatedState(): AccountSessionState = AccountSessionState.Authenticated(
        account = AuthenticatedAccount("shared-ics-account"),
        binding = CacheBinding(
            canonicalEndpoint = "https://tasks.example.test",
            serverInstanceId = "shared-ics-server",
            accountId = "shared-ics-account",
            capabilityVersion = 1,
            boundaryEpoch = 1L,
            mode = CacheMode.POCKETBASE,
        ),
        freshness = AccountSessionFreshness.ONLINE,
    )

    private fun claimIcsForReview(payloadId: Long, uid: String): SharedTaskPayload {
        updateSharedTaskIntakeReadiness(
            accountId = "shared-ics-account",
            boundaryEpoch = 1L,
            isMounted = true,
            isUiBusy = false,
        )
        publishSharedTaskPayload(payloadId, icsContent = validIcs(uid))
        return assertNotNull(
            claimSharedIcsPayloadForReview(
                payloadId,
                accountId = "shared-ics-account",
                boundaryEpoch = 1L,
            )
        )
    }

    private fun importCalendarEventsAction(
        taskRepository: FakeTaskRepository,
        accountBoundaryExecutor: AccountBoundaryExecutor,
    ): ImportCalendarEventsAction {
        val tagRepository = FakeTagRepository()
        return ImportCalendarEventsAction(
            taskRepository = taskRepository,
            categoryRepository = FakeCategoryRepository(),
            tagRepository = tagRepository,
            addTagAction = AddTagAction(tagRepository),
            scheduleTaskRemindersAction = ScheduleTaskRemindersAction(
                scheduler = NotificationScheduler(),
                taskRepository = taskRepository,
            ),
            accountBoundaryExecutor = accountBoundaryExecutor,
        )
    }

    private fun triggerSyncAction(): TriggerSyncAction {
        val provider = PocketBaseClientProvider()
        return TriggerSyncAction(
            provider,
            SyncService(provider, emptyList(), accountMutationGate = MutexAccountMutationGate()),
        )
    }

    private fun validIcs(uid: String = "shared-event"): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:$uid
        SUMMARY:Shared event
        DTSTART;VALUE=DATE:20260504
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
