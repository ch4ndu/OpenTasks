package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.data.sync.canonicalUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InitializeSyncActionTest {
    @Test
    fun authenticatedStartupSyncUsesTheAlreadyActiveAccountBoundary() = runTest {
        val provider = PocketBaseClientProvider()
        val binding = CacheBinding(
            canonicalEndpoint = "https://tasks.example.com:443",
            serverInstanceId = "server",
            accountId = "account-a",
            capabilityVersion = 2,
            boundaryEpoch = 4L,
        )
        provider.activate(binding, "active-token")
        val syncService = SyncService(
            pbProvider = provider,
            adapters = emptyList(),
            accountMutationGate = MutexAccountMutationGate(),
        )

        InitializeSyncAction(syncService)()

        assertEquals(binding, provider.activeBinding)
        assertEquals(binding.canonicalEndpoint, provider.endpoint?.canonicalUrl)
    }
}
