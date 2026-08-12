package com.udnahc.opentasks.data.auth

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Non-secret identity data returned by the PocketBase users collection. */
@Serializable
data class AuthenticatedAccount(
    val accountId: String,
    val email: String? = null,
    val displayName: String? = null,
)

@Serializable
enum class CacheMode {
    POCKETBASE,
    LOCAL_ONLY,
}

const val LOCAL_CACHE_OWNER_ID = "__opentasks_local__"

/** The durable proof that the one local cache belongs to one account boundary. */
@Serializable
data class CacheBinding(
    val canonicalEndpoint: String,
    val serverInstanceId: String,
    val accountId: String,
    val capabilityVersion: Int,
    val boundaryEpoch: Long,
    val mode: CacheMode = CacheMode.POCKETBASE,
)

/**
 * The non-secret account boundary carried by authenticated data and platform
 * work.  It deliberately contains no access token, password, or other
 * credential material.
 */
@Serializable
data class AccountBoundary(
    val canonicalEndpoint: String,
    val serverInstanceId: String,
    val accountId: String,
    val capabilityVersion: Int,
    val boundaryEpoch: Long,
    val mode: CacheMode = CacheMode.POCKETBASE,
) {
    fun matches(binding: CacheBinding): Boolean =
        canonicalEndpoint == binding.canonicalEndpoint &&
            serverInstanceId == binding.serverInstanceId &&
            accountId == binding.accountId &&
            capabilityVersion == binding.capabilityVersion &&
            boundaryEpoch == binding.boundaryEpoch &&
            mode == binding.mode
}

fun CacheBinding.asAccountBoundary(): AccountBoundary = AccountBoundary(
    canonicalEndpoint = canonicalEndpoint,
    serverInstanceId = serverInstanceId,
    accountId = accountId,
    capabilityVersion = capabilityVersion,
    boundaryEpoch = boundaryEpoch,
    mode = mode,
)

fun CacheBinding.isValidLocalBinding(): Boolean =
    mode == CacheMode.LOCAL_ONLY &&
        canonicalEndpoint.isEmpty() &&
        serverInstanceId.isEmpty() &&
        accountId == LOCAL_CACHE_OWNER_ID &&
        capabilityVersion == 0 &&
        boundaryEpoch > 0L

fun CacheBinding.isValidPocketBaseBinding(): Boolean =
    mode == CacheMode.POCKETBASE &&
        canonicalEndpoint.isNotBlank() &&
        serverInstanceId.isNotBlank() &&
        accountId.isNotBlank() &&
        accountId != LOCAL_CACHE_OWNER_ID &&
        capabilityVersion > 0 &&
        boundaryEpoch > 0L

fun CacheBinding.isValidActiveBinding(): Boolean =
    isValidLocalBinding() || isValidPocketBaseBinding()

@Serializable
enum class AccountTransitionPurpose {
    ACCOUNT_CHANGE,
    LOCAL_CLEAR,
    LOCAL_AUTHORITATIVE_REPLACEMENT,
}

@Serializable
enum class AccountTransitionPhase {
    PREPARED,
    NEEDS_ACTIVATION,
    PRE_RESET,
    FILES_PENDING,
    REMOTE_DELETE_PENDING,
    EXACT_SEED_PENDING,
}

/** Durable crash-recovery marker. The marker is never allowed to authorize task UI. */
@Serializable
data class AccountTransition(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val canonicalEndpoint: String,
    val serverInstanceId: String,
    val capabilityVersion: Int,
    val boundaryEpoch: Long,
    val phase: AccountTransitionPhase,
    val purpose: AccountTransitionPurpose = AccountTransitionPurpose.ACCOUNT_CHANGE,
)

enum class AccountSessionFreshness {
    ONLINE,
    OFFLINE,
}

enum class AccountReauthenticationReason {
    AUTHENTICATION_REJECTED,
    TOKEN_UNAVAILABLE,
    CACHE_BINDING_MISSING,
    CACHE_BINDING_MISMATCH,
    CAPABILITY_MISMATCH,
    LEGACY_CACHE_OWNERSHIP_UNPROVEN,
    PERSISTED_STATE_INVALID,
}

sealed interface AccountSessionState {
    data object Restoring : AccountSessionState

    data object SignedOut : AccountSessionState

    data class Authenticated(
        val account: AuthenticatedAccount,
        val binding: CacheBinding,
        val freshness: AccountSessionFreshness,
    ) : AccountSessionState

    data class LocalOnly(
        val binding: CacheBinding,
    ) : AccountSessionState

    data class Transitioning(val transition: AccountTransition) : AccountSessionState

    data class ReauthenticationRequired(
        val account: AuthenticatedAccount?,
        val reason: AccountReauthenticationReason,
        val canonicalEndpoint: String? = null,
    ) : AccountSessionState
}

fun AccountSessionState.activeBindingOrNull(): CacheBinding? = when (this) {
    is AccountSessionState.Authenticated -> binding
    is AccountSessionState.LocalOnly -> binding
    else -> null
}

fun AccountSessionState.authenticatedAccountOrNull(): AuthenticatedAccount? =
    (this as? AccountSessionState.Authenticated)?.account

/**
 * Stores only PocketBase auth tokens. Passwords are never represented by this
 * contract and are therefore never eligible for persistence.
 */
interface AuthTokenStore {
    val storageWarning: String?
        get() = null

    suspend fun readActiveToken(): String?
    suspend fun writeActiveToken(token: String)
    suspend fun clearActiveToken()

    suspend fun readPendingToken(): String?
    suspend fun writePendingToken(token: String)
    suspend fun clearPendingToken()

    /** Repeating promotion after a crash is safe and has no additional effect. */
    suspend fun promotePendingToken()

    suspend fun clearAllTokens()
}

class SecureTokenStoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

interface AccountMutationGate {
    suspend fun <T> withExclusive(block: suspend () -> T): T
}

/** One process-wide serialization boundary for account and cache mutations. */
class MutexAccountMutationGate : AccountMutationGate {
    private val mutex = Mutex()

    override suspend fun <T> withExclusive(block: suspend () -> T): T {
        val heldGate = currentCoroutineContext()[HeldAccountMutationGate]
        if (heldGate?.gate === this) return block()
        return mutex.withLock {
            withContext(HeldAccountMutationGate(this)) { block() }
        }
    }

    private class HeldAccountMutationGate(
        val gate: MutexAccountMutationGate,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<HeldAccountMutationGate>
    }
}
