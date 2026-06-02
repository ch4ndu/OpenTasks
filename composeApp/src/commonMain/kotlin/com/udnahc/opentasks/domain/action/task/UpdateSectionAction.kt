package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository

class UpdateSectionAction(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(
        task: Task,
        section: String?
    ) {
        repository.update(task.copy(section = section, updatedAt = localNow()))
    }

    suspend fun renameSection(
        tasks: List<Task>,
        newName: String
    ) {
        val now = localNow()
        for (task in tasks) {
            repository.update(task.copy(section = newName, updatedAt = now))
        }
    }

    suspend fun clearSection(tasks: List<Task>) {
        val now = localNow()
        for (task in tasks) {
            repository.update(task.copy(section = null, updatedAt = now))
        }
    }
}
