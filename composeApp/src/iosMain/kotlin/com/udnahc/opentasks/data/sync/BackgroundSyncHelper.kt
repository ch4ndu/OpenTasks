package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("BackgroundSyncHelper")

object BackgroundSyncHelper : KoinComponent {

    private val syncService: SyncService by inject()
    private val rebuildReminderQueueAction: RebuildReminderQueueAction by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun performSync() {
        log.d { "Background sync starting" }
        scope.launch {
            try {
                syncService.syncAll()
                rebuildReminderQueueAction()
            } catch (e: Exception) {
                log.e { "Background sync failed: ${e.message}" }
            }
        }
    }
}
