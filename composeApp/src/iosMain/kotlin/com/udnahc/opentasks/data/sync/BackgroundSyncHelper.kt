package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object BackgroundSyncHelper : KoinComponent {

    private val syncService: SyncService by inject()
    private val rescheduleAllRemindersAction: RescheduleAllRemindersAction by inject()

    fun performSync() {
        runBlocking {
            syncService.syncAll()
            rescheduleAllRemindersAction()
        }
    }
}
