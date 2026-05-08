package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.sync.SyncService

class InitializeSyncAction(
    private val configurePocketBaseUrlAction: ConfigurePocketBaseUrlAction,
    private val syncService: SyncService,
) {
    /** Reads the stored URL, configures the client if present, and syncs. */
    suspend operator fun invoke() {
        if (!configurePocketBaseUrlAction()) return
        syncService.syncAll()
    }
}
