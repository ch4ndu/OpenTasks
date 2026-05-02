package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository

class ToggleTaskStarredAction(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(task: Task) {
        repository.update(task.copy(isStarred = !task.isStarred, updatedAt = localNow()))
    }
}
