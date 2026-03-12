package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val taskDao: TaskDao
) : TaskRepository {

    override fun getAllTasks(): Flow<List<Task>> =
        taskDao.getAllTasks().map { tasks -> tasks.map { it.withLocalTimestamps() } }

    override suspend fun getTaskById(id: Long): Task? =
        taskDao.getTaskById(id)?.withLocalTimestamps()

    override suspend fun insert(task: Task) = taskDao.insert(task)

    override suspend fun update(task: Task) = taskDao.update(task)

    override suspend fun delete(task: Task) = taskDao.delete(task)

    /** Converts UTC timestamps from the database to local time for presentation. */
    private fun Task.withLocalTimestamps() = copy(
        deadline = deadline?.let { utcToLocal(it) },
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt)
    )
}
