package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lighthousegames.logging.logging

private val log = logging("SyncService")

class SyncService(
    private val pbProvider: PocketBaseClientProvider,
    private val adapters: List<BaseSyncAdapter<*, *>>,
    private val seedExecutor: ServerSeedExecutor? = null,
) {
    private val syncMutex = Mutex()
    private val resetMutex = Mutex()
    private val resetInProgress = MutableStateFlow(false)

    suspend fun syncAll() {
        if (resetInProgress.value) {
            log.d { "Sync skipped: local data reset in progress" }
            return
        }
        val client = pbProvider.client ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return
        }
        syncAll(client)
    }

    suspend fun syncAll(client: PocketbaseClient) {
        if (resetInProgress.value) {
            log.d { "Sync skipped: local data reset in progress" }
            return
        }
        syncMutex.withLock {
            if (resetInProgress.value) {
                log.d { "Sync skipped: local data reset in progress" }
                return
            }
            if (seedExecutor?.isPending() == true) {
                seedExecutor.resume(client)
                return
            }
            log.d { "Sync started" }
            val passFailures = syncPass(client)
            if (passFailures.isEmpty()) {
                log.d { "Sync completed" }
            } else {
                log.e { "Sync completed with ${passFailures.size} failure(s)" }
            }
            if (passFailures.isNotEmpty()) {
                throw SyncException(passFailures)
            }
        }
    }

    /**
     * Runs local cleanup without allowing an active or queued sync to race it.
     * The reset flag is set before cancellation or waiting so new sync requests are rejected.
     * PocketBase is disconnected before waiting and remains disconnected if cleanup fails.
     */
    suspend fun <T> runExclusiveReset(
        cancelPendingSync: suspend () -> Unit,
        clearLocalData: suspend () -> T,
    ): T = resetMutex.withLock {
        resetInProgress.value = true
        try {
            try {
                cancelPendingSync()
            } finally {
                pbProvider.disconnect()
            }
            syncMutex.withLock {
                clearLocalData()
            }
        } finally {
            resetInProgress.value = false
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
        (collectionName == COLLECTION_ATTACHMENTS && COLLECTION_TASKS in failedPulls) ||
                collectionName == COLLECTION_TASK_TAGS &&
                (COLLECTION_TASKS in failedPulls || COLLECTION_TAGS in failedPulls)

    private fun shouldSkipPush(
        collectionName: String,
        failedPulls: Set<String>
    ): Boolean =
        collectionName in failedPulls ||
                (collectionName == COLLECTION_TASKS && COLLECTION_CATEGORIES in failedPulls) ||
                (collectionName == COLLECTION_ATTACHMENTS && COLLECTION_TASKS in failedPulls) ||
                (collectionName == COLLECTION_TASK_TAGS &&
                        (COLLECTION_TASKS in failedPulls || COLLECTION_TAGS in failedPulls))

    private companion object {
        const val COLLECTION_CATEGORIES = "categories"
        const val COLLECTION_TAGS = "tags"
        const val COLLECTION_TASKS = "tasks"
        const val COLLECTION_ATTACHMENTS = "attachments"
        const val COLLECTION_TASK_TAGS = "task_tags"
    }
}
