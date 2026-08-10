package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.settings.AccountStateStore
import com.udnahc.opentasks.data.settings.LegacyCacheIdentity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountBoundaryGuardTest {
    private val binding = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 7,
    )

    @Test
    fun activeBindingAcceptsOnlyMatchingAccountAndEpoch() = runTest {
        val guard = AccountBoundaryGuard(FakeAccountStateStore(binding = binding))

        assertEquals(binding.asAccountBoundary(), guard.activeBoundary())
        assertTrue(guard.matches("account-a", 7))
        assertFalse(guard.matches("account-b", 7))
        assertFalse(guard.matches("account-a", 6))
        assertFalse(guard.matches(null, 7))
        assertFalse(guard.matches("account-a", 0))
    }

    @Test
    fun transitionAndMissingBindingFailClosed() = runTest {
        val transition = AccountTransition(
            sourceAccountId = "account-a",
            destinationAccountId = "account-b",
            canonicalEndpoint = binding.canonicalEndpoint,
            serverInstanceId = binding.serverInstanceId,
            capabilityVersion = binding.capabilityVersion,
            boundaryEpoch = 8,
            phase = AccountTransitionPhase.PREPARED,
        )

        assertNull(AccountBoundaryGuard(FakeAccountStateStore()).activeBoundary())
        assertNull(AccountBoundaryGuard(FakeAccountStateStore(binding, transition)).activeBoundary())
        assertFalse(AccountBoundaryGuard(FakeAccountStateStore(binding, transition)).matches("account-a", 7))
    }
}

private class FakeAccountStateStore(
    private var binding: CacheBinding? = null,
    private var transition: AccountTransition? = null,
) : AccountStateStore {
    override suspend fun readCacheBinding(): CacheBinding? = binding
    override suspend fun writeCacheBinding(binding: CacheBinding) { this.binding = binding }
    override suspend fun clearCacheBinding() { binding = null }
    override suspend fun readTransition(): AccountTransition? = transition
    override suspend fun writeTransition(transition: AccountTransition) { this.transition = transition }
    override suspend fun clearTransition() { transition = null }

    override suspend fun persistBindingAndTransition(
        binding: CacheBinding?,
        transition: AccountTransition?,
    ) {
        this.binding = binding
        this.transition = transition
    }

    override suspend fun <T> replaceCacheAndPersist(
        binding: CacheBinding?,
        transition: AccountTransition?,
        clearCache: suspend () -> T,
    ): T {
        val result = clearCache()
        persistBindingAndTransition(binding, transition)
        return result
    }

    override suspend fun readLegacyCacheIdentity(): LegacyCacheIdentity = LegacyCacheIdentity(null, null)
    override suspend fun readLastBoundaryEpoch(): Long = binding?.boundaryEpoch ?: transition?.boundaryEpoch ?: 0
}
