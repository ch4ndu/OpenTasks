package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.task_reminder_due_in_day
import opentasks.composeapp.generated.resources.task_reminder_due_in_days
import opentasks.composeapp.generated.resources.task_reminder_due_in_hour
import opentasks.composeapp.generated.resources.task_reminder_due_in_hours
import opentasks.composeapp.generated.resources.task_reminder_due_in_minutes
import opentasks.composeapp.generated.resources.task_reminder_due_now
import opentasks.composeapp.generated.resources.task_reminder_ending_now
import opentasks.composeapp.generated.resources.task_reminder_overdue
import opentasks.composeapp.generated.resources.task_reminder_starting_in_hour
import opentasks.composeapp.generated.resources.task_reminder_starting_in_hours
import opentasks.composeapp.generated.resources.task_reminder_starting_in_minutes
import opentasks.composeapp.generated.resources.task_reminder_starting_now
import org.jetbrains.compose.resources.getString
import org.lighthousegames.logging.logging

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

class ScheduleTaskRemindersAction(
    private val scheduler: ReminderScheduler,
    private val taskRepository: TaskRepository,
    private val allDayDismissalStore: AllDayNotificationDismissalStore? = null,
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

    /**
     * Schedule reminders for a task that already has raw UTC timestamps.
     * Used by RescheduleAllRemindersAction which bulk-reads from the UTC path.
     */
    suspend fun invokeWithUtcTask(task: Task) {
        scheduleForTask(task)
    }

    private suspend fun scheduleForTask(task: Task) {
        log.d { "Scheduling reminders for task ${task.id}" }
        scheduler.cancelReminders(task.id)
        scheduler.stopOngoing(task.id)

        if (task.status == TaskStatus.DONE || task.isDeleted || task.deadline == null) {
            log.d { "Cancelled reminders for completed/deleted task ${task.id}" }
            return
        }

        val now = nowUtcMillisProvider()
        scheduleAllDayOngoingIfNeeded(task, now)

        val dateReminderValues = task.dateReminders.parseMinuteValues()
        val durationReminderValues = task.durationReminders.parseMinuteValues()
        val dateReminderTriggers = dateReminderValues
            .map { mins -> ReminderTrigger(mins, task.deadline - (mins.toLong() * MILLIS_PER_MINUTE)) }
            .ifEmpty { task.legacyReminderTriggers() }

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
                )
            } else {
                log.v { "Skipped past date reminder $index at ${trigger.triggerAtUtcMillis}" }
            }
        }

        // Duration reminders (minutes before deadline)
        durationReminderValues.forEachIndexed { index, mins ->
            val triggerAt = if (mins == -1) {
                // AT_THE_END: fire at endDeadline if available, skip otherwise
                task.endDeadline ?: return@forEachIndexed
            } else {
                task.deadline - (mins * MILLIS_PER_MINUTE)
            }
            if (triggerAt > now) {
                log.v { "Scheduled duration reminder $index at $triggerAt" }
                scheduler.schedule(
                    taskId = task.id,
                    title = task.title,
                    body = if (mins == -1) {
                        getString(Res.string.task_reminder_ending_now)
                    } else {
                        startingReminderBody(mins)
                    },
                    triggerAtMillis = triggerAt,
                    reminderId = DURATION_REMINDER_OFFSET + index,
                )
            } else {
                log.v { "Skipped past duration reminder $index at $triggerAt" }
            }
        }

        // Overdue notification — fires at the moment the deadline passes
        // Skip if a zero-minute date reminder already fires at the same time.
        val hasDueNowReminder = dateReminderTriggers.any { it.triggerAtUtcMillis == task.deadline } ||
            durationReminderValues.any { mins ->
                if (mins == -1) task.endDeadline == task.deadline else mins == 0
            }

        if (task.deadline > now && !hasDueNowReminder) {
            log.v { "Scheduled overdue notification at ${task.deadline}" }
            scheduler.schedule(
                taskId = task.id,
                title = task.title,
                body = getString(Res.string.task_reminder_overdue),
                triggerAtMillis = task.deadline,
                reminderId = OVERDUE_REMINDER_ID,
            )
        }
    }

    private suspend fun scheduleAllDayOngoingIfNeeded(task: Task, now: Long) {
        if (!task.isAllDay) return
        if (!task.isDueToday(now)) {
            log.d { "Skipped all-day ongoing notification for task ${task.id}: not due today" }
            return
        }
        if (allDayDismissalStore?.isDismissedToday(task.id) == true) {
            log.d { "Skipped all-day ongoing notification for task ${task.id}: dismissed today" }
            return
        }
        log.d { "Starting all-day ongoing notification for task ${task.id}" }
        scheduler.startOngoing(task.id, task.title)
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

    private suspend fun dueReminderBody(minutes: Int): String = when {
        minutes == 0 -> getString(Res.string.task_reminder_due_now)
        minutes < 60 -> getString(Res.string.task_reminder_due_in_minutes, minutes)
        minutes < 1440 -> {
            val hours = minutes / 60
            getString(
                if (hours == 1) {
                    Res.string.task_reminder_due_in_hour
                } else {
                    Res.string.task_reminder_due_in_hours
                },
                hours,
            )
        }
        else -> {
            val days = minutes / 1440
            getString(
                if (days == 1) {
                    Res.string.task_reminder_due_in_day
                } else {
                    Res.string.task_reminder_due_in_days
                },
                days,
            )
        }
    }

    private suspend fun startingReminderBody(minutes: Int): String = when {
        minutes == 0 -> getString(Res.string.task_reminder_starting_now)
        minutes < 60 -> getString(Res.string.task_reminder_starting_in_minutes, minutes)
        else -> {
            val hours = minutes / 60
            getString(
                if (hours == 1) {
                    Res.string.task_reminder_starting_in_hour
                } else {
                    Res.string.task_reminder_starting_in_hours
                },
                hours,
            )
        }
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
