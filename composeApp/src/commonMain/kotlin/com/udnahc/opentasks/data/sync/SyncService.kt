package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.auth.AccountAuthenticationRejectionHandler
import com.udnahc.opentasks.data.auth.AccountSyncCoordinator
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AccountTransitionPurpose
import com.udnahc.opentasks.data.settings.AccountStateStore
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val passContextFactory: SyncPassContextFactory = SyncPassContextFactory(),
    private val authenticationRejectionHandler: AccountAuthenticationRejectionHandler? = null,
) : AccountSyncCoordinator {
    private val syncMutex = Mutex()
    private val resetMutex = Mutex()
    private val resetInProgress = MutableStateFlow(false)
    private val _outcome = MutableStateFlow<SyncOutcome>(SyncOutcome.Idle)

    val outcome: StateFlow<SyncOutcome> = _outcome.asStateFlow()

    suspend fun syncAll() = accountMutationGate.withExclusive {
        if (resetInProgress.value) {
            log.d { "Sync skipped: local data reset in progress" }
            return@withExclusive
        }
        val activeMetadata = pbProvider.activeClientMetadata() ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return@withExclusive
        }
        if (activeMetadata.binding == null) {
            log.d { "Sync skipped: no active authenticated account boundary" }
            return@withExclusive
        }
        if (!hasStableBoundary(activeMetadata, allowTransition = false)) {
            log.d { "Sync skipped: active client does not match the durable account boundary" }
            return@withExclusive
        }
        syncAllWithinMutation(activeMetadata.client)
    }

    suspend fun syncAll(client: PocketbaseClient) = accountMutationGate.withExclusive {
        val activeMetadata = pbProvider.requireActiveClientMetadata(client)
        check(hasStableBoundary(activeMetadata, allowTransition = false)) {
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
        val activeMetadata = pbProvider.activeClientMetadata() ?: run {
            log.d { "Sync skipped: no PocketBase client" }
            return false
        }
        if (activeMetadata.binding == null) {
            log.d { "Sync skipped: no active authenticated account boundary" }
            return false
        }
        if (!hasStableBoundary(activeMetadata, allowTransition = false)) {
            log.d { "Sync skipped: active client does not match the durable account boundary" }
            return false
        }
        syncAllWithinMutation(activeMetadata.client)
        return true
    }

    /** Pulls a newly activated account without pushing the replacement cache. */
    suspend fun initialPull(client: PocketbaseClient = pbProvider.client ?: error("No active PocketBase client")) =
        accountMutationGate.withExclusive {
            pbProvider.requireActiveClientMetadata(client)
            initialPullWithinMutation(client)
        }

    /** Used by account transitions that already hold AccountMutationGate. */
    override suspend fun initialPullWithinMutation(client: PocketbaseClient) {
        val activeMetadata = pbProvider.requireActiveClientMetadata(client)
        check(hasStableBoundary(activeMetadata, allowTransition = true)) {
            "PocketBase initial-pull client does not match the durable account boundary"
        }
        if (resetInProgress.value) return
        val startingBoundary = activeMetadata.boundary
            ?: throw IllegalStateException("PocketBase initial pull has no active account boundary")
        syncMutex.withLock {
            runWithOutcome(startingBoundary) {
                if (resetInProgress.value) {
                    return@runWithOutcome
                }
                val pass = passContextFactory.create(client)
                if (seedExecutor?.isPending() == true) {
                    seedExecutor.resume(pass)
                    return@runWithOutcome
                }
                val failures = mutableListOf<SyncCollectionFailure>()
                val failedPulls = mutableSetOf<String>()
                adapters.sortedBy { it.order }.forEach { adapter ->
                    val collectionName = adapter.collectionName
                    if (shouldSkipPull(collectionName, failedPulls)) {
                        log.w { "Skipping initial pull $collectionName because a parent collection pull failed" }
                    } else {
                        runCatching { adapter.pullAll(pass) }
                            .onFailure {
                                if (it is CancellationException) throw it
                                it.rethrowSyncAuthenticationRejected()
                                log.e { "Initial pull $collectionName failed" }
                                failedPulls += collectionName
                                failures += SyncCollectionFailure(collectionName, "initial_pull", it, startingBoundary)
                            }
                    }
                }
                if (failures.isNotEmpty()) throw SyncException(failures)
            }
        }
    }

    override suspend fun syncAllWithinMutation(client: PocketbaseClient) {
        val activeMetadata = pbProvider.requireActiveClientMetadata(client)
        check(hasStableBoundary(activeMetadata, allowTransition = false)) {
            "PocketBase sync client does not match the durable account boundary"
        }
        if (resetInProgress.value) {
            log.d { "Sync skipped: local data reset in progress" }
            return
        }
        val startingBoundary = activeMetadata.boundary
            ?: throw IllegalStateException("PocketBase sync has no active account boundary")
        syncMutex.withLock {
            runWithOutcome(startingBoundary) {
                if (resetInProgress.value) {
                    log.d { "Sync skipped: local data reset in progress" }
                    return@runWithOutcome
                }
                val pass = passContextFactory.create(client)
                if (seedExecutor?.isPending() == true) {
                    seedExecutor.resume(pass)
                    return@runWithOutcome
                }
                log.d { "Sync started" }
                val passFailures = syncPass(pass, startingBoundary)
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
            _outcome.value = SyncOutcome.Idle
        }
    }

    private suspend fun syncPass(
        pass: SyncPassContext,
        boundary: com.udnahc.opentasks.data.auth.AccountBoundary,
    ): List<SyncCollectionFailure> {
        val failures = mutableListOf<SyncCollectionFailure>()
        val failedPulls = mutableSetOf<String>()

        adapters.sortedBy { it.order }.forEach { adapter ->
            val collectionName = adapter.collectionName
            if (shouldSkipPull(collectionName, failedPulls)) {
                log.w { "Skipping pull $collectionName because a parent collection pull failed" }
            } else {
                runCatching { adapter.pullAll(pass) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        it.rethrowSyncAuthenticationRejected()
                        log.e { "Pull $collectionName failed" }
                        failedPulls += collectionName
                        failures += SyncCollectionFailure(collectionName, "pull", it, boundary)
                    }
            }

            if (shouldSkipPush(collectionName, failedPulls)) {
                log.w { "Skipping push $collectionName because pull dependencies failed" }
            } else {
                runCatching { adapter.pushAll(pass) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        it.rethrowSyncAuthenticationRejected()
                        log.e { "Push $collectionName failed" }
                        failures += SyncCollectionFailure(collectionName, "push", it, boundary)
                    }
            }
        }

        return failures
    }

    private suspend fun <T> runWithOutcome(
        startingBoundary: com.udnahc.opentasks.data.auth.AccountBoundary,
        block: suspend () -> T,
    ): T {
        _outcome.value = SyncOutcome.Syncing
        return try {
            block().also { _outcome.value = SyncOutcome.Success }
        } catch (error: CancellationException) {
            _outcome.value = SyncOutcome.Idle
            throw error
        } catch (error: Throwable) {
            val authenticationRejection = error.findSyncAuthenticationRejected()
            if (authenticationRejection != null) {
                val transitioned = try {
                    authenticationRejectionHandler?.onAuthenticationRejected(startingBoundary) ?: false
                } catch (callbackError: CancellationException) {
                    _outcome.value = SyncOutcome.Idle
                    throw callbackError
                } catch (callbackError: Throwable) {
                    log.e { "Failed to apply the sync authentication-rejection transition" }
                    false
                }
                _outcome.value = if (transitioned) {
                    SyncOutcome.ReauthenticationRequired
                } else {
                    SyncOutcome.Failed
                }
                throw authenticationRejection
            }
            _outcome.value = SyncOutcome.Failed
            throw error
        }
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
        clientMetadata: PocketBaseClientMetadata,
        allowTransition: Boolean,
    ): Boolean {
        val stateStore = accountStateStore ?: return true
        val binding = stateStore.readCacheBinding() ?: return false
        if (clientMetadata.binding != binding) return false
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
