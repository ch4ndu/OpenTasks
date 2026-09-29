package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class ObserveTasksForFilterUseCase(
    private val repository: TaskRepository,
    private val localDaySignal: LocalDaySignal,
) {
    operator fun invoke(filter: TaskListFilter): Flow<List<Task>> =
        combine(repository.getAllTasks(), localDaySignal.dates) { tasks, today ->
            val day = TaskFilterDay(today)
            tasks.filter { filter.includes(it, day) }
        }
            .flowOn(Dispatchers.Default)
}
