package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val taskNotificationLog = logging("TaskNotificationActions")

class MarkTaskNotificationDoneAction(
    private val taskRepository: TaskRepository,
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
) {
    suspend operator fun invoke(
        taskId: String,
        occurrenceDeadlineUtcMillis: Long? = null,
    ) {
        val task = taskRepository.getTaskById(taskId)
        if (task == null) {
            taskNotificationLog.d { "Task $taskId not found for notification Mark Done" }
            return
        }
        toggleTaskCompleteAction(
            task = task,
            occurrenceDeadlineLocalMillis = occurrenceDeadlineUtcMillis?.let { utcToLocal(it) },
        )
    }
}

class DismissTaskNotificationAction(
    private val taskRepository: TaskRepository,
    private val allDayNotificationDismissalStore: AllDayNotificationDismissalStore,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(taskId: String) {
        val task = taskRepository.getTaskById(taskId)
        if (task?.isAllDay == true) {
            allDayNotificationDismissalStore.dismissToday(taskId)
        }
        reminderScheduler.stopOngoing(taskId)
    }
}
