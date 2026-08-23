package com.udnahc.opentasks.data.notification

import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OutOfQuotaPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReminderRebuildWorkerTest {
    @Test
    fun systemActionsSelectOnlyTheDedicatedReminderRebuildWorker() {
        assertTrue(isReminderRebuildIntentAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(isReminderRebuildIntentAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(isReminderRebuildIntentAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(isReminderRebuildIntentAction(Intent.ACTION_TIME_CHANGED))
        assertEquals(false, isReminderRebuildIntentAction("unsupported"))
        assertEquals(ExistingWorkPolicy.REPLACE, reminderRebuildExistingWorkPolicy())
    }

    @Test
    fun rebuildWorkRequestUsesExpeditedFallbackOnlyOnAndroidSAndLater() {
        val modern = reminderRebuildWorkRequest(Build.VERSION_CODES.S)
        val older = reminderRebuildWorkRequest(Build.VERSION_CODES.R)

        assertTrue(modern.workSpec.expedited)
        assertEquals(
            OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST,
            modern.workSpec.outOfQuotaPolicy,
        )
        assertEquals(false, older.workSpec.expedited)
    }

    @Test
    fun rebuildSuccessDoesNotCancelCurrentReminders() = runTest {
        var rebuildCalls = 0
        var cancelCalls = 0

        runReminderRebuildWithinBoundary(
            rebuild = { rebuildCalls += 1 },
            cancelAll = { cancelCalls += 1 },
        )

        assertEquals(1, rebuildCalls)
        assertEquals(0, cancelCalls)
    }

    @Test
    fun rebuildFailurePreservesOriginalAndSuppressesCleanupFailure() = runTest {
        val original = IllegalStateException("rebuild")
        val cleanup = IllegalArgumentException("cleanup")

        val thrown = assertFailsWith<IllegalStateException> {
            runReminderRebuildWithinBoundary(
                rebuild = { throw original },
                cancelAll = { throw cleanup },
            )
        }

        assertSame(original, thrown)
        assertEquals(listOf(cleanup), thrown.suppressedExceptions)
    }

    @Test
    fun rebuildCancellationEscapesWithoutCancellingCurrentReminders() = runTest {
        val cancellation = CancellationException("cancelled")
        var cleanupCalls = 0

        val thrown = assertFailsWith<CancellationException> {
            runReminderRebuildWithinBoundary(
                rebuild = { throw cancellation },
                cancelAll = { cleanupCalls += 1 },
            )
        }

        assertSame(cancellation, thrown)
        assertEquals(0, cleanupCalls)
    }

    @Test
    fun workerMapsOrdinaryFailureToRetryAndLetsCancellationEscape() = runTest {
        val failure = IllegalStateException("rebuild")

        val outcome = runReminderRebuildWorker { throw failure }
        val retry = outcome as ReminderRebuildWorkerOutcome.Retry
        assertSame(failure, retry.error)

        val cancellation = CancellationException("cancelled")
        val thrown = assertFailsWith<CancellationException> {
            runReminderRebuildWorker { throw cancellation }
        }
        assertSame(cancellation, thrown)
    }
}
