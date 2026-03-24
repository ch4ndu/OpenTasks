package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTasksByPriorityUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<Map<TaskPriority, List<Task>>> =
        repository.getAllTasks().map { list ->
            list.groupBy { it.priority }
                .mapValues { (_, tasks) -> sortedTasks(tasks) }
        }
}

private fun sortedTasks(tasks: List<Task>): List<Task> =
    tasks.sortedWith(
        compareBy<Task> { it.isCompleted }
            .thenBy { it.deadline == null }
            .thenBy { it.deadline ?: Long.MAX_VALUE }
    )
