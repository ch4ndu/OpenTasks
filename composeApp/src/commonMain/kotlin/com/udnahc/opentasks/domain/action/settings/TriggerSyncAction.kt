package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService

class TriggerSyncAction(
    private val pbProvider: PocketBaseClientProvider,
    private val syncService: SyncService,
) {
    /** Triggers a full sync if the PocketBase client is configured. */
    suspend operator fun invoke() {
        if (!pbProvider.isConfigured) return
        syncService.syncAll()
    }
}
