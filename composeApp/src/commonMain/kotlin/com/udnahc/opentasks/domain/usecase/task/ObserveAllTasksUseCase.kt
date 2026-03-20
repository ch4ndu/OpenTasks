package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<Task>> = repository.getAllTasks()
}
