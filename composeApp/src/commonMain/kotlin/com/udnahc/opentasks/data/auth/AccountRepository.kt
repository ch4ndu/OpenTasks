package com.udnahc.opentasks.data.auth

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.flow.StateFlow
import com.udnahc.opentasks.data.sync.ReplacementCollectionCount

data class LocalServerReplacementPreview(
    val canonicalEndpoint: String,
    val account: AuthenticatedAccount,
    val serverInstanceId: String,
    val authoritativeReplaceVersion: Int,
    val collectionCounts: List<ReplacementCollectionCount>,
    val attachmentCount: Int,
    internal val ownerInventoryFingerprint: String,
    internal val localInventoryFingerprint: String,
)

sealed interface LocalServerReplacementConfirmation {
    data object Started : LocalServerReplacementConfirmation
    data class PreviewChanged(val preview: LocalServerReplacementPreview) : LocalServerReplacementConfirmation
}

interface AccountRepository {
    val sessionState: StateFlow<AccountSessionState>

    suspend fun restoreSession(): AccountSessionState

    suspend fun startLocalOnly(): AccountSessionState

    suspend fun clearLocalData(): AccountSessionState

    suspend fun prepareLocalServerReplacement(
        endpoint: String,
        email: String,
        password: String,
    ): LocalServerReplacementPreview = error("Authoritative replacement is unavailable")

    suspend fun confirmLocalServerReplacement(): LocalServerReplacementConfirmation =
        error("Authoritative replacement is unavailable")

    suspend fun cancelLocalServerReplacementPreparation() = Unit

    suspend fun login(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState

    suspend fun reauthenticate(
        email: String,
        password: String,
    ): AccountSessionState

    suspend fun switchAccount(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState

    suspend fun logout(): AccountSessionState
}

interface AccountSyncCoordinator {
    suspend fun syncAllWithinMutation(client: PocketbaseClient)

    suspend fun initialPullWithinMutation(client: PocketbaseClient)
}

/** Narrow sync-to-account callback for a confirmed HTTP 401 on an active boundary. */
interface AccountAuthenticationRejectionHandler {
    /** Returns true only when the exact live authenticated boundary was transitioned. */
    suspend fun onAuthenticationRejected(boundary: AccountBoundary): Boolean
}

/**
 * Avoids a construction cycle: SyncService depends on this stable callback
 * boundary, while AccountRepositoryImpl registers itself once constructed.
 */
class AccountAuthenticationRejectionDispatcher : AccountAuthenticationRejectionHandler {
    private var handler: AccountAuthenticationRejectionHandler? = null

    fun register(handler: AccountAuthenticationRejectionHandler) {
        this.handler = handler
    }

    override suspend fun onAuthenticationRejected(boundary: AccountBoundary): Boolean =
        handler?.onAuthenticationRejected(boundary) ?: false
}
