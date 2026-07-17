package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Transaction
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    /** Sync-only physical cleanup for a never-uploaded tombstone after child identity checks. */
    @Delete
    suspend fun delete(task: Task)

    @Transaction
    suspend fun mutateActive(
        id: String,
        transform: (Task) -> Task?,
    ): TaskMutationStorageResult {
        val previous = getTaskById(id) ?: return TaskMutationStorageResult.Missing
        val next = transform(previous)
        if (next != null) update(next)
        return TaskMutationStorageResult.Existing(previous, next)
    }

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT id FROM tasks WHERE isDeleted = 0")
    suspend fun getActiveTaskIds(): List<String>

    @Query("SELECT * FROM tasks WHERE status != 'DONE' AND isDeleted = 0 AND deadline IS NOT NULL")
    suspend fun getTasksWithDeadlines(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    suspend fun getTaskById(id: String): Task?

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    fun observeTaskById(id: String): Flow<Task?>

    /** Unfiltered lookup including soft-deleted rows. For sync use only. */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findTaskByIdAnyState(id: String): Task?

    @Query("SELECT * FROM tasks WHERE sourceExternalId = :externalId AND isDeleted = 0 LIMIT 1")
    suspend fun getTaskByExternalId(externalId: String): Task?

    @Query("SELECT * FROM tasks WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Task>

    @Query("UPDATE tasks SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE tasks SET isSynced = 1 WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = :isDeleted")
    suspend fun markSyncedIfUnchanged(
        id: String,
        updatedAt: Long,
        isDeleted: Boolean
    ): Int

    @Query("UPDATE tasks SET isSynced = 0 WHERE id = :id")
    suspend fun markUnsynced(id: String)

    @Query("UPDATE tasks SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(
        id: String,
        pbId: String
    )

    @Query("UPDATE tasks SET pbId = NULL, isSynced = 0")
    suspend fun resetSyncMetadataForServerSeed()

    @Upsert
    suspend fun upsert(task: Task)

    @Transaction
    suspend fun mergeRemoteIfNewer(remote: Task): RemoteMergeResult {
        val local = findTaskByIdAnyState(remote.id)
        if (local != null && local.updatedAt >= remote.updatedAt) return RemoteMergeResult.KeptLocal
        upsert(remote)
        return RemoteMergeResult.Applied
    }

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<Task>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'DONE' AND deadline IS NOT NULL AND deadline >= :startUtc AND deadline < :endUtc ORDER BY deadline ASC")
    suspend fun getTasksInDateRange(
        startUtc: Long,
        endUtc: Long
    ): List<Task>

    /** Calendar surfaces include completed tasks; task-list and reminder queries do not. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND deadline IS NOT NULL AND deadline >= :startUtc AND deadline < :endUtc ORDER BY deadline ASC")
    suspend fun getTasksInDateRangeIncludingCompleted(
        startUtc: Long,
        endUtc: Long,
    ): List<Task>

    /** One-shot active-row query. Unlike widget task-list queries, it retains DONE rows. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY deadline ASC, updatedAt DESC")
    suspend fun getActiveTasksOnce(): List<Task>

    /** Task-list widget query; calendar widgets deliberately use the DONE-inclusive range query. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'DONE' ORDER BY deadline ASC, updatedAt DESC")
    suspend fun getIncompleteTasksOnce(): List<Task>
}

sealed interface TaskMutationStorageResult {
    data object Missing : TaskMutationStorageResult
    data class Existing(val previous: Task, val next: Task?) : TaskMutationStorageResult
}
