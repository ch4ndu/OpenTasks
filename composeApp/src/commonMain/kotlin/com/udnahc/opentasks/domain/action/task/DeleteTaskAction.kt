package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository

class DeleteTaskAction(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task) {
        repository.delete(task)
    }
}
