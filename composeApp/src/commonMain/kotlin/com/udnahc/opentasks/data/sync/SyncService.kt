package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.sync.Mutex
import org.lighthousegames.logging.logging
import kotlin.concurrent.Volatile

private val log = logging("SyncService")

class SyncService(
    private val pbProvider: PocketBaseClientProvider,
    private val adapters: List<BaseSyncAdapter<*, *>>,
) {
    private val syncMutex = Mutex()
    @Volatile
    private var pendingSyncRequested = false

    suspend fun syncAll() {
        val client = pbProvider.client ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return
        }
        syncAll(client)
    }

    suspend fun syncAll(client: PocketbaseClient) {
        if (!syncMutex.tryLock()) {
            log.d { "Sync already in progress, marking pending re-sync" }
            pendingSyncRequested = true
            return
        }
        try {
            var passFailures: List<SyncCollectionFailure>
            do {
                pendingSyncRequested = false
                log.d { "Sync started" }
                passFailures = syncPass(client)
                if (passFailures.isEmpty()) {
                    log.d { "Sync completed" }
                } else {
                    log.e { "Sync completed with ${passFailures.size} failure(s)" }
                }
            } while (pendingSyncRequested)
            if (passFailures.isNotEmpty()) {
                throw SyncException(passFailures)
            }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncPass(client: PocketbaseClient): List<SyncCollectionFailure> {
        val failures = mutableListOf<SyncCollectionFailure>()
        val failedPulls = mutableSetOf<String>()

        adapters.sortedBy { it.order }.forEach { adapter ->
            val collectionName = adapter.collectionName
            if (shouldSkipPull(collectionName, failedPulls)) {
                log.w { "Skipping pull $collectionName because a parent collection pull failed" }
            } else {
                runCatching { adapter.pullAll(client) }
                    .onFailure {
                        log.e(it) { "Pull $collectionName failed" }
                        failedPulls += collectionName
                        failures += SyncCollectionFailure(collectionName, "pull", it)
                    }
            }

            if (shouldSkipPush(collectionName, failedPulls)) {
                log.w { "Skipping push $collectionName because pull dependencies failed" }
            } else {
                runCatching { adapter.pushAll(client) }
                    .onFailure {
                        log.e(it) { "Push $collectionName failed" }
                        failures += SyncCollectionFailure(collectionName, "push", it)
                    }
            }
        }

        return failures
    }

    private fun shouldSkipPull(
        collectionName: String,
        failedPulls: Set<String>
    ): Boolean =
        collectionName == COLLECTION_TASK_TAGS &&
                (COLLECTION_TASKS in failedPulls || COLLECTION_TAGS in failedPulls)

    private fun shouldSkipPush(
        collectionName: String,
        failedPulls: Set<String>
    ): Boolean =
        collectionName in failedPulls ||
                (collectionName == COLLECTION_TASKS && COLLECTION_CATEGORIES in failedPulls) ||
                (collectionName == COLLECTION_TASK_TAGS &&
                        (COLLECTION_TASKS in failedPulls || COLLECTION_TAGS in failedPulls))

    private companion object {
        const val COLLECTION_CATEGORIES = "categories"
        const val COLLECTION_TAGS = "tags"
        const val COLLECTION_TASKS = "tasks"
        const val COLLECTION_TASK_TAGS = "task_tags"
    }
}
