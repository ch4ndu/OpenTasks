package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class ObserveTasksForListUseCase(private val repository: TaskRepository) {
    operator fun invoke(listId: StateFlow<Long>): Flow<List<Task>> =
        combine(repository.getAllTasks(), listId) { tasks, id ->
            tasks.filter { it.listId == id }
        }
}
