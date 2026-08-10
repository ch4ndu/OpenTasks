package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.LocalSyncDefaults
import com.udnahc.opentasks.data.settings.AccountStateStore
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.data.sync.canonicalUrl
import com.udnahc.opentasks.data.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("AccountRepository")

internal class AccountRepositoryImpl(
    private val tokenStore: AuthTokenStore,
    private val stateStore: AccountStateStore,
    private val authenticator: AccountAuthenticator,
    private val cacheInspector: AccountCacheInspectorContract,
    private val cacheResetter: AccountCacheResetterContract,
    private val mutationGate: AccountMutationGate,
    private val pbProvider: PocketBaseClientProvider,
    private val syncService: AccountSyncCoordinator,
    private val reminderScheduler: ReminderScheduler,
    private val buildTimePocketBaseUrl: String = LocalSyncDefaults.POCKETBASE_URL,
) : AccountRepository {
    private val _sessionState = MutableStateFlow<AccountSessionState>(AccountSessionState.Restoring)

    override val sessionState: StateFlow<AccountSessionState> = _sessionState.asStateFlow()

    override suspend fun restoreSession(): AccountSessionState {
        log.d { "Session restore waiting for the account mutation gate" }
        return mutationGate.withExclusive {
            log.d { "Session restore acquired the account mutation gate" }
            val live = _sessionState.value as? AccountSessionState.Authenticated
            if (live != null) {
                log.d { "Session restore reused the authenticated session published by an earlier caller" }
                return@withExclusive live
            }
            publish(AccountSessionState.Restoring)
            try {
                restoreWithinMutation()
            } catch (error: SecureTokenStoreException) {
                log.e(error) { "Session restore found unreadable secure credential state" }
                pbProvider.disconnect()
                publish(
                    AccountSessionState.ReauthenticationRequired(
                        account = null,
                        reason = AccountReauthenticationReason.PERSISTED_STATE_INVALID,
                    )
                )
            } catch (error: IllegalArgumentException) {
                log.e(error) { "Session restore found invalid persisted account state" }
                pbProvider.disconnect()
                publish(
                    AccountSessionState.ReauthenticationRequired(
                        account = null,
                        reason = AccountReauthenticationReason.PERSISTED_STATE_INVALID,
                    )
                )
            } catch (error: CancellationException) {
                log.d { "Session restore was cancelled" }
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Session restore failed" }
                throw error
            }
        }
    }

    override suspend fun login(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState = mutationGate.withExclusive {
        val credential = authenticator.authenticate(canonicalizeAccountEndpoint(endpoint), email, password)
        applyCredentialWithinMutation(credential)
    }

    override suspend fun switchAccount(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState = mutationGate.withExclusive {
        val credential = authenticator.authenticate(canonicalizeAccountEndpoint(endpoint), email, password)
        val currentBinding = stateStore.readCacheBinding()
        if (currentBinding == null || currentBinding.accountId == credential.account.accountId) {
            applyCredentialWithinMutation(credential)
        } else {
            switchWithCredentialWithinMutation(currentBinding, credential)
        }
    }

    override suspend fun reauthenticate(
        email: String,
        password: String,
    ): AccountSessionState = mutationGate.withExclusive {
        val binding = stateStore.readCacheBinding()
            ?: return@withExclusive publish(
                AccountSessionState.ReauthenticationRequired(
                    account = null,
                    reason = AccountReauthenticationReason.CACHE_BINDING_MISSING,
                )
            )
        val transition = stateStore.readTransition()
        if (transition?.phase == AccountTransitionPhase.PREPARED) {
            return@withExclusive publish(AccountSessionState.Transitioning(transition))
        }
        val credential = try {
            authenticator.authenticate(canonicalizeAccountEndpoint(binding.canonicalEndpoint), email, password)
        } catch (error: AccountAuthenticationRejectedException) {
            return@withExclusive publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.AUTHENTICATION_REJECTED,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        }
        if (credential.account.accountId != binding.accountId) {
            return@withExclusive publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.CAPABILITY_MISMATCH,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        }
        completeSameAccountCredentialWithinMutation(binding, credential, transition)
    }

    override suspend fun logout(): AccountSessionState = mutationGate.withExclusive {
        val transition = stateStore.readTransition()
        if (transition != null) {
            return@withExclusive publish(AccountSessionState.Transitioning(transition))
        }
        val binding = stateStore.readCacheBinding()
        if (binding == null) {
            pbProvider.disconnect()
            tokenStore.clearAllTokens()
            stateStore.clearTransition()
            return@withExclusive publish(AccountSessionState.SignedOut)
        }

        val activeClient = refreshSourceWithinMutation(binding, "Logout")
        syncService.syncAllWithinMutation(activeClient)
        val snapshot = cacheInspector.inspect()
        if (snapshot.hasUnsyncedRows) {
            throw AccountTransitionBlockedException("Logout requires a fully synchronized source cache")
        }

        val prepared = AccountTransition(
            sourceAccountId = binding.accountId,
            destinationAccountId = "",
            canonicalEndpoint = binding.canonicalEndpoint,
            serverInstanceId = binding.serverInstanceId,
            capabilityVersion = binding.capabilityVersion,
            boundaryEpoch = nextBoundaryEpoch(binding.boundaryEpoch),
            phase = AccountTransitionPhase.PREPARED,
        )
        stateStore.writeTransition(prepared)
        publish(AccountSessionState.Transitioning(prepared))
        reminderScheduler.cancelAllAccountReminders()
        var durableTransition = prepared
        try {
            val needsActivation = prepared.copy(phase = AccountTransitionPhase.NEEDS_ACTIVATION)
            cacheResetter.replaceCacheWithinMutation(
                binding = null,
                transition = needsActivation,
            )
            durableTransition = needsActivation
            cacheResetter.clearAttachmentFilesWithinMutation()
            tokenStore.clearAllTokens()
            stateStore.clearTransition()
            pbProvider.disconnect()
            publish(AccountSessionState.SignedOut)
        } catch (error: CancellationException) {
            publish(AccountSessionState.Transitioning(durableTransition))
            throw error
        } catch (error: Throwable) {
            publish(AccountSessionState.Transitioning(durableTransition))
            throw error
        }
    }

    private suspend fun applyCredentialWithinMutation(credential: AccountCredential): AccountSessionState {
        val transition = stateStore.readTransition()
        if (transition != null) {
            return publish(AccountSessionState.Transitioning(transition))
        }
        val binding = stateStore.readCacheBinding()
        if (binding != null && binding.accountId != credential.account.accountId) {
            return switchWithCredentialWithinMutation(binding, credential)
        }
        if (binding == null) {
            tokenStore.writePendingToken(credential.token)
            var durableTransition: AccountTransition? = null
            try {
                val nextBinding = adoptLegacyCacheOrReset(credential)
                val nextTransition = transitionFor(
                    sourceAccountId = "",
                    credential = credential,
                    boundaryEpoch = nextBinding.boundaryEpoch,
                    phase = AccountTransitionPhase.NEEDS_ACTIVATION,
                )
                stateStore.persistBindingAndTransition(nextBinding, nextTransition)
                durableTransition = nextTransition
                tokenStore.promotePendingToken()
                val client = pbProvider.activate(nextBinding, credential.token)
                syncService.initialPullWithinMutation(client)
                stateStore.clearTransition()
                return publish(
                    AccountSessionState.Authenticated(
                        credential.account,
                        nextBinding,
                        AccountSessionFreshness.ONLINE,
                    )
                )
            } catch (error: CancellationException) {
                durableTransition?.let { publish(AccountSessionState.Transitioning(it)) }
                throw error
            } catch (error: Throwable) {
                if (durableTransition != null) {
                    publish(AccountSessionState.Transitioning(durableTransition))
                } else {
                    tokenStore.clearPendingToken()
                }
                throw error
            }
        }
        return completeSameAccountCredentialWithinMutation(binding, credential, null)
    }

    private suspend fun completeSameAccountCredentialWithinMutation(
        binding: CacheBinding,
        credential: AccountCredential,
        transition: AccountTransition?,
    ): AccountSessionState {
        val validatedBinding = binding.validateFor(credential)
        tokenStore.writePendingToken(credential.token)
        try {
            stateStore.writeCacheBinding(validatedBinding)
            tokenStore.promotePendingToken()
            val client = pbProvider.activate(validatedBinding, credential.token)
            if (transition?.phase == AccountTransitionPhase.NEEDS_ACTIVATION) {
                syncService.initialPullWithinMutation(client)
                stateStore.clearTransition()
            }
            return publish(
                AccountSessionState.Authenticated(
                    credential.account,
                    validatedBinding,
                    AccountSessionFreshness.ONLINE,
                )
            )
        } catch (error: CancellationException) {
            if (transition?.phase == AccountTransitionPhase.NEEDS_ACTIVATION) {
                publish(AccountSessionState.Transitioning(transition))
            }
            throw error
        } catch (error: Throwable) {
            if (transition?.phase == AccountTransitionPhase.NEEDS_ACTIVATION) {
                publish(AccountSessionState.Transitioning(transition))
            } else {
                tokenStore.clearPendingToken()
            }
            throw error
        }
    }

    private suspend fun switchWithCredentialWithinMutation(
        sourceBinding: CacheBinding,
        credential: AccountCredential,
    ): AccountSessionState {
        if (sourceBinding.accountId == credential.account.accountId) {
            return completeSameAccountCredentialWithinMutation(sourceBinding, credential, null)
        }
        requireSameServer(sourceBinding, credential)
        val sourceClient = refreshSourceWithinMutation(sourceBinding, "Account switching")

        syncService.syncAllWithinMutation(sourceClient)
        val snapshot = cacheInspector.inspect()
        if (snapshot.hasUnsyncedRows) {
            throw AccountTransitionBlockedException("Account switching requires a fully synchronized source cache")
        }

        val destinationBinding = CacheBinding(
            canonicalEndpoint = credential.endpoint.canonicalUrl,
            serverInstanceId = credential.capability.serverInstanceId,
            accountId = credential.account.accountId,
            capabilityVersion = credential.capability.capabilityVersion,
            boundaryEpoch = nextBoundaryEpoch(sourceBinding.boundaryEpoch),
        )
        val prepared = transitionFor(
            sourceAccountId = sourceBinding.accountId,
            credential = credential,
            boundaryEpoch = destinationBinding.boundaryEpoch,
            phase = AccountTransitionPhase.PREPARED,
        )
        tokenStore.writePendingToken(credential.token)
        stateStore.writeTransition(prepared)
        publish(AccountSessionState.Transitioning(prepared))
        reminderScheduler.cancelAllAccountReminders()
        var durableTransition = prepared
        try {
            val needsActivation = prepared.copy(phase = AccountTransitionPhase.NEEDS_ACTIVATION)
            cacheResetter.replaceCacheWithinMutation(
                binding = destinationBinding,
                transition = needsActivation,
            )
            durableTransition = needsActivation
            cacheResetter.clearAttachmentFilesWithinMutation()
            tokenStore.promotePendingToken()
            val destinationClient = pbProvider.activate(destinationBinding, credential.token)
            publish(AccountSessionState.Transitioning(durableTransition))
            syncService.initialPullWithinMutation(destinationClient)
            stateStore.clearTransition()
            return publish(
                AccountSessionState.Authenticated(
                    credential.account,
                    destinationBinding,
                    AccountSessionFreshness.ONLINE,
                )
            )
        } catch (error: CancellationException) {
            publish(AccountSessionState.Transitioning(durableTransition))
            throw error
        } catch (error: Throwable) {
            log.w { "Account transition remains durable for startup recovery" }
            publish(AccountSessionState.Transitioning(durableTransition))
            throw error
        }
    }

    private suspend fun restoreWithinMutation(): AccountSessionState {
        val transition = stateStore.readTransition()
        if (transition != null) {
            log.d { "Session restore found durable transition phase=${transition.phase}" }
            return recoverTransitionWithinMutation(transition)
        }

        val binding = stateStore.readCacheBinding()
        val activeToken = tokenStore.readActiveToken()
        log.d {
            "Session restore loaded persisted state; binding=${binding != null}, " +
                "activeToken=${activeToken != null}"
        }
        if (binding == null && activeToken == null) {
            log.d { "Session restore found no persisted account" }
            tokenStore.clearPendingToken()
            pbProvider.disconnect()
            return publish(AccountSessionState.SignedOut)
        }
        if (binding == null) {
            log.w { "Session restore found a token without a cache binding" }
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = null,
                    reason = AccountReauthenticationReason.CACHE_BINDING_MISSING,
                )
            )
        }
        if (activeToken == null) {
            log.w { "Session restore found a cache binding without an active token" }
            tokenStore.clearPendingToken()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.TOKEN_UNAVAILABLE,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        }
        // Without a durable transition, the active token and binding are the
        // source of truth. A leftover pending token is never allowed to alter
        // normal startup restoration.
        tokenStore.clearPendingToken()

        val endpoint = try {
            canonicalizeAccountEndpoint(binding.canonicalEndpoint)
        } catch (error: IllegalArgumentException) {
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.CACHE_BINDING_MISMATCH,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        }
        log.d {
            "Session restore refreshing PocketBase credentials at " +
                "${endpoint.protocol.name.lowercase()}://${endpoint.host}:${endpoint.port}"
        }
        val refreshed = try {
            authenticator.refresh(endpoint, activeToken)
        } catch (error: AccountAuthenticationRejectedException) {
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.AUTHENTICATION_REJECTED,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        } catch (error: AccountCapabilityRejectedException) {
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.CAPABILITY_MISMATCH,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        } catch (error: AccountConnectivityException) {
            pbProvider.activate(binding, activeToken)
            log.w {
                "PocketBase account refresh unavailable; restoring the proven cache boundary offline"
            }
            return publish(
                AccountSessionState.Authenticated(
                    AuthenticatedAccount(binding.accountId),
                    binding,
                    AccountSessionFreshness.OFFLINE,
                )
            )
        }

        log.d { "Session restore refreshed credentials and capability metadata" }
        if (!binding.matches(refreshed)) {
            log.w { "Session restore rejected refreshed credentials that did not match the cache binding" }
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = refreshed.account,
                    reason = AccountReauthenticationReason.CAPABILITY_MISMATCH,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        }
        tokenStore.writeActiveToken(refreshed.token)
        tokenStore.clearPendingToken()
        pbProvider.activate(binding, refreshed.token)
        log.d { "Session restore activated the refreshed PocketBase client" }
        return publish(
            AccountSessionState.Authenticated(
                refreshed.account,
                binding,
                AccountSessionFreshness.ONLINE,
            )
        )
    }

    private suspend fun recoverTransitionWithinMutation(transition: AccountTransition): AccountSessionState {
        if (transition.phase == AccountTransitionPhase.PREPARED) {
            tokenStore.clearPendingToken()
            stateStore.clearTransition()
            return restoreWithinMutation()
        }
        if (transition.destinationAccountId.isBlank()) {
            pbProvider.disconnect()
            cacheResetter.clearAttachmentFilesWithinMutation()
            tokenStore.clearAllTokens()
            stateStore.clearCacheBinding()
            stateStore.clearTransition()
            return publish(AccountSessionState.SignedOut)
        }

        val binding = stateStore.readCacheBinding()
        if (binding == null ||
            binding.accountId != transition.destinationAccountId ||
            binding.canonicalEndpoint != transition.canonicalEndpoint ||
            binding.serverInstanceId != transition.serverInstanceId ||
            binding.capabilityVersion != transition.capabilityVersion ||
            binding.boundaryEpoch != transition.boundaryEpoch
        ) {
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(transition.destinationAccountId),
                    reason = AccountReauthenticationReason.CACHE_BINDING_MISMATCH,
                    canonicalEndpoint = transition.canonicalEndpoint,
                )
            )
        }
        if (transition.sourceAccountId.isNotBlank()) {
            try {
                cacheResetter.clearAttachmentFilesWithinMutation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w { "Account transition is waiting for attachment cleanup" }
                pbProvider.disconnect()
                return publish(AccountSessionState.Transitioning(transition))
            }
        }
        // After cache replacement the pending slot contains the destination
        // token while the active slot may still contain the source token.
        val token = tokenStore.readPendingToken() ?: tokenStore.readActiveToken()
        if (token == null) {
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.TOKEN_UNAVAILABLE,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        }
        val refreshed = try {
            authenticator.refresh(canonicalizeAccountEndpoint(binding.canonicalEndpoint), token)
        } catch (error: AccountAuthenticationRejectedException) {
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.AUTHENTICATION_REJECTED,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        } catch (error: AccountCapabilityRejectedException) {
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = AuthenticatedAccount(binding.accountId),
                    reason = AccountReauthenticationReason.CAPABILITY_MISMATCH,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        } catch (error: AccountConnectivityException) {
            pbProvider.disconnect()
            return publish(AccountSessionState.Transitioning(transition))
        }
        if (!binding.matches(refreshed)) {
            tokenStore.clearAllTokens()
            pbProvider.disconnect()
            return publish(
                AccountSessionState.ReauthenticationRequired(
                    account = refreshed.account,
                    reason = AccountReauthenticationReason.CAPABILITY_MISMATCH,
                    canonicalEndpoint = binding.canonicalEndpoint,
                )
            )
        }
        tokenStore.writeActiveToken(refreshed.token)
        tokenStore.clearPendingToken()
        val client = pbProvider.activate(binding, refreshed.token)
        return try {
            publish(AccountSessionState.Transitioning(transition))
            syncService.initialPullWithinMutation(client)
            stateStore.clearTransition()
            publish(
                AccountSessionState.Authenticated(
                    refreshed.account,
                    binding,
                    AccountSessionFreshness.ONLINE,
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Account transition recovery remains pending" }
            publish(AccountSessionState.Transitioning(transition))
        }
    }

    private suspend fun adoptLegacyCacheOrReset(credential: AccountCredential): CacheBinding {
        val snapshot = cacheInspector.inspect()
        if (!snapshot.isPristineInboxOnly && !legacyOwnershipIsProven(credential)) {
            if (snapshot.hasUnsyncedRows) {
                throw LegacyCacheOwnershipException(credential.account, hasUnsyncedRows = true)
            }
            cacheResetter.resetWithinMutation()
        }
        return CacheBinding(
            canonicalEndpoint = credential.endpoint.canonicalUrl,
            serverInstanceId = credential.capability.serverInstanceId,
            accountId = credential.account.accountId,
            capabilityVersion = credential.capability.capabilityVersion,
            boundaryEpoch = nextBoundaryEpoch(0L),
        )
    }

    private suspend fun legacyOwnershipIsProven(credential: AccountCredential): Boolean {
        if (credential.capability.legacyOwnerAccount != credential.account.accountId) return false
        val legacyIdentity = stateStore.readLegacyCacheIdentity()
        val serverMatches = legacyIdentity.serverInstanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { it == credential.capability.serverInstanceId }
        if (serverMatches == true) return true
        if (!legacyIdentity.serverInstanceId.isNullOrBlank()) return false

        val metadataEndpoint = runCatching {
            canonicalizeAccountEndpoint(credential.capability.legacyEndpoint).canonicalUrl
        }.getOrNull() ?: return false
        val savedEndpoint = legacyIdentity.canonicalEndpoint
            ?.let { runCatching { canonicalizeAccountEndpoint(it).canonicalUrl }.getOrNull() }
        val buildEndpoint = buildTimePocketBaseUrl
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { canonicalizeAccountEndpoint(it).canonicalUrl }.getOrNull() }
        return savedEndpoint == metadataEndpoint && buildEndpoint == metadataEndpoint
    }

    private fun transitionFor(
        sourceAccountId: String,
        credential: AccountCredential,
        boundaryEpoch: Long,
        phase: AccountTransitionPhase,
    ): AccountTransition = AccountTransition(
        sourceAccountId = sourceAccountId,
        destinationAccountId = credential.account.accountId,
        canonicalEndpoint = credential.endpoint.canonicalUrl,
        serverInstanceId = credential.capability.serverInstanceId,
        capabilityVersion = credential.capability.capabilityVersion,
        boundaryEpoch = boundaryEpoch,
        phase = phase,
    )

    private suspend fun nextBoundaryEpoch(previousEpoch: Long): Long {
        val current = maxOf(previousEpoch, stateStore.readLastBoundaryEpoch())
        if (current == Long.MAX_VALUE) {
            throw SecureTokenStoreException("Account boundary epoch is exhausted")
        }
        return current + 1L
    }

    private suspend fun refreshSourceWithinMutation(
        binding: CacheBinding,
        operation: String,
    ): io.github.agrevster.pocketbaseKotlin.PocketbaseClient {
        val activeToken = tokenStore.readActiveToken()
            ?: throw AccountTransitionBlockedException("$operation requires source-account credentials")
        val refreshed = authenticator.refresh(
            canonicalizeAccountEndpoint(binding.canonicalEndpoint),
            activeToken,
        )
        if (!binding.matches(refreshed)) {
            throw AccountTransitionBlockedException("$operation could not prove the source account boundary")
        }
        tokenStore.writeActiveToken(refreshed.token)
        return pbProvider.activate(binding, refreshed.token)
    }

    private fun requireSameServer(
        sourceBinding: CacheBinding,
        credential: AccountCredential,
    ) {
        if (sourceBinding.canonicalEndpoint != credential.endpoint.canonicalUrl ||
            sourceBinding.serverInstanceId != credential.capability.serverInstanceId ||
            sourceBinding.capabilityVersion != credential.capability.capabilityVersion
        ) {
            throw AccountTransitionBlockedException(
                "Account switching is limited to the active PocketBase server",
            )
        }
    }

    private fun CacheBinding.validateFor(credential: AccountCredential): CacheBinding {
        if (!matches(credential)) {
            throw AccountCapabilityRejectedException("PocketBase capability does not match the cache binding")
        }
        return copy(canonicalEndpoint = credential.endpoint.canonicalUrl)
    }

    private fun CacheBinding.matches(credential: AccountCredential): Boolean =
        canonicalEndpoint == credential.endpoint.canonicalUrl &&
            serverInstanceId == credential.capability.serverInstanceId &&
            accountId == credential.account.accountId &&
            capabilityVersion == credential.capability.capabilityVersion

    private fun publish(state: AccountSessionState): AccountSessionState {
        log.d { "Publishing account session state=${state.diagnosticName()}" }
        _sessionState.value = state
        return state
    }
}

private fun AccountSessionState.diagnosticName(): String = when (this) {
    AccountSessionState.Restoring -> "restoring"
    AccountSessionState.SignedOut -> "signed-out"
    is AccountSessionState.Authenticated -> "authenticated-${freshness.name.lowercase()}"
    is AccountSessionState.ReauthenticationRequired -> "reauthentication-${reason.name.lowercase()}"
    is AccountSessionState.Transitioning -> "transitioning-${transition.phase.name.lowercase()}"
}
