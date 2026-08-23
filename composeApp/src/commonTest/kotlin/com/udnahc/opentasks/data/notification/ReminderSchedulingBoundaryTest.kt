package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReminderSchedulingBoundaryTest {
    private val boundary = AccountBoundary(
        canonicalEndpoint = "https://tasks.example.com",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 1L,
    )

    @Test
    fun queuedAccountAArmDoesNotRunAfterTransitionToAccountB() = runTest {
        val gate = MutexAccountMutationGate()
        var activeBoundary: AccountBoundary? = boundary
        var arms = 0
        val gateEntered = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        val holder = launch {
            gate.withExclusive {
                gateEntered.complete(Unit)
                releaseGate.await()
            }
        }
        gateEntered.await()
        val arm = async {
            assertFailsWith<IllegalStateException> {
                withHeldReminderBoundary(
                    mutationGate = gate,
                    activeBoundary = { activeBoundary },
                    expectedBoundary = boundary,
                ) { arms += 1 }
            }
        }

        activeBoundary = boundary.copy(accountId = "account-b", boundaryEpoch = 2L)
        releaseGate.complete(Unit)
        holder.join()
        arm.await()

        assertEquals(0, arms)
    }

    @Test
    fun reentrantHeldBoundaryRunsTheArmExactlyOnce() = runTest {
        val gate = MutexAccountMutationGate()
        var arms = 0

        gate.withExclusive {
            withHeldReminderBoundary(gate, { boundary }, expectedBoundary = boundary) {
                arms += 1
            }
        }

        assertEquals(1, arms)
    }

    @Test
    fun heldBoundaryPropagatesCancellation() = runTest {
        val gate = MutexAccountMutationGate()
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> {
            withHeldReminderBoundary(gate, { throw cancellation }) { error("must not arm") }
        }

        assertEquals(cancellation.message, thrown.message)
    }

    @Test
    fun exactAlarmSelectionPreservesPreSAndCapabilityGatedPrecision() {
        assertTrue(shouldUseExactAlarm(ANDROID_S_API_LEVEL - 1, canScheduleExactAlarms = false))
        assertTrue(shouldUseExactAlarm(ANDROID_S_API_LEVEL, canScheduleExactAlarms = true))
        assertEquals(false, shouldUseExactAlarm(ANDROID_S_API_LEVEL, canScheduleExactAlarms = false))
    }
}
