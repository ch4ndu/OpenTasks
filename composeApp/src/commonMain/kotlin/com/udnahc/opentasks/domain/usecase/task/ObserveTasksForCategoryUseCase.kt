package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class ObserveTasksForCategoryUseCase(private val repository: TaskRepository) {
    operator fun invoke(categoryId: StateFlow<String>): Flow<List<Task>> =
        combine(repository.getAllTasks(), categoryId) { tasks, id ->
            tasks.filter { it.categoryId == id }
        }
}
