package com.udnahc.opentasks.data.sync

import androidx.work.NetworkType
import com.udnahc.opentasks.periodicSyncRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncWorkerTest {

    @Test
    fun noUrlSkipsOnlyNetworkSyncAndStillRunsLocalMaintenance() = runTest {
        val calls = mutableListOf<String>()

        runScheduledSyncMaintenance(
            configureNetwork = { false },
            syncNetwork = { calls += "network" },
            rebuildReminders = { calls += "reminders" },
            refreshWidgets = { calls += "widgets" },
        )

        assertEquals(listOf("reminders", "widgets"), calls)
    }

    @Test
    fun periodicMaintenanceWorkIsNotNetworkConstrained() {
        assertEquals(NetworkType.NOT_REQUIRED, periodicSyncRequest().workSpec.constraints.requiredNetworkType)
    }
}
