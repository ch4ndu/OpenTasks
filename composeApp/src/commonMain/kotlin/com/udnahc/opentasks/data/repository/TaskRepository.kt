package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getAllTasks(): Flow<List<Task>>
    suspend fun getTaskById(id: String): Task?
    fun observeTaskById(id: String): Flow<Task?>
    suspend fun getTaskByExternalId(externalId: String): Task?
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)
    suspend fun delete(task: Task)
    suspend fun getTasksWithDeadlines(): List<Task>
}
