package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.repository.TaskRepository

class ObserveAllTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke() = repository.getAllTasks()
}
