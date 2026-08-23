package com.udnahc.opentasks.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.dao.TaskMutationStorageResult
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.auth.AccountMutationGate
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
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Test-only failure point proving the cross-DAO writer transaction rolls back as one unit. */
    internal val beforeTaskGraphParentTombstone: (() -> Unit)? = null,
    private val mutationGate: AccountMutationGate,
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

    override suspend fun insert(task: Task): CommittedMutation<Long> = mutationGate.withExclusive {
        log.v { "Inserting task: ${task.id}" }
        val result = withContext(ioDispatcher) {
            taskDao.insert(task.withDefaultTimestamps().withUtcTimestamps())
        }
        CommittedMutation(result).withPostCommitWarning(
            triggerSyncAfterCommit(),
            PostCommitWarningPhase.SYNC,
        )
    }

    override suspend fun mutateExisting(
        id: String,
        transform: (Task) -> Task?,
    ): CommittedMutation<TaskMutationResult> = mutationGate.withExclusive {
        val result = withContext(ioDispatcher) {
            taskDao.mutateActive(id) { stored ->
                transform(stored.withLocalTimestamps())
                    ?.withUtcTimestamps()
                    ?.copy(isSynced = false)
            }
        }
        val mapped = when (result) {
            TaskMutationStorageResult.Missing -> TaskMutationResult.Missing
            is TaskMutationStorageResult.Existing -> TaskMutationResult.Existing(
                previous = result.previous.withLocalTimestamps(),
                next = result.next?.withLocalTimestamps(),
            )
        }
        val warning = if (mapped is TaskMutationResult.Existing && mapped.next != null) {
            triggerSyncAfterCommit()
        } else {
            null
        }
        CommittedMutation(mapped).withPostCommitWarning(warning, PostCommitWarningPhase.SYNC)
    }

    override suspend fun deleteGraph(id: String): CommittedMutation<TaskGraphDeletionResult> = mutationGate.withExclusive {
        val nowUtc = utcNow()
        val result = withContext(ioDispatcher) {
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    val previous = taskDao.getTaskById(id) ?: return@immediateTransaction TaskGraphDeletionResult.Missing
                    val tagDao = database.tagDao()
                    val attachmentDao = database.attachmentDao()
                    val taskTags = tagDao.getTaskTagsForTaskAnyState(id)
                    val attachments = attachmentDao.getForOwnerAnyState(ATTACHMENT_OWNER_TASK, id)
                    val neverUploadedFiles = attachments.asSequence()
                        .filter { !it.isDeleted && it.pbId == null }
                        .map { TaskAttachmentFilePaths(it.localPath, it.thumbnailPath) }
                        .toList()
                    val updatedAtUtc = maxOf(
                        nowUtc,
                        previous.updatedAt + 1,
                        taskTags.filterNot { it.isDeleted }.maxOfOrNull { it.updatedAt + 1 } ?: Long.MIN_VALUE,
                        attachments.filterNot { it.isDeleted }.maxOfOrNull { it.updatedAt + 1 } ?: Long.MIN_VALUE,
                    )

                    // Child tombstones are written before the parent in this same transaction.
                    tagDao.tombstoneActiveTaskTagsForTask(id, updatedAtUtc)
                    attachmentDao.tombstoneActiveForOwner(ATTACHMENT_OWNER_TASK, id, updatedAtUtc)
                    beforeTaskGraphParentTombstone?.invoke()
                    taskDao.update(previous.copy(isDeleted = true, isSynced = false, updatedAt = updatedAtUtc))
                    TaskGraphDeletionResult.Deleted(
                        task = previous.withLocalTimestamps(),
                        neverUploadedFilePaths = neverUploadedFiles,
                    )
                }
            }
        }
        val warning = if (result is TaskGraphDeletionResult.Deleted) {
            triggerSyncAfterCommit()
        } else {
            null
        }
        CommittedMutation(result).withPostCommitWarning(warning, PostCommitWarningPhase.SYNC)
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
            taskDao.getActiveTasksOnce()
                .map { it.withLocalTimestamps() }
        }

    override suspend fun getAllTasksOnceUtc(): List<Task> =
        withContext(ioDispatcher) {
            taskDao.getActiveTasksOnce()
        }

    /** Fills in 0L timestamps with current local time before insert. */
    private fun Task.withDefaultTimestamps(): Task {
        val now = localNow()
        return copy(
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = if (updatedAt == 0L) now else updatedAt,
        )
    }

    private suspend fun triggerSyncAfterCommit(): Throwable? = try {
        syncTrigger.triggerSync()
        null
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        log.w(error) { "Task write committed, but sync scheduling failed" }
        error
    }

    /** Converts UTC timestamps from the database to local time for presentation. */
    private fun Task.withLocalTimestamps() = copy(
        deadline = deadline?.let { utcToLocal(it) },
        endDeadline = endDeadline?.let { utcToLocal(it) },
        completedAt = completedAt?.let { utcToLocal(it) },
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt)
    )

    /** Converts local-shifted timestamps to UTC for database storage. */
    private fun Task.withUtcTimestamps() = copy(
        deadline = deadline?.let { localToUtc(it) },
        endDeadline = endDeadline?.let { localToUtc(it) },
        completedAt = completedAt?.let { localToUtc(it) },
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )
}
