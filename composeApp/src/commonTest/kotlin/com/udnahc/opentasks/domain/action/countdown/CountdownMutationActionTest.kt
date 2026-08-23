package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderRequest
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import com.udnahc.opentasks.testutil.testCountdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CountdownMutationActionTest {
    @Test
    fun committedSyncAndReminderWarningsMergeWithoutLosingEitherCause() = runTest {
        val syncFailure = IllegalStateException("sync")
        val reminderFailure = IllegalStateException("reminder")
        val repository = FakeCountdownRepository().apply {
            insertPostCommitWarning = syncFailure
        }
        val action = AddCountdownAction(
            repository = repository,
            scheduleCountdownRemindersAction = ScheduleCountdownRemindersAction(
                scheduler = ThrowingScheduler(reminderFailure),
                countdownRepository = repository,
                nowUtcMillisProvider = { 0L },
            ),
        )

        val result = action(testCountdown(id = "countdown", targetDate = 4_000_000_000_000L, reminders = "0"))
        val warning = assertNotNull(result.postCommitWarning)

        assertEquals(PostCommitWarningPhase.COMBINED, warning.phase)
        assertTrue(warning.cause.cause === syncFailure)
        assertTrue(warning.cause.suppressed.any { it === reminderFailure })
        assertEquals("countdown", result.value.id)
    }

    @Test
    fun reminderCancellationRemainsCancellationAfterCommittedCountdownWrite() = runTest {
        val repository = FakeCountdownRepository()
        val action = AddCountdownAction(
            repository = repository,
            scheduleCountdownRemindersAction = ScheduleCountdownRemindersAction(
                scheduler = ThrowingScheduler(CancellationException("cancel")),
                countdownRepository = repository,
                nowUtcMillisProvider = { 0L },
            ),
        )

        assertFailsWith<CancellationException> {
            action(testCountdown(id = "countdown", targetDate = 4_000_000_000_000L, reminders = "0"))
        }
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun reminderOnlyFailureReturnsCommittedValueWithMaintenanceWarning() = runTest {
        val repository = FakeCountdownRepository()
        val action = UpdateCountdownAction(
            repository = repository,
            scheduleCountdownRemindersAction = ScheduleCountdownRemindersAction(
                scheduler = ThrowingScheduler(IllegalStateException("reminder")),
                countdownRepository = repository,
                nowUtcMillisProvider = { 0L },
            ),
        )

        val result = action(testCountdown(id = "countdown", targetDate = 4_000_000_000_000L, reminders = "0"))

        assertEquals("countdown", result.value.id)
        assertEquals(PostCommitWarningPhase.REMINDER_MAINTENANCE, result.postCommitWarning?.phase)
    }

    @Test
    fun deleteReturnsCommittedTombstoneAndMergesSyncAndReminderWarnings() = runTest {
        val syncFailure = IllegalStateException("sync")
        val reminderFailure = IllegalStateException("reminder")
        val repository = FakeCountdownRepository().apply {
            deletePostCommitWarning = syncFailure
        }
        val action = DeleteCountdownAction(
            repository = repository,
            scheduleCountdownRemindersAction = ScheduleCountdownRemindersAction(
                scheduler = ThrowingScheduler(
                    failure = reminderFailure,
                    cancelFailure = reminderFailure,
                ),
                countdownRepository = repository,
                nowUtcMillisProvider = { 0L },
            ),
        )

        val result = action(testCountdown(id = "countdown"))
        val warning = assertNotNull(result.postCommitWarning)

        assertTrue(result.value.isDeleted)
        assertEquals(PostCommitWarningPhase.COMBINED, warning.phase)
        assertTrue(warning.cause.cause === syncFailure)
        assertTrue(warning.cause.suppressed.any { it === reminderFailure })
        assertTrue(repository.deleted.single().isDeleted)
        assertTrue(repository.updated.isEmpty())
    }
}

private class ThrowingScheduler(
    private val failure: Throwable,
    private val cancelFailure: Throwable? = null,
) : ReminderScheduler {
    override suspend fun schedule(request: ReminderRequest) = throw failure
    override suspend fun cancel(semanticKey: String) = Unit
    override suspend fun cancelPendingReminders(eventId: String) = Unit
    override suspend fun cancelReminders(eventId: String) {
        cancelFailure?.let { throw it }
    }
    override suspend fun cancelAll(eventId: String) = Unit
    override suspend fun startOngoing(identity: ReminderIdentity, title: String) = Unit
    override suspend fun stopOngoing(eventId: String) = Unit
    override suspend fun cancelAllAccountReminders() = Unit
}
