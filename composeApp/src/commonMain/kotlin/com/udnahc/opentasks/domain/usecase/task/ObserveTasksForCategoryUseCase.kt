package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveTasksForCategoryUseCase(private val repository: TaskRepository) {
    operator fun invoke(categoryId: String): Flow<List<Task>> =
        repository.getAllTasks()
            .map { tasks -> tasks.filter { it.categoryId == categoryId } }
            .flowOn(Dispatchers.Default)
}
