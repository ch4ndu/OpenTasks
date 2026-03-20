package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class ObserveTasksForPriorityUseCase(private val repository: TaskRepository) {
    operator fun invoke(priority: StateFlow<TaskPriority>): Flow<List<Task>> =
        combine(repository.getAllTasks(), priority) { tasks, p ->
            tasks.filter { it.priority == p }
        }
}
