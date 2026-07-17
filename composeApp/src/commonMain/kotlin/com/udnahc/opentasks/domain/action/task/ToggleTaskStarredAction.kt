package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.repository.TaskRepository

class ToggleTaskStarredAction(
    private val repository: TaskRepository,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(taskId: String): TaskWriteResult =
        coordinator.write(taskId, TaskWriteIntent.ToggleStar)
}
