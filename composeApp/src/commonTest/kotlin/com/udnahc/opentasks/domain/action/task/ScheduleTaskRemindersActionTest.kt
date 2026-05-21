package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class ScheduleTaskRemindersActionTest {
    @Test
    fun allDayTaskDueTodayStartsOngoingNotification() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 8, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val deadline = LocalDateTime(2026, 5, 8, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val task = testTask(id = "task-today", deadline = deadline, isAllDay = true)
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            allDayDismissalStore = AllDayNotificationDismissalStore(FakeAppSettingsRepository()) { now },
            nowUtcMillisProvider = { now },
        )

        action("task-today")

        assertEquals(listOf("task-today"), scheduler.startedOngoing)
    }

    @Test
    fun allDayTaskDismissedTodayDoesNotRestartOngoingNotification() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 8, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val deadline = LocalDateTime(2026, 5, 8, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val settingsRepository = FakeAppSettingsRepository()
        val dismissalStore = AllDayNotificationDismissalStore(settingsRepository) { now }
        dismissalStore.dismissToday("task-today")
        val task = testTask(id = "task-today", deadline = deadline, isAllDay = true)
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            allDayDismissalStore = dismissalStore,
            nowUtcMillisProvider = { now },
        )

        action("task-today")

        assertTrue(scheduler.startedOngoing.isEmpty())
    }

    @Test
    fun allDayTaskDueAnotherDayDoesNotStartOngoingNotification() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 8, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val deadline = LocalDateTime(2026, 5, 9, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val task = testTask(id = "task-tomorrow", deadline = deadline, isAllDay = true)
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            allDayDismissalStore = AllDayNotificationDismissalStore(FakeAppSettingsRepository()) { now },
            nowUtcMillisProvider = { now },
        )

        action("task-tomorrow")

        assertTrue(scheduler.startedOngoing.isEmpty())
    }

    @Test
    fun inactiveAndUndatedTasksCancelWithoutStartingOngoingNotification() = runTest {
        val now = LocalDateTime(2026, 5, 8, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val tasks = listOf(
            testTask(id = "done", deadline = now, isAllDay = true, status = TaskStatus.DONE),
            testTask(id = "deleted", deadline = now, isAllDay = true, isDeleted = true),
            testTask(id = "undated", deadline = null, isAllDay = true),
        )
        val scheduler = FakeReminderScheduler()
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(tasks),
            allDayDismissalStore = AllDayNotificationDismissalStore(FakeAppSettingsRepository()) { now },
            nowUtcMillisProvider = { now },
        )

        action("done")
        action("deleted")
        action("undated")

        assertTrue(scheduler.startedOngoing.isEmpty())
        assertEquals(listOf("done", "deleted", "undated"), scheduler.cancelledReminders)
    }

    @Test
    fun futureTimedReminderStillSchedulesAlarm() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 8, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val deadline = LocalDateTime(2026, 5, 8, 13, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val task = testTask(id = "timed", deadline = deadline, dateReminders = "0")
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            nowUtcMillisProvider = { now },
        )

        action("timed")

        assertEquals(listOf(deadline), scheduler.scheduledAt)
        assertTrue(scheduler.startedOngoing.isEmpty())
    }

    @Test
    fun pastRecurringTaskSchedulesNextFutureOccurrenceWithoutUpdatingStoredDeadline() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 10, 11, 30).toInstant(timeZone).toEpochMilliseconds()
        val storedDeadline = LocalDateTime(2026, 5, 8, 13, 0).toInstant(timeZone).toEpochMilliseconds()
        val nextOccurrence = LocalDateTime(2026, 5, 10, 13, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val repository = FakeTaskRepository(
            listOf(
                testTask(
                    id = "recurring",
                    deadline = storedDeadline,
                    recurrenceType = RecurrenceType.DAILY,
                    recurrenceInterval = 1,
                    dateReminders = "60,0",
                )
            )
        )
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = repository,
            nowUtcMillisProvider = { now },
        )

        action("recurring")

        assertEquals(storedDeadline, repository.tasks.single().deadline)
        assertEquals(
            listOf(nextOccurrence - MILLIS_PER_HOUR, nextOccurrence),
            scheduler.scheduled.map { it.triggerAtMillis },
        )
        assertEquals(listOf(false, true), scheduler.scheduled.map { it.allowMarkDone })
        assertEquals(listOf(false, true), scheduler.scheduled.map { it.rescheduleAfterFire })
        assertEquals(listOf(nextOccurrence, nextOccurrence), scheduler.scheduled.map { it.occurrenceDeadlineUtcMillis })
    }

    @Test
    fun durationOnTimeReminderDoesNotSuppressDueNotificationWithMarkDone() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 8, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val deadline = LocalDateTime(2026, 5, 8, 13, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val task = testTask(id = "duration", deadline = deadline, durationReminders = "0")
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            nowUtcMillisProvider = { now },
        )

        action("duration")

        assertEquals(listOf(false, true), scheduler.scheduled.map { it.allowMarkDone })
        assertEquals(listOf(false, false), scheduler.scheduled.map { it.rescheduleAfterFire })
        assertEquals(listOf(deadline, deadline), scheduler.scheduled.map { it.triggerAtMillis })
    }

    @Test
    fun nonRecurringPastTaskDoesNotScheduleFutureOccurrence() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 10, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val deadline = LocalDateTime(2026, 5, 8, 13, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val task = testTask(id = "past", deadline = deadline, dateReminders = "0")
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            nowUtcMillisProvider = { now },
        )

        action("past")

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun pastRecurringAllDayTaskStartsOngoingForTodayOccurrence() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 9, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val storedDeadline = LocalDateTime(2026, 5, 8, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val todayOccurrence = LocalDateTime(2026, 5, 9, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val task = testTask(
            id = "all-day",
            deadline = storedDeadline,
            isAllDay = true,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
        )
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            allDayDismissalStore = AllDayNotificationDismissalStore(FakeAppSettingsRepository()) { now },
            nowUtcMillisProvider = { now },
        )

        action("all-day")

        assertEquals(listOf("all-day"), scheduler.startedOngoing)
        assertEquals(listOf<Long?>(todayOccurrence), scheduler.ongoingOccurrences)
    }

    @Test
    fun scheduleAfterOccurrenceMovesRecurringAllDayTaskToNextDay() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 9, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val storedDeadline = LocalDateTime(2026, 5, 8, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val todayOccurrence = LocalDateTime(2026, 5, 9, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val nextOccurrence = LocalDateTime(2026, 5, 10, 0, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val task = testTask(
            id = "all-day",
            deadline = storedDeadline,
            isAllDay = true,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
        )
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(listOf(task)),
            allDayDismissalStore = AllDayNotificationDismissalStore(FakeAppSettingsRepository()) { now },
            nowUtcMillisProvider = { now },
        )

        action.invokeAfterOccurrence("all-day", todayOccurrence)

        assertTrue(scheduler.startedOngoing.isEmpty())
        assertEquals(listOf(nextOccurrence), scheduler.scheduled.map { it.triggerAtMillis })
        assertEquals(listOf(true), scheduler.scheduled.map { it.rescheduleAfterFire })
    }

    @Test
    fun legacyMonthReminderUsesCalendarMonthAndPreservesLocalTime() {
        val timeZone = TimeZone.currentSystemDefault()
        val deadlineUtc = LocalDateTime(2026, 3, 31, 10, 30)
            .toInstant(timeZone)
            .toEpochMilliseconds()

        val triggerUtc = legacyReminderTriggerUtcMillis(
            deadlineUtcMillis = deadlineUtc,
            value = 1,
            unit = NotifyBeforeUnit.MONTHS,
        )
        val triggerLocal = Instant.fromEpochMilliseconds(triggerUtc).toLocalDateTime(timeZone)

        assertEquals(2026, triggerLocal.year)
        assertEquals(Month.FEBRUARY, triggerLocal.month)
        assertEquals(28, triggerLocal.day)
        assertEquals(10, triggerLocal.hour)
        assertEquals(30, triggerLocal.minute)
    }

    @Test
    fun legacyWeekReminderUsesSevenCalendarDaysAndPreservesLocalTime() {
        val timeZone = TimeZone.currentSystemDefault()
        val deadlineUtc = LocalDateTime(2026, 5, 11, 9, 15)
            .toInstant(timeZone)
            .toEpochMilliseconds()

        val triggerUtc = legacyReminderTriggerUtcMillis(
            deadlineUtcMillis = deadlineUtc,
            value = 1,
            unit = NotifyBeforeUnit.WEEKS,
        )
        val triggerLocal = Instant.fromEpochMilliseconds(triggerUtc).toLocalDateTime(timeZone)

        assertEquals(2026, triggerLocal.year)
        assertEquals(Month.MAY, triggerLocal.month)
        assertEquals(4, triggerLocal.day)
        assertEquals(9, triggerLocal.hour)
        assertEquals(15, triggerLocal.minute)
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    data class ScheduledReminder(
        val triggerAtMillis: Long,
        val occurrenceDeadlineUtcMillis: Long?,
        val allowMarkDone: Boolean,
        val rescheduleAfterFire: Boolean,
    )

    val cancelledReminders = mutableListOf<String>()
    val startedOngoing = mutableListOf<String>()
    val ongoingOccurrences = mutableListOf<Long?>()
    val scheduledAt = mutableListOf<Long>()
    val scheduled = mutableListOf<ScheduledReminder>()

    override fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
        occurrenceDeadlineUtcMillis: Long?,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
    ) {
        scheduledAt.add(triggerAtMillis)
        scheduled.add(
            ScheduledReminder(
                triggerAtMillis = triggerAtMillis,
                occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                allowMarkDone = allowMarkDone,
                rescheduleAfterFire = rescheduleAfterFire,
            )
        )
    }

    override fun cancel(taskId: String, reminderId: Int) = Unit

    override fun cancelReminders(taskId: String) {
        cancelledReminders.add(taskId)
    }

    override fun cancelAll(taskId: String) = Unit

    override fun startOngoing(
        taskId: String,
        title: String,
        occurrenceDeadlineUtcMillis: Long?,
    ) {
        startedOngoing.add(taskId)
        ongoingOccurrences.add(occurrenceDeadlineUtcMillis)
    }

    override fun stopOngoing(taskId: String) = Unit
}
