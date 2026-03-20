package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTasksByDayUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<Map<Long, List<Task>>> =
        repository.getAllTasks().map { list ->
            list.filter { it.deadline != null }.groupBy { dayKey(it.deadline!!) }
        }
}
