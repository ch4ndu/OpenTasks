package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.udnahc.opentasks.data.model.TaskCompletion
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCompletionDao {

    @Insert
    suspend fun insert(taskCompletion: TaskCompletion)

    @Delete
    suspend fun delete(taskCompletion: TaskCompletion)

    @Query("SELECT * FROM task_completions WHERE isDeleted = 0 ORDER BY completedAt DESC, id ASC")
    fun getAllTaskCompletions(): Flow<List<TaskCompletion>>

    /** Unfiltered lookup including soft-deleted rows. For sync use only. */
    @Query("SELECT * FROM task_completions WHERE id = :id")
    suspend fun findTaskCompletionByIdAnyState(id: String): TaskCompletion?

    @Query("SELECT * FROM task_completions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<TaskCompletion>

    @Query("UPDATE task_completions SET isSynced = 1 WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = :isDeleted")
    suspend fun markSyncedIfUnchanged(
        id: String,
        updatedAt: Long,
        isDeleted: Boolean
    ): Int

    @Query("UPDATE task_completions SET isSynced = 0 WHERE id = :id")
    suspend fun markUnsynced(id: String)

    @Query("UPDATE task_completions SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(
        id: String,
        pbId: String
    )

    @Query("UPDATE task_completions SET pbId = NULL, isSynced = 0")
    suspend fun resetSyncMetadataForServerSeed()

    @Upsert
    suspend fun upsert(taskCompletion: TaskCompletion)

    @Transaction
    suspend fun mergeRemoteIfNewer(remote: TaskCompletion): RemoteMergeResult {
        val local = findTaskCompletionByIdAnyState(remote.id)
        if (local != null && local.updatedAt >= remote.updatedAt) return RemoteMergeResult.KeptLocal
        upsert(remote)
        return RemoteMergeResult.Applied
    }

    @Query("SELECT * FROM task_completions")
    suspend fun getAllTaskCompletionsOnce(): List<TaskCompletion>

    @Query("DELETE FROM task_completions")
    suspend fun deleteAll()
}
