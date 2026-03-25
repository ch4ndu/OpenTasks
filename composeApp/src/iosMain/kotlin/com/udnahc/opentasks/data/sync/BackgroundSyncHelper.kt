package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("BackgroundSyncHelper")

object BackgroundSyncHelper : KoinComponent {

    private val syncService: SyncService by inject()
    private val rescheduleAllRemindersAction: RescheduleAllRemindersAction by inject()

    fun performSync() {
        log.d { "Background sync starting" }
        runBlocking {
            try {
                syncService.syncAll()
                rescheduleAllRemindersAction()
            } catch (e: Exception) {
                log.e { "Background sync failed: ${e.message}" }
            }
        }
    }
}
