package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val log = logging("UpdateTaskAction")

class UpdateTaskAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke(task: Task) {
        log.d { "Updating task: ${task.id}" }
        val updated = task.copy(updatedAt = utcNow())
        repository.update(updated)
        scheduleTaskRemindersAction(updated)
    }
}
