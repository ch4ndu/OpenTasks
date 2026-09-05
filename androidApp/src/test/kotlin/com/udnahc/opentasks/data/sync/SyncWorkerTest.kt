package com.udnahc.opentasks.data.sync

import androidx.work.NetworkType
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.CacheMode
import com.udnahc.opentasks.data.auth.LOCAL_CACHE_OWNER_ID
import com.udnahc.opentasks.periodicSyncRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SyncWorkerTest {
    private val remoteBoundary = AccountBoundary(
        canonicalEndpoint = "https://tasks.example.com",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 7L,
    )

    @Test
    fun localOnlyBoundarySkipsNetworkAndStillRunsLocalMaintenance() = runTest {
        val calls = mutableListOf<String>()
        val localBoundary = remoteBoundary.copy(
            canonicalEndpoint = "",
            serverInstanceId = "",
            accountId = LOCAL_CACHE_OWNER_ID,
            capabilityVersion = 0,
            mode = CacheMode.LOCAL_ONLY,
        )

        runWorkerMaintenance(
            boundary = localBoundary,
            syncNetwork = { calls += "network" },
            maintenanceSteps = listOf(
                { calls += "reminders" },
                { calls += "widgets" },
            ),
        )

        assertEquals(listOf("reminders", "widgets"), calls)
    }

    @Test
    fun configuredMaintenanceRunsSyncBeforeLocalMaintenance() = runTest {
        val calls = mutableListOf<String>()

        runWorkerMaintenance(
            syncNetwork = {
                calls += "network"
            },
            maintenanceSteps = listOf(
                { calls += "reminders" },
                { calls += "task-widget" },
                { calls += "calendar-widget" },
                { calls += "week-widget" },
            ),
        )

        assertEquals(
            listOf("network", "reminders", "task-widget", "calendar-widget", "week-widget"),
            calls,
        )
    }

    @Test
    fun networkFailureStillRunsMaintenanceAndRethrowsOriginalWithSuppressedFailures() = runTest {
        val calls = mutableListOf<String>()
        val networkFailure = IllegalStateException("network")
        val reminderFailure = IllegalArgumentException("reminders")
        val widgetFailure = IllegalArgumentException("widgets")

        val thrown = assertFailsWith<IllegalStateException> {
            runWorkerMaintenance(
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
                        calls += "widgets"
                        throw widgetFailure
                    },
                ),
            )
        }

        assertSame(networkFailure, thrown)
        assertEquals(listOf("network", "reminders", "widgets"), calls)
        assertEquals(listOf(reminderFailure, widgetFailure), thrown.suppressedExceptions)
    }

    @Test
    fun cancellationFromNetworkEscapesBeforeMaintenance() = runTest {
        val calls = mutableListOf<String>()
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> {
            runWorkerMaintenance(
                syncNetwork = {
                    calls += "network"
                    throw cancellation
                },
                maintenanceSteps = listOf(
                    { calls += "reminders" },
                    { calls += "widgets" },
                ),
            )
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf("network"), calls)
    }

    @Test
    fun periodicMaintenanceWorkIsNotNetworkConstrained() {
        val binding = CacheBinding(
            canonicalEndpoint = "https://tasks.example.com",
            serverInstanceId = "server",
            accountId = "account-a",
            capabilityVersion = 2,
            boundaryEpoch = 7,
        )
        val request = periodicSyncRequest(binding)

        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertEquals("account-a", request.workSpec.input.getString(SyncWorker.KEY_ACCOUNT_ID))
        assertEquals(7, request.workSpec.input.getLong(SyncWorker.KEY_BOUNDARY_EPOCH, 0))
    }

    @Test
    fun localBoundarySchedulesTheSameMaintenanceContractWithoutNetworkConstraint() {
        val binding = CacheBinding(
            canonicalEndpoint = "",
            serverInstanceId = "",
            accountId = LOCAL_CACHE_OWNER_ID,
            capabilityVersion = 0,
            boundaryEpoch = 12L,
            mode = CacheMode.LOCAL_ONLY,
        )

        val request = periodicSyncRequest(binding)

        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(LOCAL_CACHE_OWNER_ID, request.workSpec.input.getString(SyncWorker.KEY_ACCOUNT_ID))
        assertEquals(12L, request.workSpec.input.getLong(SyncWorker.KEY_BOUNDARY_EPOCH, 0L))
    }

    private suspend fun runWorkerMaintenance(
        boundary: AccountBoundary = remoteBoundary,
        syncNetwork: suspend (AccountBoundary) -> Unit,
        maintenanceSteps: List<suspend (AccountBoundary) -> Unit>,
    ) {
        runScheduledSyncMaintenance(
            capturedBoundary = boundary,
            syncNetwork = syncNetwork,
            withRevalidatedBoundary = { expected, block -> block(expected) },
            maintenanceSteps = maintenanceSteps,
        )
    }
}
