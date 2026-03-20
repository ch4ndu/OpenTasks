package com.udnahc.opentasks.domain.action.tasklist

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.TaskList
import com.udnahc.opentasks.data.repository.TaskListRepository

class AddTaskListAction(private val repository: TaskListRepository) {
    suspend operator fun invoke(name: String) {
        repository.insert(
            TaskList(
                name = name,
                createdAt = utcNow(),
            )
        )
    }
}
