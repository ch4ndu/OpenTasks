package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("BackgroundSyncHelper")

class BackgroundSyncHandle internal constructor(
    private val job: Job,
) {
    fun cancel() {
        job.cancel()
    }
}

object BackgroundSyncHelper : KoinComponent {

    private val syncService: SyncService by inject()
    private val rebuildReminderQueueAction: RebuildReminderQueueAction by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun performSync(completion: (Boolean) -> Unit): BackgroundSyncHandle {
        log.d { "Background sync starting" }
        val job = scope.launch {
            try {
                syncService.syncAll()
                rebuildReminderQueueAction()
            } catch (e: CancellationException) {
                log.d { "Background sync cancelled" }
                throw e
            } catch (e: Exception) {
                log.e { "Background sync failed: ${e.message}" }
                throw e
            }
        }
        job.invokeOnCompletion { cause ->
            completion(cause == null)
        }
        return BackgroundSyncHandle(job)
    }
}
