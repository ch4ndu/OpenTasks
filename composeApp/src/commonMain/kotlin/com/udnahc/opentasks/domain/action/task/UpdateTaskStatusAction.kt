package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository

class UpdateTaskStatusAction(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(task: Task, newStatus: TaskStatus) {
        repository.update(task.copy(status = newStatus, updatedAt = localNow()))
    }
}
