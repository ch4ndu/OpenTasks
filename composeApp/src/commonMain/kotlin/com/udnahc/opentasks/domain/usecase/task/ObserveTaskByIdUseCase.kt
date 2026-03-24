package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

class ObserveTaskByIdUseCase(private val repository: TaskRepository) {
    operator fun invoke(taskId: String): Flow<Task?> = repository.observeTaskById(taskId)
}
