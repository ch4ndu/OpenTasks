package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.sync.SyncService

class InitializeSyncAction(
    private val syncService: SyncService,
) {
    /** Authenticated startup already has a validated active account client. */
    suspend operator fun invoke() = syncService.syncAll()
}
