package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TriggerSyncActionTest {
    @Test
    fun syncNowAndCancelPendingNeverCancelAnAlreadyClaimedDelayedPass() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = PocketBaseClientProvider().apply { configure("http://localhost:8090") }
        val service = SyncService(provider, emptyList(), accountMutationGate = MutexAccountMutationGate())
        val serialSync = Mutex()
        val delayedPassStarted = CompletableDeferred<Unit>()
        val releaseDelayedPass = CompletableDeferred<Unit>()
        var runCount = 0
        var delayedPassCancelled = false
        val trigger = TriggerSyncAction(
            pbProvider = provider,
            syncService = service,
            runSyncPass = {
                serialSync.withLock {
                    runCount += 1
                    if (runCount == 1) {
                        delayedPassStarted.complete(Unit)
                        try {
                            releaseDelayedPass.await()
                        } catch (error: CancellationException) {
                            delayedPassCancelled = true
                            throw error
                        }
                    }
                }
            },
            coroutineDispatcher = dispatcher,
            waitForDebounce = {},
        )

        trigger.triggerSync()
        runCurrent()
        delayedPassStarted.await()

        trigger.cancelPendingSync()
        assertFalse(delayedPassCancelled)

        val immediate = async { trigger.syncNow() }
        runCurrent()
        assertEquals(1, runCount)

        releaseDelayedPass.complete(Unit)
        advanceUntilIdle()
        immediate.await()

        assertFalse(delayedPassCancelled)
        assertEquals(2, runCount)
    }
}
