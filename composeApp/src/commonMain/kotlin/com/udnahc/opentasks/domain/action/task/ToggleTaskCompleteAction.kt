package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository

class ToggleTaskCompleteAction(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task) {
        repository.update(
            task.copy(
                isCompleted = !task.isCompleted,
                updatedAt = utcNow(),
            )
        )
    }
}
