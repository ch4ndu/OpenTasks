package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction

class UpdateTaskStatusAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(
        taskId: String,
        newStatus: TaskStatus
    ): TaskWriteResult {
        val result = coordinator.write(taskId, TaskWriteIntent.SetStatus(newStatus))
        if (result is TaskWriteResult.Updated) {
            rebuildReminderQueueAction?.afterRecordChange { scheduleTaskRemindersAction(taskId) }
                ?: scheduleTaskRemindersAction(taskId)
        }
        return result
    }
}
