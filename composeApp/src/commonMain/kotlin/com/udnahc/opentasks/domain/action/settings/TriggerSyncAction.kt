package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import org.lighthousegames.logging.logging

private val log = logging("TriggerSyncAction")

class TriggerSyncAction(
    private val pbProvider: PocketBaseClientProvider,
    private val syncService: SyncService,
) {
    /** Triggers a full sync if the PocketBase client is configured. */
    suspend operator fun invoke() {
        log.d { "Triggering sync" }
        if (!pbProvider.isConfigured) {
            log.d { "Sync skipped: PocketBase not configured" }
            return
        }
        syncService.syncAll()
    }
}
