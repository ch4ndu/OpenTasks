package com.udnahc.opentasks.data.sync

import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import org.lighthousegames.logging.logging

private val log = logging("SyncService")

class SyncService(
    private val pbProvider: PocketBaseClientProvider,
    private val adapters: List<BaseSyncAdapter<*, *>>,
) {
    private val syncMutex = Mutex()
    @Volatile private var pendingSyncRequested = false

    suspend fun syncAll() {
        val client = pbProvider.client ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return
        }
        if (!syncMutex.tryLock()) {
            log.d { "Sync already in progress, marking pending re-sync" }
            pendingSyncRequested = true
            return
        }
        try {
            do {
                pendingSyncRequested = false
                log.d { "Sync started" }
                val sorted = adapters.sortedBy { it.order }
                for (adapter in sorted) {
                    runCatching { adapter.pushAll(client) }
                        .onFailure { log.e { "Push ${adapter.collectionName} failed: ${it.message}" } }
                }
                for (adapter in sorted) {
                    runCatching { adapter.pullAll(client) }
                        .onFailure { log.e { "Pull ${adapter.collectionName} failed: ${it.message}" } }
                }
                log.d { "Sync completed" }
            } while (pendingSyncRequested)
        } finally {
            syncMutex.unlock()
        }
    }
}
