package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.sync.SyncTrigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging

private val log = logging("TaskRepository")

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val syncTrigger: SyncTrigger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TaskRepository {

    override fun getAllTasks(): Flow<List<Task>> =
        taskDao.getAllTasks()
            .map { tasks -> tasks.map { it.withLocalTimestamps() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun getTaskById(id: String): Task? =
        withContext(ioDispatcher) { taskDao.getTaskById(id)?.withLocalTimestamps() }

    override fun observeTaskById(id: String): Flow<Task?> =
        taskDao.observeTaskById(id)
            .map { it?.withLocalTimestamps() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun getTaskByExternalId(externalId: String): Task? =
        withContext(ioDispatcher) { taskDao.getTaskByExternalId(externalId)?.withLocalTimestamps() }

    override suspend fun insert(task: Task): Long {
        log.v { "Inserting task: ${task.id}" }
        val result = withContext(ioDispatcher) {
            taskDao.insert(task.withDefaultTimestamps().withUtcTimestamps())
        }
        syncTrigger.triggerSync()
        return result
    }

    override suspend fun update(task: Task) {
        log.v { "Updating task: ${task.id}" }
        withContext(ioDispatcher) {
            taskDao.update(task.withUtcTimestamps().copy(isSynced = false))
        }
        syncTrigger.triggerSync()
    }

    override suspend fun delete(task: Task) {
        log.v { "Soft-deleting task: ${task.id}" }
        withContext(ioDispatcher) {
            taskDao.update(task.withUtcTimestamps().copy(isDeleted = true, isSynced = false))
        }
        syncTrigger.triggerSync()
    }

    /** Returns tasks with raw UTC timestamps (no local conversion) for notification scheduling.
     *  ScheduleTaskRemindersAction and AlarmManager require UTC millis. */
    override suspend fun getTasksWithDeadlines(): List<Task> =
        withContext(ioDispatcher) { taskDao.getTasksWithDeadlines() }

    /** Returns a single task with raw UTC timestamps for notification scheduling. */
    override suspend fun getTaskByIdUtc(id: String): Task? =
        withContext(ioDispatcher) { taskDao.getTaskById(id) }

    override suspend fun getAllTasksOnce(): List<Task> =
        withContext(ioDispatcher) {
            taskDao.getAllTasksOnce()
                .filter { !it.isDeleted }
                .map { it.withLocalTimestamps() }
        }

    override suspend fun getAllTasksOnceUtc(): List<Task> =
        withContext(ioDispatcher) {
            taskDao.getAllTasksOnce()
                .filter { !it.isDeleted }
        }

    /** Fills in 0L timestamps with current local time before insert. */
    private fun Task.withDefaultTimestamps(): Task {
        val now = localNow()
        return copy(
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = if (updatedAt == 0L) now else updatedAt,
        )
    }

    /** Converts UTC timestamps from the database to local time for presentation. */
    private fun Task.withLocalTimestamps() = copy(
        deadline = deadline?.let { utcToLocal(it) },
        endDeadline = endDeadline?.let { utcToLocal(it) },
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt)
    )

    /** Converts local-shifted timestamps to UTC for database storage. */
    private fun Task.withUtcTimestamps() = copy(
        deadline = deadline?.let { localToUtc(it) },
        endDeadline = endDeadline?.let { localToUtc(it) },
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )
}
