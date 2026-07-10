package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.computeNextDeadlineUtc
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.ReminderTextProvider
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.notification.PlainReminderTextProvider
import com.udnahc.opentasks.data.notification.ReminderRequest
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.lighthousegames.logging.logging
import kotlin.time.Instant

private val log = logging("ScheduleTaskRemindersAction")

private const val OVERDUE_REMINDER_ID = 49
private const val DURATION_REMINDER_OFFSET = 50
private const val MINUTES_PER_DAY = 1440
private const val MINUTES_PER_WEEK = 10080
private const val MONTH_REMINDER_LABEL_DAYS = 30

internal data class ReminderTrigger(
    val minutesForLabel: Int,
    val triggerAtUtcMillis: Long,
)

private data class DurationReminderTrigger(
    val index: Int,
    val minutesForLabel: Int,
    val triggerAtUtcMillis: Long,
)

private data class SchedulingOccurrence(
    val deadlineUtcMillis: Long,
    val endDeadlineUtcMillis: Long?,
)

class ScheduleTaskRemindersAction(
    private val scheduler: ReminderScheduler,
    private val taskRepository: TaskRepository,
    private val allDayDismissalStore: AllDayNotificationDismissalStore? = null,
    private val textProvider: ReminderTextProvider = PlainReminderTextProvider,
    private val nowUtcMillisProvider: () -> Long = ::utcNow,
) {
    /**
     * Schedule reminders by task ID. Re-reads the task from DB with raw UTC timestamps
     * to ensure alarm scheduling uses correct absolute times.
     */
    suspend operator fun invoke(taskId: String) {
        val task = taskRepository.getTaskByIdUtc(taskId)
        if (task == null) {
            log.d { "Task $taskId not found, cancelling reminders" }
            scheduler.cancelReminders(taskId)
            scheduler.stopOngoing(taskId)
            return
        }
        scheduleForTask(task)
    }

    suspend fun invokeAfterOccurrence(
        taskId: String,
        occurrenceDeadlineUtcMillis: Long
    ) {
        val task = taskRepository.getTaskByIdUtc(taskId)
        if (task == null) {
            log.d { "Task $taskId not found, cancelling reminders" }
            scheduler.cancelReminders(taskId)
            scheduler.stopOngoing(taskId)
            return
        }
        scheduleForTask(task, afterOccurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis)
    }

    /**
     * Schedule reminders for a task that already has raw UTC timestamps.
     * Used by RescheduleAllRemindersAction which bulk-reads from the UTC path.
     */
    suspend fun invokeWithUtcTask(task: Task) {
        scheduleForTask(task)
    }

    suspend fun buildFutureRequests(
        task: Task,
        occurrenceLimit: Int,
    ): List<ReminderRequest> {
        if (
            occurrenceLimit <= 0 || task.status == TaskStatus.DONE || task.isDeleted ||
            task.deadline == null
        ) return emptyList()
        val now = nowUtcMillisProvider()
        val requests = mutableListOf<ReminderRequest>()
        var afterOccurrence: Long? = null
        repeat(occurrenceLimit) {
            val occurrence = task.schedulingOccurrence(now, afterOccurrence) ?: return@repeat
            requests += task.requestsForOccurrence(occurrence, now)
            afterOccurrence = occurrence.deadlineUtcMillis
            if (task.recurrenceType == RecurrenceType.NONE) return requests
        }
        return requests
    }

    private suspend fun Task.requestsForOccurrence(
        occurrence: SchedulingOccurrence,
        now: Long,
    ): List<ReminderRequest> {
        val taskForOccurrence = copy(
            deadline = occurrence.deadlineUtcMillis,
            endDeadline = occurrence.endDeadlineUtcMillis,
        )
        val dateTriggers = taskForOccurrence.dateReminders.parseMinuteValues()
            .map { minutes ->
                ReminderTrigger(
                    minutes,
                    occurrence.deadlineUtcMillis - minutes.toLong() * MILLIS_PER_MINUTE,
                )
            }
            .ifEmpty { taskForOccurrence.legacyReminderTriggers() }
        val durationTriggers = taskForOccurrence.durationReminders.parseMinuteValues()
            .mapIndexedNotNull { index, minutes ->
                val triggerAt = if (minutes == -1) {
                    taskForOccurrence.endDeadline ?: return@mapIndexedNotNull null
                } else {
                    occurrence.deadlineUtcMillis - minutes * MILLIS_PER_MINUTE
                }
                DurationReminderTrigger(index, minutes, triggerAt)
            }
        val hasDueNow = dateTriggers.any { it.triggerAtUtcMillis == occurrence.deadlineUtcMillis }
        val overdueAt = occurrence.deadlineUtcMillis.takeIf { it > now && !hasDueNow }
        return buildList {
            dateTriggers.filter { it.triggerAtUtcMillis > now }.forEachIndexed { index, trigger ->
                add(
                    ReminderRequest(
                        eventId = id,
                        title = title,
                        body = textProvider.taskDue(trigger.minutesForLabel),
                        triggerAtUtcMillis = trigger.triggerAtUtcMillis,
                        reminderId = index,
                        occurrenceUtcMillis = occurrence.deadlineUtcMillis,
                        allowMarkDone = trigger.triggerAtUtcMillis == occurrence.deadlineUtcMillis,
                    )
                )
            }
            durationTriggers.filter { it.triggerAtUtcMillis > now }.forEach { trigger ->
                add(
                    ReminderRequest(
                        eventId = id,
                        title = title,
                        body = if (trigger.minutesForLabel == -1) {
                            textProvider.taskEndingNow()
                        } else {
                            textProvider.taskStarting(trigger.minutesForLabel)
                        },
                        triggerAtUtcMillis = trigger.triggerAtUtcMillis,
                        reminderId = DURATION_REMINDER_OFFSET + trigger.index,
                        occurrenceUtcMillis = occurrence.deadlineUtcMillis,
                    )
                )
            }
            overdueAt?.let { triggerAt ->
                add(
                    ReminderRequest(
                        eventId = id,
                        title = title,
                        body = textProvider.taskOverdue(),
                        triggerAtUtcMillis = triggerAt,
                        reminderId = OVERDUE_REMINDER_ID,
                        occurrenceUtcMillis = occurrence.deadlineUtcMillis,
                        allowMarkDone = true,
                    )
                )
            }
        }
    }

    private suspend fun scheduleForTask(
        task: Task,
        afterOccurrenceDeadlineUtcMillis: Long? = null,
    ) {
        log.d { "Scheduling reminders for task ${task.id}" }
        scheduler.cancelReminders(task.id)
        scheduler.stopOngoing(task.id)

        if (task.status == TaskStatus.DONE || task.isDeleted || task.deadline == null) {
            log.d { "Cancelled reminders for completed/deleted task ${task.id}" }
            return
        }

        val now = nowUtcMillisProvider()
        val occurrence = task.schedulingOccurrence(now, afterOccurrenceDeadlineUtcMillis) ?: return
        val taskForOccurrence = task.copy(
            deadline = occurrence.deadlineUtcMillis,
            endDeadline = occurrence.endDeadlineUtcMillis,
        )

        scheduleAllDayOngoingIfNeeded(taskForOccurrence, now)

        val dateReminderValues = taskForOccurrence.dateReminders.parseMinuteValues()
        val durationReminderValues = taskForOccurrence.durationReminders.parseMinuteValues()
        val dateReminderTriggers = dateReminderValues
            .map { mins ->
                ReminderTrigger(
                    mins,
                    occurrence.deadlineUtcMillis - (mins.toLong() * MILLIS_PER_MINUTE)
                )
            }
            .ifEmpty { taskForOccurrence.legacyReminderTriggers() }
        val durationReminderTriggers = durationReminderValues.mapIndexedNotNull { index, mins ->
            val triggerAt = if (mins == -1) {
                taskForOccurrence.endDeadline ?: return@mapIndexedNotNull null
            } else {
                occurrence.deadlineUtcMillis - (mins * MILLIS_PER_MINUTE)
            }
            DurationReminderTrigger(index, mins, triggerAt)
        }
        val hasDueNowReminder =
            dateReminderTriggers.any { it.triggerAtUtcMillis == occurrence.deadlineUtcMillis }
        val overdueTriggerAt =
            occurrence.deadlineUtcMillis.takeIf { it > now && !hasDueNowReminder }
        val lastScheduledTriggerAt = (
                dateReminderTriggers.map { it.triggerAtUtcMillis } +
                        durationReminderTriggers.map { it.triggerAtUtcMillis } +
                        listOfNotNull(overdueTriggerAt)
                )
            .filter { it > now }
            .maxOrNull()

        // Date reminders (minutes before deadline, stored as ReminderOption.minutesValue)
        dateReminderTriggers.forEachIndexed { index, trigger ->
            if (trigger.triggerAtUtcMillis > now) {
                log.v { "Scheduled date reminder $index at ${trigger.triggerAtUtcMillis}" }
                scheduler.schedule(
                    taskId = task.id,
                    title = task.title,
                    body = dueReminderBody(trigger.minutesForLabel),
                    triggerAtMillis = trigger.triggerAtUtcMillis,
                    reminderId = index,
                    occurrenceDeadlineUtcMillis = occurrence.deadlineUtcMillis,
                    allowMarkDone = trigger.triggerAtUtcMillis == occurrence.deadlineUtcMillis,
                    rescheduleAfterFire = task.recurrenceType != RecurrenceType.NONE &&
                            trigger.triggerAtUtcMillis == lastScheduledTriggerAt,
                )
            } else {
                log.v { "Skipped past date reminder $index at ${trigger.triggerAtUtcMillis}" }
            }
        }

        // Duration reminders (minutes before deadline)
        durationReminderTriggers.forEach { trigger ->
            val triggerAt = trigger.triggerAtUtcMillis
            if (triggerAt > now) {
                log.v { "Scheduled duration reminder ${trigger.index} at $triggerAt" }
                scheduler.schedule(
                    taskId = taskForOccurrence.id,
                    title = taskForOccurrence.title,
                    body = if (trigger.minutesForLabel == -1) {
                        textProvider.taskEndingNow()
                    } else {
                        startingReminderBody(trigger.minutesForLabel)
                    },
                    triggerAtMillis = triggerAt,
                    reminderId = DURATION_REMINDER_OFFSET + trigger.index,
                    occurrenceDeadlineUtcMillis = occurrence.deadlineUtcMillis,
                    allowMarkDone = false,
                    rescheduleAfterFire = task.recurrenceType != RecurrenceType.NONE &&
                            triggerAt == lastScheduledTriggerAt,
                )
            } else {
                log.v { "Skipped past duration reminder ${trigger.index} at $triggerAt" }
            }
        }

        // Overdue notification — fires at the moment the deadline passes
        // Skip if a zero-minute date reminder already fires at the same time.
        if (overdueTriggerAt != null) {
            log.v { "Scheduled overdue notification at ${occurrence.deadlineUtcMillis}" }
            scheduler.schedule(
                taskId = taskForOccurrence.id,
                title = taskForOccurrence.title,
                body = textProvider.taskOverdue(),
                triggerAtMillis = overdueTriggerAt,
                reminderId = OVERDUE_REMINDER_ID,
                occurrenceDeadlineUtcMillis = occurrence.deadlineUtcMillis,
                allowMarkDone = true,
                rescheduleAfterFire = task.recurrenceType != RecurrenceType.NONE &&
                        overdueTriggerAt == lastScheduledTriggerAt,
            )
        }
    }

    private suspend fun scheduleAllDayOngoingIfNeeded(
        task: Task,
        now: Long
    ) {
        if (!task.isAllDay) return
        val occurrenceDeadline = task.deadline ?: return
        if (!task.isDueToday(now)) {
            log.d { "Skipped all-day ongoing notification for task ${task.id}: not due today" }
            return
        }
        if (allDayDismissalStore?.isDismissedToday(task.id) == true) {
            log.d { "Skipped all-day ongoing notification for task ${task.id}: dismissed today" }
            return
        }
        log.d { "Starting all-day ongoing notification for task ${task.id}" }
        scheduler.startOngoing(
            task.id,
            task.title,
            occurrenceDeadlineUtcMillis = occurrenceDeadline,
        )
    }

    private fun Task.isDueToday(now: Long): Boolean {
        val deadlineValue = deadline ?: return false
        val timeZone = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        val dueDate = Instant.fromEpochMilliseconds(deadlineValue).toLocalDateTime(timeZone).date
        return dueDate == today
    }

    private fun String.parseMinuteValues(): List<Int> =
        split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun Task.schedulingOccurrence(
        now: Long,
        afterOccurrenceDeadlineUtcMillis: Long?,
    ): SchedulingOccurrence? {
        val storedDeadline = deadline ?: return null
        val occurrenceDeadline = if (recurrenceType == RecurrenceType.NONE) {
            storedDeadline
        } else if (isAllDay) {
            firstAllDayOccurrenceOnOrAfterToday(
                storedDeadline,
                now,
                afterOccurrenceDeadlineUtcMillis
            )
        } else {
            firstTimedOccurrenceAfterNow(storedDeadline, now, afterOccurrenceDeadlineUtcMillis)
        }
        val duration = endDeadline?.let { it - storedDeadline }
        return SchedulingOccurrence(
            deadlineUtcMillis = occurrenceDeadline,
            endDeadlineUtcMillis = duration?.let { occurrenceDeadline + it },
        )
    }

    private fun Task.firstTimedOccurrenceAfterNow(
        storedDeadline: Long,
        now: Long,
        afterOccurrenceDeadlineUtcMillis: Long?,
    ): Long {
        var candidate = storedDeadline
        if (afterOccurrenceDeadlineUtcMillis != null) {
            while (candidate <= afterOccurrenceDeadlineUtcMillis) {
                candidate = computeNextDeadlineUtc(
                    currentDeadlineUtcMillis = candidate,
                    recurrenceType = recurrenceType.name,
                    interval = recurrenceInterval,
                )
            }
        }
        while (candidate <= now) {
            candidate = computeNextDeadlineUtc(
                currentDeadlineUtcMillis = candidate,
                recurrenceType = recurrenceType.name,
                interval = recurrenceInterval,
            )
        }
        return candidate
    }

    private fun Task.firstAllDayOccurrenceOnOrAfterToday(
        storedDeadline: Long,
        now: Long,
        afterOccurrenceDeadlineUtcMillis: Long?,
    ): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        var candidate = storedDeadline
        if (afterOccurrenceDeadlineUtcMillis != null) {
            while (candidate <= afterOccurrenceDeadlineUtcMillis) {
                candidate = computeNextDeadlineUtc(
                    currentDeadlineUtcMillis = candidate,
                    recurrenceType = recurrenceType.name,
                    interval = recurrenceInterval,
                )
            }
        }
        while (Instant.fromEpochMilliseconds(candidate).toLocalDateTime(timeZone).date < today) {
            candidate = computeNextDeadlineUtc(
                currentDeadlineUtcMillis = candidate,
                recurrenceType = recurrenceType.name,
                interval = recurrenceInterval,
            )
        }
        return candidate
    }

    private suspend fun dueReminderBody(minutes: Int): String = textProvider.taskDue(minutes)

    private suspend fun startingReminderBody(minutes: Int): String = textProvider.taskStarting(minutes)

    internal fun Task.legacyReminderTriggers(): List<ReminderTrigger> {
        if (dateReminders.isNotBlank() || durationReminders.isNotBlank()) return emptyList()
        val value = notifyBeforeValue.takeIf { it > 0 } ?: return emptyList()
        val minutesForLabel = when (notifyBeforeUnit) {
            NotifyBeforeUnit.NONE -> return emptyList()
            NotifyBeforeUnit.DAYS -> value * MINUTES_PER_DAY
            NotifyBeforeUnit.WEEKS -> value * MINUTES_PER_WEEK
            NotifyBeforeUnit.MONTHS -> value * MONTH_REMINDER_LABEL_DAYS * MINUTES_PER_DAY
        }
        return listOf(
            ReminderTrigger(
                minutesForLabel = minutesForLabel,
                triggerAtUtcMillis = legacyReminderTriggerUtcMillis(
                    deadlineUtcMillis = deadline ?: return emptyList(),
                    value = value,
                    unit = notifyBeforeUnit,
                ),
            )
        )
    }
}

internal fun legacyReminderTriggerUtcMillis(
    deadlineUtcMillis: Long,
    value: Int,
    unit: NotifyBeforeUnit,
): Long {
    val timeZone = TimeZone.currentSystemDefault()
    val deadlineLocal = Instant.fromEpochMilliseconds(deadlineUtcMillis)
        .toLocalDateTime(timeZone)
    val startDate = when (unit) {
        NotifyBeforeUnit.NONE -> return deadlineUtcMillis
        NotifyBeforeUnit.DAYS -> deadlineLocal.date.minus(value, DateTimeUnit.DAY)
        NotifyBeforeUnit.WEEKS -> deadlineLocal.date.minus(value * 7, DateTimeUnit.DAY)
        NotifyBeforeUnit.MONTHS -> deadlineLocal.date.minus(value, DateTimeUnit.MONTH)
    }
    return LocalDateTime(startDate, deadlineLocal.time)
        .toInstant(timeZone)
        .toEpochMilliseconds()
}
