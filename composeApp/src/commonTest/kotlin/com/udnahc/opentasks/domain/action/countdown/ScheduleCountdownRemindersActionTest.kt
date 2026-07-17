package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.extensions.computeNextDeadlineUtc
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleCountdownRemindersActionTest {
    @Test
    fun recurringCountdownAdvancesUntilConfiguredReminderIsFutureAndChainsLastAlarm() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 1, 1, 10, 0).toInstant(timeZone).toEpochMilliseconds()
        val target = LocalDateTime(2026, 1, 1, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val expectedOccurrence = LocalDateTime(2026, 1, 9, 0, 0)
            .toInstant(timeZone).toEpochMilliseconds()
        val countdown = Countdown(
            id = "recurring",
            title = "Recurring",
            targetDate = target,
            reminders = "10080",
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
        )
        val scheduler = FakeScheduler()
        val action = ScheduleCountdownRemindersAction(
            scheduler,
            FakeCountdownRepository(listOf(countdown)),
            nowUtcMillisProvider = { now },
        )

        action(countdown.id)

        assertEquals(listOf(expectedOccurrence), scheduler.scheduled.map { it.occurrence })
        assertEquals(listOf(true), scheduler.scheduled.map { it.rescheduleAfterFire })
    }

    @Test
    fun occurrenceValidationRejectsOldOneOffTargetAndAcceptsRecurringSeriesMember() {
        val target = 1_000L
        val action = ScheduleCountdownRemindersAction(
            FakeScheduler(),
            FakeCountdownRepository(),
        )

        assertFalse(action.isValidOccurrence(Countdown("one", "One", target), target + 1))
        assertTrue(action.isValidOccurrence(Countdown("one", "One", target), target))

        val recurring = Countdown(
            id = "daily",
            title = "Daily",
            targetDate = target,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
        )
        val nextOccurrence = computeNextDeadlineUtc(target, RecurrenceType.DAILY.name)
        assertTrue(action.isValidOccurrence(recurring, nextOccurrence))
        assertFalse(action.isValidOccurrence(recurring, nextOccurrence + 1))
    }
}

private class FakeScheduler : ReminderScheduler {
    data class Scheduled(val occurrence: Long?, val rescheduleAfterFire: Boolean)
    val scheduled = mutableListOf<Scheduled>()

    override suspend fun schedule(request: com.udnahc.opentasks.data.notification.ReminderRequest) {
        scheduled += Scheduled(request.occurrenceUtcMillis, request.rescheduleAfterFire)
    }

    override suspend fun cancel(semanticKey: String) = Unit
    override suspend fun cancelPendingReminders(eventId: String) = Unit
    override suspend fun cancelReminders(eventId: String) = Unit
    override suspend fun cancelAll(eventId: String) = Unit
    override suspend fun startOngoing(
        identity: com.udnahc.opentasks.data.notification.ReminderIdentity,
        title: String,
    ) = Unit
    override suspend fun stopOngoing(eventId: String) = Unit
}
