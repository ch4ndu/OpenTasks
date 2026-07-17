package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("UpdateTaskAction")

class UpdateTaskAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(taskId: String, intent: TaskWriteIntent): TaskWriteResult {
        log.d { "Updating task: $taskId" }
        val result = coordinator.write(taskId, intent)
        if (result is TaskWriteResult.Updated) {
            rebuildReminderQueueAction?.afterRecordChange { scheduleTaskRemindersAction(taskId) }
                ?: scheduleTaskRemindersAction(taskId)
        }
        return result
    }
}
