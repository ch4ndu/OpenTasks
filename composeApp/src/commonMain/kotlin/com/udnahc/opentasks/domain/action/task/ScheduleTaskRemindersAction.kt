package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.notification.NotificationScheduler

private const val MILLIS_PER_DAY = 86_400_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val DURATION_REMINDER_OFFSET = 50

class ScheduleTaskRemindersAction(private val scheduler: NotificationScheduler) {
    operator fun invoke(task: Task) {
        scheduler.cancelReminders(task.id)

        if (task.isCompleted || task.deadline == null) {
            scheduler.stopOngoing(task.id)
            return
        }

        val now = utcNow()

        // Date reminders (days before deadline)
        if (task.dateReminders.isNotBlank()) {
            val dayValues = task.dateReminders.split(",").mapNotNull { it.trim().toIntOrNull() }
            dayValues.forEachIndexed { index, days ->
                val triggerAt = task.deadline - (days * MILLIS_PER_DAY)
                if (triggerAt > now) {
                    scheduler.schedule(
                        taskId = task.id,
                        title = task.title,
                        body = if (days == 0) "Due today" else "Due in $days day${if (days > 1) "s" else ""}",
                        triggerAtMillis = triggerAt,
                        reminderId = index,
                    )
                }
            }
        }

        // Duration reminders (minutes before deadline)
        if (task.durationReminders.isNotBlank()) {
            val minuteValues = task.durationReminders.split(",").mapNotNull { it.trim().toIntOrNull() }
            minuteValues.forEachIndexed { index, mins ->
                if (mins == -1) return@forEachIndexed // AT_THE_END handled separately if needed
                val triggerAt = task.deadline - (mins * MILLIS_PER_MINUTE)
                if (triggerAt > now) {
                    scheduler.schedule(
                        taskId = task.id,
                        title = task.title,
                        body = when {
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

        // Ongoing notification for all-day tasks
        if (task.isAllDay) {
            scheduler.startOngoing(task.id, task.title)
        } else {
            scheduler.stopOngoing(task.id)
        }
    }
}
