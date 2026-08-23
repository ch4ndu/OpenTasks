package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncOutcome
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.data.sync.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("TriggerSyncAction")

class TriggerSyncAction(
    private val pbProvider: PocketBaseClientProvider,
    private val syncService: SyncService,
    private val runSyncPass: suspend () -> Unit = { syncService.syncAll() },
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val waitForDebounce: suspend (Long) -> Unit = { delay(it) },
) : SyncTrigger {
    // Long-lived scope: this class is a Koin `single` that lives for the app's lifetime.
    // SupervisorJob ensures individual sync failures don't cancel the entire scope.
    private val scope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
    private val debounceMutex = Mutex()
    private var pendingDelayJob: Job? = null

    val outcome: StateFlow<SyncOutcome> = syncService.outcome

    companion object {
        private const val DEBOUNCE_DELAY_MS = 2000L
    }

    /**
     * Triggers a full sync if the PocketBase client is configured.
     * Rapid calls are debounced: only the last call within a 2-second window
     * actually triggers the sync.
     */
    suspend operator fun invoke() = triggerSync()

    override suspend fun triggerSync() {
        log.d { "Triggering sync (debounced)" }
        if (!pbProvider.isConfigured) {
            log.d { "Sync skipped: PocketBase not configured" }
            return
        }
        debounceMutex.withLock {
            pendingDelayJob?.cancel()
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    waitForDebounce(DEBOUNCE_DELAY_MS)
                    val owningJob = currentCoroutineContext()[Job]
                    val claimed = debounceMutex.withLock {
                        if (pendingDelayJob === owningJob) {
                            pendingDelayJob = null
                            true
                        } else {
                            false
                        }
                    }
                    if (!claimed) return@launch
                    log.d { "Debounce elapsed, starting sync" }
                    runSyncPass()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.e(error) { "Debounced sync failed" }
                }
            }
            pendingDelayJob = job
            job.start()
        }
    }

    /**
     * Runs a full sync immediately in the caller's coroutine and suspends until it completes.
     * Cancels any pending debounced sync. Use for user-initiated syncs (e.g. pull-to-refresh)
     * that need to show progress tied to actual completion.
     */
    suspend fun syncNow() {
        log.d { "Triggering sync (immediate)" }
        if (!pbProvider.isConfigured) {
            log.d { "Sync skipped: PocketBase not configured" }
            return
        }
        val pending = debounceMutex.withLock {
            pendingDelayJob.also { pendingDelayJob = null }
        }
        pending?.cancelAndJoin()
        runSyncPass()
    }

    suspend fun cancelPendingSync() {
        val pending = debounceMutex.withLock {
            pendingDelayJob.also { pendingDelayJob = null }
        }
        pending?.cancelAndJoin()
    }
}
