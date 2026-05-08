package com.udnahc.opentasks.data.sync

interface SyncTrigger {
    suspend fun triggerSync()
}
