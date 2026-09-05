package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
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
    private val accountBoundaryExecutor: AccountBoundaryExecutor by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun performSync(completion: (Boolean) -> Unit): BackgroundSyncHandle {
        log.d { "Background sync starting" }
        var succeeded = false
        val job = scope.launch {
            try {
                val capturedBoundary = accountBoundaryExecutor.withActiveCacheBoundary { boundary ->
                    boundary
                }
                if (capturedBoundary == null) {
                    log.d { "Background maintenance skipped without an active cache boundary" }
                } else {
                    runScheduledSyncMaintenance(
                        capturedBoundary = capturedBoundary,
                        syncNetwork = { syncService.syncActiveClientWithinMutation() },
                        withRevalidatedBoundary = { expected, block ->
                            accountBoundaryExecutor.withForegroundBoundary(expected, block)
                        },
                        maintenanceSteps = listOf(
                            { rebuildReminderQueueAction() },
                        ),
                    )
                }
                succeeded = true
            } catch (error: CancellationException) {
                log.d { "Background sync cancelled" }
                throw error
            } catch (_: Exception) {
                log.e { "Background sync failed" }
            }
        }
        job.invokeOnCompletion { cause ->
            completion(cause == null && succeeded)
        }
        return BackgroundSyncHandle(job)
    }
}
