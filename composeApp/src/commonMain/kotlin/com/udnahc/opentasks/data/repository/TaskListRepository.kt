package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.TaskList
import kotlinx.coroutines.flow.Flow

interface TaskListRepository {
    fun getAllLists(): Flow<List<TaskList>>
    suspend fun getListById(id: Long): TaskList?
    suspend fun insert(taskList: TaskList): Long
    suspend fun update(taskList: TaskList)
    suspend fun delete(taskList: TaskList)
}
