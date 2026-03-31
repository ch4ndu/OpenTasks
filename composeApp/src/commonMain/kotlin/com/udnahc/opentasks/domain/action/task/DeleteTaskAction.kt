package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val log = logging("DeleteTaskAction")

class DeleteTaskAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke(task: Task) {
        log.d { "Soft-deleting task: ${task.id}" }
        val deleted = task.copy(isDeleted = true, updatedAt = localNow())
        repository.update(deleted)
        scheduleTaskRemindersAction(deleted.id)
    }
}
