package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveTasksByDayUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<Map<Long, List<Task>>> =
        repository.getAllTasks().map { list ->
            list.mapNotNull { task -> task.deadline?.let { dl -> dayKey(dl) to task } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, tasks) -> sortCalendarTasksForDay(tasks) }
        }.flowOn(Dispatchers.Default)
}
