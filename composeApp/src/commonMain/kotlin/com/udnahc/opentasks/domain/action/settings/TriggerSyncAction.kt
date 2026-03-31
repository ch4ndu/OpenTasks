package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("TriggerSyncAction")

class TriggerSyncAction(
    private val pbProvider: PocketBaseClientProvider,
    private val syncService: SyncService,
) {
    // Long-lived scope: this class is a Koin `single` that lives for the app's lifetime.
    // SupervisorJob ensures individual sync failures don't cancel the entire scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null

    companion object {
        private const val DEBOUNCE_DELAY_MS = 2000L
    }

    /**
     * Triggers a full sync if the PocketBase client is configured.
     * Rapid calls are debounced: only the last call within a 2-second window
     * actually triggers the sync.
     */
    suspend operator fun invoke() {
        log.d { "Triggering sync (debounced)" }
        if (!pbProvider.isConfigured) {
            log.d { "Sync skipped: PocketBase not configured" }
            return
        }
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            log.d { "Debounce elapsed, starting sync" }
            syncService.syncAll()
        }
    }
}
