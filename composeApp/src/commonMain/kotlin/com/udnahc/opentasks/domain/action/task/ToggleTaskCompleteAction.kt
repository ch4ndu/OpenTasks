package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository

class ToggleTaskCompleteAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke(task: Task) {
        val updated = task.copy(
            isCompleted = !task.isCompleted,
            updatedAt = utcNow(),
        )
        repository.update(updated)
        scheduleTaskRemindersAction(updated)
    }
}
