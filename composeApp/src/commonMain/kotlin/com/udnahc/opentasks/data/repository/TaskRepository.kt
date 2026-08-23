package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getAllTasks(): Flow<List<Task>>
    suspend fun getTaskById(id: String): Task?
    fun observeTaskById(id: String): Flow<Task?>
    suspend fun getTaskByExternalId(externalId: String): Task?
    suspend fun insert(task: Task): CommittedMutation<Long>

    /**
     * The sole ordinary existing-task write boundary. The transform receives a
     * current active row with local timestamps and runs in the Room writer transaction.
     */
    suspend fun mutateExisting(
        id: String,
        transform: (Task) -> Task?,
    ): CommittedMutation<TaskMutationResult>

    /**
     * Deletes one task and its child relations at the single Room writer boundary.
     * Paths are returned only after the transaction commits, for best-effort file cleanup.
     */
    suspend fun deleteGraph(id: String): CommittedMutation<TaskGraphDeletionResult>
    suspend fun getTasksWithDeadlines(): List<Task>

    /** Returns task with raw UTC timestamps for notification scheduling. */
    suspend fun getTaskByIdUtc(id: String): Task?

    /** Returns all non-deleted tasks with local timestamps as a one-shot list. */
    suspend fun getAllTasksOnce(): List<Task>

    /** Returns all non-deleted tasks with raw UTC timestamps (for export generators). */
    suspend fun getAllTasksOnceUtc(): List<Task>
}

sealed interface TaskMutationResult {
    data object Missing : TaskMutationResult
    data class Existing(val previous: Task, val next: Task?) : TaskMutationResult
}

sealed interface TaskGraphDeletionResult {
    data object Missing : TaskGraphDeletionResult
    data class Deleted(
        val task: Task,
        val neverUploadedFilePaths: List<TaskAttachmentFilePaths>,
    ) : TaskGraphDeletionResult
}

data class TaskAttachmentFilePaths(
    val localPath: String,
    val thumbnailPath: String,
)
