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
import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderKind
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

private const val MINUTES_PER_DAY = 1440
private const val MINUTES_PER_WEEK = 10080
private const val MONTH_REMINDER_LABEL_DAYS = 30
private const val MAX_OCCURRENCE_ADVANCES_PER_LOOKUP = 4096

internal data class ReminderTrigger(
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
        scheduleForTask(
            task,
            afterOccurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
            preserveDeliveredReminder = true,
        )
    }

    /**
     * Schedule reminders for a task that already has raw UTC timestamps.
     * Used by the unified rebuild queue, which bulk-reads from the UTC path.
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
            val occurrence = task.schedulingOccurrence(now, afterOccurrence) ?: return requests
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
        val hasDueNow = dateTriggers.any { it.triggerAtUtcMillis == occurrence.deadlineUtcMillis }
        val overdueAt = occurrence.deadlineUtcMillis.takeIf { it > now && !hasDueNow }
        val dateRequests = dateTriggers.mapIndexed { ordinal, trigger ->
            ReminderRequest(
                identity = ReminderIdentity(id, occurrence.deadlineUtcMillis, ReminderKind.DATE, ordinal),
                title = title,
                body = textProvider.taskDue(trigger.minutesForLabel),
                triggerAtUtcMillis = trigger.triggerAtUtcMillis,
                allowMarkDone = trigger.triggerAtUtcMillis == occurrence.deadlineUtcMillis,
            )
        }.filter { it.triggerAtUtcMillis > now }
        val durationRequests = taskForOccurrence.durationReminders.parseMinuteValues().mapIndexed { ordinal, minutes ->
            val triggerAt = (
                if (minutes == -1) {
                    taskForOccurrence.endDeadline
                } else {
                    occurrence.deadlineUtcMillis - minutes * MILLIS_PER_MINUTE
                }
            ) ?: return@mapIndexed null
            ReminderRequest(
                identity = ReminderIdentity(id, occurrence.deadlineUtcMillis, ReminderKind.DURATION, ordinal),
                title = title,
                body = if (minutes == -1) {
                    textProvider.taskEndingNow()
                } else {
                    textProvider.taskStarting(minutes)
                },
                triggerAtUtcMillis = triggerAt,
            )
        }.filterNotNull().filter { it.triggerAtUtcMillis > now }
        val overdueRequest = overdueAt?.let { triggerAt ->
            ReminderRequest(
                identity = ReminderIdentity(id, occurrence.deadlineUtcMillis, ReminderKind.OVERDUE, 0),
                title = title,
                body = textProvider.taskOverdue(),
                triggerAtUtcMillis = triggerAt,
                allowMarkDone = true,
            )
        }
        val requests = dateRequests + durationRequests + listOfNotNull(overdueRequest)
        val lastTriggerAt = requests.maxOfOrNull(ReminderRequest::triggerAtUtcMillis)
        return requests.map { request ->
            request.copy(
                rescheduleAfterFire = recurrenceType != RecurrenceType.NONE &&
                    request.triggerAtUtcMillis == lastTriggerAt,
            )
        }
    }

    private suspend fun scheduleForTask(
        task: Task,
        afterOccurrenceDeadlineUtcMillis: Long? = null,
        preserveDeliveredReminder: Boolean = false,
    ) {
        log.d { "Scheduling reminders for task ${task.id}" }
        if (preserveDeliveredReminder) {
            scheduler.cancelPendingReminders(task.id)
        } else {
            scheduler.cancelReminders(task.id)
            scheduler.stopOngoing(task.id)
        }

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

        task.requestsForOccurrence(occurrence, now).forEach { request ->
            log.v { "Scheduling ${request.identity.kind} reminder at ${request.triggerAtUtcMillis}" }
            scheduler.schedule(request)
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
            identity = ReminderIdentity(
                eventId = task.id,
                occurrenceUtcMillis = occurrenceDeadline,
                kind = ReminderKind.ONGOING,
                ordinal = 0,
            ),
            title = task.title,
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
        } ?: return null
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
    ): Long? {
        var candidate = storedDeadline
        repeat(MAX_OCCURRENCE_ADVANCES_PER_LOOKUP) {
            val boundary = maxOf(now, afterOccurrenceDeadlineUtcMillis ?: Long.MIN_VALUE)
            if (candidate > boundary) return candidate
            candidate = computeNextDeadlineUtc(
                currentDeadlineUtcMillis = candidate,
                recurrenceType = recurrenceType.name,
                interval = recurrenceInterval,
                anchorDay = recurrenceAnchorDay,
            )
        }
        log.w { "Skipped unbounded recurring task lookup for $id" }
        return null
    }

    private fun Task.firstAllDayOccurrenceOnOrAfterToday(
        storedDeadline: Long,
        now: Long,
        afterOccurrenceDeadlineUtcMillis: Long?,
    ): Long? {
        val timeZone = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        var candidate = storedDeadline
        repeat(MAX_OCCURRENCE_ADVANCES_PER_LOOKUP) {
            val afterOccurrence = afterOccurrenceDeadlineUtcMillis
            val isAfterPrevious = afterOccurrence == null || candidate > afterOccurrence
            val isTodayOrLater = Instant.fromEpochMilliseconds(candidate).toLocalDateTime(timeZone).date >= today
            if (isAfterPrevious && isTodayOrLater) return candidate
            candidate = computeNextDeadlineUtc(
                currentDeadlineUtcMillis = candidate,
                recurrenceType = recurrenceType.name,
                interval = recurrenceInterval,
                anchorDay = recurrenceAnchorDay,
            )
        }
        log.w { "Skipped unbounded recurring all-day lookup for $id" }
        return null
    }

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
