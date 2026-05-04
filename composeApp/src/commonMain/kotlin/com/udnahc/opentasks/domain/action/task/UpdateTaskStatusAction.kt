package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository

class UpdateTaskStatusAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke(task: Task, newStatus: TaskStatus) {
        val updated = task.copy(status = newStatus, updatedAt = localNow())
        repository.update(updated)
        scheduleTaskRemindersAction(updated.id)
    }
}
