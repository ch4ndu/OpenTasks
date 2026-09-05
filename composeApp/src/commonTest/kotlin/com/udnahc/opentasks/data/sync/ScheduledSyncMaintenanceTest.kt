package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryGuard
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.AccountSessionFreshness
import com.udnahc.opentasks.data.auth.AccountSessionState
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.auth.FakeAccountRepository
import com.udnahc.opentasks.data.auth.LOCAL_CACHE_OWNER_ID
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.auth.WidgetFakeAccountStateStore
import com.udnahc.opentasks.data.auth.asAccountBoundary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ScheduledSyncMaintenanceTest {
    private val binding = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 7L,
    )
    private val boundary = binding.asAccountBoundary()

    @Test
    fun localOnlySkipsNetworkAndRunsEachMaintenanceStep() = runTest {
        val localBoundary = AccountBoundary(
            canonicalEndpoint = "",
            serverInstanceId = "",
            accountId = LOCAL_CACHE_OWNER_ID,
            capabilityVersion = 0,
            boundaryEpoch = 11L,
            mode = CacheMode.LOCAL_ONLY,
        )
        val calls = mutableListOf<String>()

        runMaintenance(
            capturedBoundary = localBoundary,
            syncNetwork = { calls += "network" },
            maintenanceSteps = listOf(
                { calls += "reminders" },
                { calls += "task-widget" },
                { calls += "calendar-widget" },
                { calls += "week-widget" },
            ),
        )

        assertEquals(
            listOf("reminders", "task-widget", "calendar-widget", "week-widget"),
            calls,
        )
    }

    @Test
    fun ordinaryNetworkFailureRunsEveryStepAndRemainsThePrimaryFailure() = runTest {
        val calls = mutableListOf<String>()
        val networkFailure = IllegalStateException("network")
        val reminderFailure = IllegalArgumentException("reminders")
        val taskWidgetFailure = IllegalArgumentException("task-widget")
        val weekWidgetFailure = IllegalArgumentException("week-widget")

        val thrown = assertFailsWith<IllegalStateException> {
            runMaintenance(
                syncNetwork = {
                    calls += "network"
                    throw networkFailure
                },
                maintenanceSteps = listOf(
                    {
                        calls += "reminders"
                        throw reminderFailure
                    },
                    {
                        calls += "task-widget"
                        throw taskWidgetFailure
                    },
                    { calls += "calendar-widget" },
                    {
                        calls += "week-widget"
                        throw weekWidgetFailure
                    },
                ),
            )
        }

        assertSame(networkFailure, thrown)
        assertEquals(
            listOf("network", "reminders", "task-widget", "calendar-widget", "week-widget"),
            calls,
        )
        assertEquals(
            listOf(reminderFailure, taskWidgetFailure, weekWidgetFailure),
            thrown.suppressedExceptions,
        )
    }

    @Test
    fun authenticationRejectionStopsBeforeMaintenance() = runTest {
        val rejection = SyncAuthenticationRejectedException()
        var validations = 0
        var maintenanceCalls = 0

        val thrown = assertFailsWith<SyncAuthenticationRejectedException> {
            runScheduledSyncMaintenance(
                capturedBoundary = boundary,
                syncNetwork = { throw IllegalStateException("wrapped", rejection) },
                withRevalidatedBoundary = { expected, block ->
                    validations += 1
                    block(expected)
                },
                maintenanceSteps = listOf({ maintenanceCalls += 1 }),
            )
        }

        assertSame(rejection, thrown)
        assertEquals(1, validations)
        assertEquals(0, maintenanceCalls)
    }

    @Test
    fun staleBoundaryAfterNetworkStopsBeforeMaintenanceWithRealExecutor() = runTest {
        val mutationGate = MutexAccountMutationGate()
        val stateStore = WidgetFakeAccountStateStore(binding)
        val repository = FakeAccountRepository(
            state = authenticatedState(binding),
            mutationGate = mutationGate,
        )
        val executor = AccountBoundaryExecutor(
            accountRepository = repository,
            accountBoundaryGuard = AccountBoundaryGuard(stateStore),
            mutationGate = mutationGate,
        )
        val replacement = binding.copy(accountId = "account-b", boundaryEpoch = 8L)
        var maintenanceCalls = 0

        assertFailsWith<AccountBoundaryRejectedException> {
            runScheduledSyncMaintenance(
                capturedBoundary = boundary,
                syncNetwork = {
                    repository.publishState(authenticatedState(replacement))
                    stateStore.setBinding(replacement)
                },
                withRevalidatedBoundary = { expected, block ->
                    executor.withForegroundBoundary(expected, block)
                },
                maintenanceSteps = listOf({ maintenanceCalls += 1 }),
            )
        }

        assertEquals(0, maintenanceCalls)
        assertEquals(0, repository.restoreCalls)
    }

    @Test
    fun boundaryRejectionBeforeNetworkRunsNoEffects() = runTest {
        var networkCalls = 0
        var maintenanceCalls = 0

        assertFailsWith<AccountBoundaryRejectedException> {
            runScheduledSyncMaintenance(
                capturedBoundary = boundary,
                syncNetwork = { networkCalls += 1 },
                withRevalidatedBoundary = { _, _ -> throw AccountBoundaryRejectedException() },
                maintenanceSteps = listOf({ maintenanceCalls += 1 }),
            )
        }

        assertEquals(0, networkCalls)
        assertEquals(0, maintenanceCalls)
    }

    @Test
    fun cancellationFromNetworkOrRevalidationStopsBeforeMaintenance() = runTest {
        val networkCancellation = CancellationException("network")
        var maintenanceCalls = 0
        val networkThrown = assertFailsWith<CancellationException> {
            runMaintenance(
                syncNetwork = { throw networkCancellation },
                maintenanceSteps = listOf({ maintenanceCalls += 1 }),
            )
        }
        assertSame(networkCancellation, networkThrown)
        assertEquals(0, maintenanceCalls)

        val revalidationCancellation = CancellationException("revalidation")
        var validations = 0
        val revalidationThrown = assertFailsWith<CancellationException> {
            runScheduledSyncMaintenance(
                capturedBoundary = boundary,
                syncNetwork = {},
                withRevalidatedBoundary = { expected, block ->
                    validations += 1
                    if (validations == 2) throw revalidationCancellation
                    block(expected)
                },
                maintenanceSteps = listOf({ maintenanceCalls += 1 }),
            )
        }
        assertSame(revalidationCancellation, revalidationThrown)
        assertEquals(0, maintenanceCalls)
    }

    @Test
    fun cancellationFromEachMaintenancePhaseStopsLaterSteps() = runTest {
        repeat(4) { cancelledIndex ->
            val cancellation = CancellationException("maintenance-$cancelledIndex")
            val calls = mutableListOf<Int>()
            val steps: List<suspend (AccountBoundary) -> Unit> = (0..3).map { index ->
                {
                    calls += index
                    if (index == cancelledIndex) throw cancellation
                }
            }

            val thrown = assertFailsWith<CancellationException> {
                runMaintenance(maintenanceSteps = steps)
            }

            assertSame(cancellation, thrown)
            assertEquals((0..cancelledIndex).toList(), calls)
        }
    }

    @Test
    fun laterBoundaryRejectionKeepsEarlierMaintenanceFailureBehindOriginalSyncFailure() = runTest {
        val networkFailure = IllegalStateException("network")
        val maintenanceFailure = IllegalArgumentException("reminders")
        val boundaryRejection = AccountBoundaryRejectedException()
        val calls = mutableListOf<String>()

        val thrown = assertFailsWith<IllegalStateException> {
            runMaintenance(
                syncNetwork = { throw networkFailure },
                maintenanceSteps = listOf(
                    {
                        calls += "reminders"
                        throw maintenanceFailure
                    },
                    {
                        calls += "task-widget"
                        throw boundaryRejection
                    },
                    { calls += "calendar-widget" },
                ),
            )
        }

        assertSame(networkFailure, thrown)
        assertEquals(listOf("reminders", "task-widget"), calls)
        assertEquals(listOf(maintenanceFailure, boundaryRejection), thrown.suppressedExceptions)
    }

    private suspend fun runMaintenance(
        capturedBoundary: AccountBoundary = boundary,
        syncNetwork: suspend (AccountBoundary) -> Unit = {},
        maintenanceSteps: List<suspend (AccountBoundary) -> Unit>,
    ) {
        runScheduledSyncMaintenance(
            capturedBoundary = capturedBoundary,
            syncNetwork = syncNetwork,
            withRevalidatedBoundary = { expected, block -> block(expected) },
            maintenanceSteps = maintenanceSteps,
        )
    }

    private fun authenticatedState(binding: CacheBinding) = AccountSessionState.Authenticated(
        account = AuthenticatedAccount(binding.accountId),
        binding = binding,
        freshness = AccountSessionFreshness.ONLINE,
    )
}
