package com.udnahc.opentasks.data.sync

import androidx.work.NetworkType
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

    @Test
    fun noStableClientSkipsOnlyNetworkSyncAndStillRunsLocalMaintenance() = runTest {
        val calls = mutableListOf<String>()

        runScheduledSyncMaintenance(
            syncNetwork = { false },
            rebuildReminders = { calls += "reminders" },
            refreshWidgets = { calls += "widgets" },
        )

        assertEquals(listOf("reminders", "widgets"), calls)
    }

    @Test
    fun configuredMaintenanceRunsSyncBeforeLocalMaintenance() = runTest {
        val calls = mutableListOf<String>()

        runScheduledSyncMaintenance(
            syncNetwork = {
                calls += "network"
                true
            },
            rebuildReminders = { calls += "reminders" },
            refreshWidgets = { calls += "widgets" },
        )

        assertEquals(listOf("network", "reminders", "widgets"), calls)
    }

    @Test
    fun networkFailureStillRunsMaintenanceAndRethrowsOriginalWithSuppressedFailures() = runTest {
        val calls = mutableListOf<String>()
        val networkFailure = IllegalStateException("network")
        val reminderFailure = IllegalArgumentException("reminders")
        val widgetFailure = IllegalArgumentException("widgets")

        val thrown = assertFailsWith<IllegalStateException> {
            runScheduledSyncMaintenance(
                syncNetwork = {
                    calls += "network"
                    throw networkFailure
                },
                rebuildReminders = {
                    calls += "reminders"
                    throw reminderFailure
                },
                refreshWidgets = {
                    calls += "widgets"
                    throw widgetFailure
                },
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
            runScheduledSyncMaintenance(
                syncNetwork = {
                    calls += "network"
                    throw cancellation
                },
                rebuildReminders = { calls += "reminders" },
                refreshWidgets = { calls += "widgets" },
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
}
