package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val triggerSyncAction: TriggerSyncAction,
) : TaskRepository {

    override fun getAllTasks(): Flow<List<Task>> =
        taskDao.getAllTasks()
            .map { tasks -> tasks.map { it.withLocalTimestamps() } }
            .flowOn(Dispatchers.Default)

    override suspend fun getTaskById(id: String): Task? =
        taskDao.getTaskById(id)?.withLocalTimestamps()

    override fun observeTaskById(id: String): Flow<Task?> =
        taskDao.observeTaskById(id)
            .map { it?.withLocalTimestamps() }
            .flowOn(Dispatchers.Default)

    override suspend fun getTaskByExternalId(externalId: String): Task? =
        taskDao.getTaskByExternalId(externalId)

    override suspend fun insert(task: Task): Long {
        val result = taskDao.insert(task)
        triggerSyncAction()
        return result
    }

    override suspend fun update(task: Task) {
        taskDao.update(task)
        triggerSyncAction()
    }

    override suspend fun delete(task: Task) {
        taskDao.delete(task)
        triggerSyncAction()
    }

    override suspend fun getTasksWithDeadlines(): List<Task> =
        taskDao.getTasksWithDeadlines()

    /** Converts UTC timestamps from the database to local time for presentation. */
    private fun Task.withLocalTimestamps() = copy(
        deadline = deadline?.let { utcToLocal(it) },
        endDeadline = endDeadline?.let { utcToLocal(it) },
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt)
    )
}
