package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("UpdateTaskAction")

class UpdateTaskAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
) {
    suspend operator fun invoke(task: Task) {
        log.d { "Updating task: ${task.id}" }
        val updated = task.copy(updatedAt = localNow())
        repository.update(updated)
        rebuildReminderQueueAction?.afterRecordChange { scheduleTaskRemindersAction(updated.id) }
            ?: scheduleTaskRemindersAction(updated.id)
    }
}
