package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val log = logging("ScheduleTaskRemindersAction")

private const val MILLIS_PER_MINUTE = 60_000L
private const val OVERDUE_REMINDER_ID = 49
private const val DURATION_REMINDER_OFFSET = 50

class ScheduleTaskRemindersAction(
    private val scheduler: NotificationScheduler,
    private val taskRepository: TaskRepository,
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
            return
        }
        scheduleForTask(task)
    }

    /**
     * Schedule reminders for a task that already has raw UTC timestamps.
     * Used by RescheduleAllRemindersAction which bulk-reads from the UTC path.
     */
    fun invokeWithUtcTask(task: Task) {
        scheduleForTask(task)
    }

    private fun scheduleForTask(task: Task) {
        log.d { "Scheduling reminders for task ${task.id}" }
        scheduler.cancelReminders(task.id)

        if (task.isCompleted || task.isDeleted || task.deadline == null) {
            log.d { "Cancelled reminders for completed/deleted task ${task.id}" }
            scheduler.stopOngoing(task.id)
            return
        }

        val now = utcNow()

        // Date reminders (minutes before deadline, stored as ReminderOption.minutesValue)
        if (task.dateReminders.isNotBlank()) {
            val minuteValues = task.dateReminders.split(",").mapNotNull { it.trim().toIntOrNull() }
            minuteValues.forEachIndexed { index, mins ->
                val triggerAt = task.deadline - (mins * MILLIS_PER_MINUTE)
                if (triggerAt > now) {
                    log.v { "Scheduled date reminder $index at $triggerAt" }
                    scheduler.schedule(
                        taskId = task.id,
                        title = task.title,
                        body = when {
                            mins == 0 -> "Due now"
                            mins < 60 -> "Due in $mins min"
                            mins < 1440 -> {
                                val hours = mins / 60
                                "Due in $hours hour${if (hours > 1) "s" else ""}"
                            }
                            else -> {
                                val days = mins / 1440
                                "Due in $days day${if (days > 1) "s" else ""}"
                            }
                        },
                        triggerAtMillis = triggerAt,
                        reminderId = index,
                    )
                }
            }
        }

        // Duration reminders (minutes before deadline)
        if (task.durationReminders.isNotBlank()) {
            val minuteValues =
                task.durationReminders.split(",").mapNotNull { it.trim().toIntOrNull() }
            minuteValues.forEachIndexed { index, mins ->
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
                        body = if (mins == -1) "Task ending now" else when {
                            mins == 0 -> "Starting now"
                            mins < 60 -> "Starting in $mins min"
                            mins == 60 -> "Starting in 1 hour"
                            else -> "Starting in ${mins / 60} hours"
                        },
                        triggerAtMillis = triggerAt,
                        reminderId = DURATION_REMINDER_OFFSET + index,
                    )
                }
            }
        }

        // Overdue notification — fires at the moment the deadline passes
        // Skip if a "Due now" (0-minute) date reminder already fires at the same time
        val hasDueNowReminder = task.dateReminders.isNotBlank() &&
            task.dateReminders.split(",").mapNotNull { it.trim().toIntOrNull() }.any { it == 0 }

        if (task.deadline > now && !hasDueNowReminder) {
            log.v { "Scheduled overdue notification at ${task.deadline}" }
            scheduler.schedule(
                taskId = task.id,
                title = task.title,
                body = "Overdue",
                triggerAtMillis = task.deadline,
                reminderId = OVERDUE_REMINDER_ID,
            )
        }

        // Ongoing notification for all-day tasks
        if (task.isAllDay) {
            scheduler.startOngoing(task.id, task.title)
        } else {
            scheduler.stopOngoing(task.id)
        }
    }
}
