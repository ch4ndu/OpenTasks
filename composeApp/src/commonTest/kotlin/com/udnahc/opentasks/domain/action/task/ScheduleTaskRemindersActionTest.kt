package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.ReminderKind
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.notification.ReminderTextProvider
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
    fun semanticOrdinalsAreAssignedBeforePastTriggersAreFiltered() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val deadline = LocalDateTime(2026, 5, 8, 13, 0).toInstant(timeZone).toEpochMilliseconds()
        val now = deadline - (90 * MILLIS_PER_HOUR / 60)
        val task = testTask(id = "stable-ordinal", deadline = deadline, dateReminders = "120,60,0")
        val action = ScheduleTaskRemindersAction(
            scheduler = FakeReminderScheduler(),
            taskRepository = FakeTaskRepository(listOf(task)),
            nowUtcMillisProvider = { now },
        )

        val requests = action.buildFutureRequests(task, occurrenceLimit = 1)

        assertEquals(
            listOf(1, 2),
            requests.filter { it.identity.kind == ReminderKind.DATE }.map { it.identity.ordinal },
        )
    }

    @Test
    fun futureOccurrenceGenerationHonorsTheRequestedBound() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 8, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val task = testTask(
            id = "bounded-occurrences",
            deadline = LocalDateTime(2026, 5, 8, 13, 0).toInstant(timeZone).toEpochMilliseconds(),
            dateReminders = "0",
            recurrenceType = RecurrenceType.DAILY,
        )
        val action = ScheduleTaskRemindersAction(
            scheduler = FakeReminderScheduler(),
            taskRepository = FakeTaskRepository(listOf(task)),
            nowUtcMillisProvider = { now },
        )

        val requests = action.buildFutureRequests(task, occurrenceLimit = 2)

        assertEquals(2, requests.size)
        assertEquals(2, requests.map { it.occurrenceUtcMillis }.distinct().size)
    }

    @Test
    fun reminderBodyComesFromInjectedResourceFreeProvider() = runTest {
        val timeZone = TimeZone.currentSystemDefault()
        val now = LocalDateTime(2026, 5, 8, 12, 0).toInstant(timeZone).toEpochMilliseconds()
        val deadline = LocalDateTime(2026, 5, 8, 13, 0).toInstant(timeZone).toEpochMilliseconds()
        val scheduler = FakeReminderScheduler()
        val action = ScheduleTaskRemindersAction(
            scheduler = scheduler,
            taskRepository = FakeTaskRepository(
                listOf(testTask(id = "text", deadline = deadline, dateReminders = "0"))
            ),
            textProvider = FakeReminderTextProvider,
            nowUtcMillisProvider = { now },
        )

        action("text")

        assertTrue(scheduler.scheduled.any { it.body == "task-due-0" })
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
        val body: String,
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

    override suspend fun schedule(request: com.udnahc.opentasks.data.notification.ReminderRequest) {
        scheduledAt.add(request.triggerAtUtcMillis)
        scheduled.add(
            ScheduledReminder(
                body = request.body,
                triggerAtMillis = request.triggerAtUtcMillis,
                occurrenceDeadlineUtcMillis = request.occurrenceUtcMillis,
                allowMarkDone = request.allowMarkDone,
                rescheduleAfterFire = request.rescheduleAfterFire,
            )
        )
    }

    override suspend fun cancel(semanticKey: String) = Unit

    override suspend fun cancelPendingReminders(eventId: String) = Unit

    override suspend fun cancelReminders(eventId: String) {
        cancelledReminders.add(eventId)
    }

    override suspend fun cancelAll(eventId: String) = Unit

    override suspend fun startOngoing(
        identity: com.udnahc.opentasks.data.notification.ReminderIdentity,
        title: String,
    ) {
        startedOngoing.add(identity.eventId)
        ongoingOccurrences.add(identity.occurrenceUtcMillis)
    }

    override suspend fun stopOngoing(eventId: String) = Unit
}

private object FakeReminderTextProvider : ReminderTextProvider {
    override suspend fun taskDue(minutes: Int): String = "task-due-$minutes"
    override suspend fun taskStarting(minutes: Int): String = "task-starting-$minutes"
    override suspend fun taskEndingNow(): String = "task-ending"
    override suspend fun taskOverdue(): String = "task-overdue"
    override suspend fun countdownDue(minutes: Int): String = "countdown-due-$minutes"
}
