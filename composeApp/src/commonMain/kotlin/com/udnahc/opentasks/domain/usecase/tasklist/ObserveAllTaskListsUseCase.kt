package com.udnahc.opentasks.domain.usecase.tasklist

import com.udnahc.opentasks.data.model.TaskList
import com.udnahc.opentasks.data.repository.TaskListRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllTaskListsUseCase(private val repository: TaskListRepository) {
    operator fun invoke(): Flow<List<TaskList>> = repository.getAllLists()
}
