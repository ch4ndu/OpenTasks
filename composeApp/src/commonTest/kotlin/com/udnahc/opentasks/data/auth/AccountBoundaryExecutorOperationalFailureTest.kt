package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.settings.AccountStateStore
import com.udnahc.opentasks.data.settings.LegacyCacheIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class AccountBoundaryExecutorOperationalFailureTest {
    private val binding = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 1L,
    )
    private val authenticated = AccountSessionState.Authenticated(
        account = AuthenticatedAccount("account-a"),
        binding = binding,
        freshness = AccountSessionFreshness.ONLINE,
    )

    @Test
    fun foregroundGuardOperationalFailureRejectsWithoutRunningCallback() = runTest {
        val repository = ExecutorTestAccountRepository(authenticated) { authenticated }
        val executor = AccountBoundaryExecutor(
            accountRepository = repository,
            accountBoundaryGuard = AccountBoundaryGuard(FailingBoundaryStateStore(binding, IllegalStateException("read"))),
            mutationGate = MutexAccountMutationGate(),
        )
        var callbackRuns = 0
        val expected = executor.captureForegroundBoundary() ?: error("expected foreground boundary")

        assertFailsWith<AccountBoundaryRejectedException> {
            executor.withForegroundBoundary(expected) { callbackRuns += 1 }
        }

        assertEquals(0, callbackRuns)
    }

    @Test
    fun restoreAndRestoredBoundaryOperationalFailuresReturnNullWithoutCallbacks() = runTest {
        val restoreFailure = IllegalStateException("restore")
        val restoreExecutor = AccountBoundaryExecutor(
            accountRepository = ExecutorTestAccountRepository(AccountSessionState.SignedOut) { throw restoreFailure },
            accountBoundaryGuard = AccountBoundaryGuard(FailingBoundaryStateStore(binding)),
            mutationGate = MutexAccountMutationGate(),
        )
        var restoreCallbackRuns = 0

        val restored = restoreExecutor.withActiveCacheBoundary { restoreCallbackRuns += 1 }

        assertNull(restored)
        assertEquals(0, restoreCallbackRuns)

        val validationExecutor = AccountBoundaryExecutor(
            accountRepository = ExecutorTestAccountRepository(AccountSessionState.SignedOut) { authenticated },
            accountBoundaryGuard = AccountBoundaryGuard(FailingBoundaryStateStore(binding, IllegalStateException("guard"))),
            mutationGate = MutexAccountMutationGate(),
        )
        var validationCallbackRuns = 0

        val validated = validationExecutor.withActiveCacheBoundary { validationCallbackRuns += 1 }

        assertNull(validated)
        assertEquals(0, validationCallbackRuns)
    }

    @Test
    fun operationalFailureHandlingDoesNotConvertCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val executor = AccountBoundaryExecutor(
            accountRepository = ExecutorTestAccountRepository(AccountSessionState.SignedOut) { throw cancellation },
            accountBoundaryGuard = AccountBoundaryGuard(FailingBoundaryStateStore(binding)),
            mutationGate = MutexAccountMutationGate(),
        )

        val thrown = assertFailsWith<CancellationException> {
            executor.withActiveCacheBoundary { error("must not run") }
        }

        assertSame(cancellation, thrown)
    }
}

private class ExecutorTestAccountRepository(
    initialState: AccountSessionState,
    private val restore: suspend () -> AccountSessionState,
) : AccountRepository {
    private val state = MutableStateFlow(initialState)

    override val sessionState: StateFlow<AccountSessionState> = state

    override suspend fun restoreSession(): AccountSessionState = restore().also { state.value = it }

    override suspend fun startLocalOnly(): AccountSessionState = error("not used")

    override suspend fun clearLocalData(): AccountSessionState = error("not used")

    override suspend fun login(endpoint: String, email: String, password: String): AccountSessionState = error("not used")

    override suspend fun reauthenticate(email: String, password: String): AccountSessionState = error("not used")

    override suspend fun switchAccount(endpoint: String, email: String, password: String): AccountSessionState = error("not used")

    override suspend fun logout(): AccountSessionState = error("not used")
}

private class FailingBoundaryStateStore(
    private var binding: CacheBinding?,
    private val readFailure: Throwable? = null,
) : AccountStateStore {
    override suspend fun readCacheBinding(): CacheBinding? = binding

    override suspend fun writeCacheBinding(binding: CacheBinding) {
        this.binding = binding
    }

    override suspend fun clearCacheBinding() {
        binding = null
    }

    override suspend fun readTransition(): AccountTransition? {
        readFailure?.let { throw it }
        return null
    }

    override suspend fun writeTransition(transition: AccountTransition) = Unit

    override suspend fun clearTransition() = Unit

    override suspend fun persistBindingAndTransition(binding: CacheBinding?, transition: AccountTransition?) {
        this.binding = binding
    }

    override suspend fun <T> replaceCacheAndPersist(
        binding: CacheBinding?,
        transition: AccountTransition?,
        clearCache: suspend () -> T,
    ): T = clearCache().also { this.binding = binding }

    override suspend fun readLegacyCacheIdentity(): LegacyCacheIdentity = LegacyCacheIdentity(null, null)

    override suspend fun readLastBoundaryEpoch(): Long = binding?.boundaryEpoch ?: 0L
}
