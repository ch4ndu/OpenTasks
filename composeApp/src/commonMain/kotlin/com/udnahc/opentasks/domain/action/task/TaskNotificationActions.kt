package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val taskNotificationLog = logging("TaskNotificationActions")

class MarkTaskNotificationDoneAction(
    private val updateTaskAction: UpdateTaskAction,
) {
    suspend operator fun invoke(
        taskId: String,
        occurrenceDeadlineUtcMillis: Long? = null,
    ) {
        updateTaskAction(
            taskId,
            TaskWriteIntent.NotificationMarkDone(occurrenceDeadlineUtcMillis?.let {
                com.udnahc.opentasks.data.extensions.utcToLocal(it)
            }),
        )
    }
}

class DismissTaskNotificationAction(
    private val taskRepository: TaskRepository,
    private val allDayNotificationDismissalStore: AllDayNotificationDismissalStore,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(taskId: String, semanticKey: String? = null) {
        val task = taskRepository.getTaskById(taskId)
        if (task?.isAllDay == true) {
            allDayNotificationDismissalStore.dismissToday(taskId)
        }
        if (semanticKey != null) reminderScheduler.cancel(semanticKey)
        else reminderScheduler.stopOngoing(taskId)
    }
}
