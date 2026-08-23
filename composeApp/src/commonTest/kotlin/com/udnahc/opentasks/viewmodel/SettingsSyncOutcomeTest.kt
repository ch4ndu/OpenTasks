package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.sync.SyncOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSyncOutcomeTest {
    @Test
    fun sharedSyncOutcomesMapToSettingsStatus() {
        assertEquals(SyncStatus.IDLE, toSettingsSyncStatus(SyncOutcome.Idle))
        assertEquals(SyncStatus.SYNCING, toSettingsSyncStatus(SyncOutcome.Syncing))
        assertEquals(SyncStatus.SUCCESS, toSettingsSyncStatus(SyncOutcome.Success))
        assertEquals(SyncStatus.SYNC_ERROR, toSettingsSyncStatus(SyncOutcome.Failed))
        assertEquals(SyncStatus.SYNC_ERROR, toSettingsSyncStatus(SyncOutcome.ReauthenticationRequired))
    }
}
