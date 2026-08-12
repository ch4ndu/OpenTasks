package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.auth.AccountSyncCoordinator
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AccountTransitionPurpose
import com.udnahc.opentasks.data.settings.AccountStateStore
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("SyncService")

class SyncService(
    private val pbProvider: PocketBaseClientProvider,
    private val adapters: List<BaseSyncAdapter<*, *>>,
    private val seedExecutor: ServerSeedExecutor? = null,
    private val accountMutationGate: AccountMutationGate,
    private val accountStateStore: AccountStateStore? = null,
) : AccountSyncCoordinator {
    private val syncMutex = Mutex()
    private val resetMutex = Mutex()
    private val resetInProgress = MutableStateFlow(false)

    suspend fun syncAll() = accountMutationGate.withExclusive {
        if (resetInProgress.value) {
            log.d { "Sync skipped: local data reset in progress" }
            return@withExclusive
        }
        val client = pbProvider.client ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return@withExclusive
        }
        if (pbProvider.activeBinding == null) {
            log.d { "Sync skipped: no active authenticated account boundary" }
            return@withExclusive
        }
        if (!hasStableBoundary(client, allowTransition = false)) {
            log.d { "Sync skipped: active client does not match the durable account boundary" }
            return@withExclusive
        }
        syncAllWithinMutation(client)
    }

    suspend fun syncAll(client: PocketbaseClient) = accountMutationGate.withExclusive {
        pbProvider.requireActiveBinding(client)
        check(hasStableBoundary(client, allowTransition = false)) {
            "PocketBase sync client does not match the durable account boundary"
        }
        syncAllWithinMutation(client)
    }

    /**
     * Syncs the active client for callers that already hold AccountMutationGate.
     * Returns false when no stable authenticated client is available and never
     * acquires AccountMutationGate itself.
     */
    suspend fun syncActiveClientWithinMutation(): Boolean {
        if (resetInProgress.value) {
            log.d { "Sync skipped: local data reset in progress" }
            return false
        }
        val client = pbProvider.client ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return false
        }
        val activeBinding = pbProvider.activeBinding ?: run {
            log.d { "Sync skipped: no active authenticated account boundary" }
            return false
        }
        if (PocketBaseClientProvider.bindingFor(client) != activeBinding) {
            log.d { "Sync skipped: active client has no stable account binding" }
            return false
        }
        if (!hasStableBoundary(client, allowTransition = false)) {
            log.d { "Sync skipped: active client does not match the durable account boundary" }
            return false
        }
        syncAllWithinMutation(client)
        return true
    }

    /** Pulls a newly activated account without pushing the replacement cache. */
    suspend fun initialPull(client: PocketbaseClient = pbProvider.client ?: error("No active PocketBase client")) =
        accountMutationGate.withExclusive {
            pbProvider.requireActiveBinding(client)
            initialPullWithinMutation(client)
        }

    /** Used by account transitions that already hold AccountMutationGate. */
    override suspend fun initialPullWithinMutation(client: PocketbaseClient) {
        pbProvider.requireActiveBinding(client)
        check(hasStableBoundary(client, allowTransition = true)) {
            "PocketBase initial-pull client does not match the durable account boundary"
        }
        if (resetInProgress.value) return
        syncMutex.withLock {
            if (resetInProgress.value) return
            if (seedExecutor?.isPending() == true) {
                seedExecutor.resume(client)
                return
            }
            val failures = mutableListOf<SyncCollectionFailure>()
            val boundary = pbProvider.activeBoundary()
            val failedPulls = mutableSetOf<String>()
            adapters.sortedBy { it.order }.forEach { adapter ->
                val collectionName = adapter.collectionName
                if (shouldSkipPull(collectionName, failedPulls)) {
                    log.w { "Skipping initial pull $collectionName because a parent collection pull failed" }
                } else {
                    runCatching { adapter.pullAll(client) }
                        .onFailure {
                            if (it is CancellationException) throw it
                            log.e(it) { "Initial pull $collectionName failed" }
                            failedPulls += collectionName
                            failures += SyncCollectionFailure(collectionName, "initial_pull", it, boundary)
                        }
                }
            }
            if (failures.isNotEmpty()) throw SyncException(failures)
        }
    }

    override suspend fun syncAllWithinMutation(client: PocketbaseClient) {
        pbProvider.requireActiveBinding(client)
        check(hasStableBoundary(client, allowTransition = false)) {
            "PocketBase sync client does not match the durable account boundary"
        }
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
    ): T = accountMutationGate.withExclusive {
        runExclusiveResetWithinAccountMutation(cancelPendingSync, clearLocalData)
    }

    /** Used by account-bound reset workflows that already hold AccountMutationGate. */
    suspend fun <T> runExclusiveResetWithinAccountMutation(
        cancelPendingSync: suspend () -> Unit = {},
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
        val boundary = pbProvider.activeBoundary()

        adapters.sortedBy { it.order }.forEach { adapter ->
            val collectionName = adapter.collectionName
            if (shouldSkipPull(collectionName, failedPulls)) {
                log.w { "Skipping pull $collectionName because a parent collection pull failed" }
            } else {
                runCatching { adapter.pullAll(client) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        log.e(it) { "Pull $collectionName failed" }
                        failedPulls += collectionName
                        failures += SyncCollectionFailure(collectionName, "pull", it, boundary)
                    }
            }

            if (shouldSkipPush(collectionName, failedPulls)) {
                log.w { "Skipping push $collectionName because pull dependencies failed" }
            } else {
                runCatching { adapter.pushAll(client) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        log.e(it) { "Push $collectionName failed" }
                        failures += SyncCollectionFailure(collectionName, "push", it, boundary)
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

    private suspend fun hasStableBoundary(
        client: PocketbaseClient,
        allowTransition: Boolean,
    ): Boolean {
        val stateStore = accountStateStore ?: return true
        val binding = stateStore.readCacheBinding() ?: return false
        if (PocketBaseClientProvider.bindingFor(client) != binding) return false
        val transition = stateStore.readTransition()
        if (transition == null) return true
        return allowTransition &&
            transition.purpose == AccountTransitionPurpose.ACCOUNT_CHANGE &&
            transition.phase == AccountTransitionPhase.NEEDS_ACTIVATION &&
            transition.destinationAccountId == binding.accountId &&
            transition.canonicalEndpoint == binding.canonicalEndpoint &&
            transition.serverInstanceId == binding.serverInstanceId &&
            transition.capabilityVersion == binding.capabilityVersion &&
            transition.boundaryEpoch == binding.boundaryEpoch
    }

    private companion object {
        const val COLLECTION_CATEGORIES = "categories"
        const val COLLECTION_TAGS = "tags"
        const val COLLECTION_TASKS = "tasks"
        const val COLLECTION_ATTACHMENTS = "attachments"
        const val COLLECTION_TASK_TAGS = "task_tags"
    }
}
