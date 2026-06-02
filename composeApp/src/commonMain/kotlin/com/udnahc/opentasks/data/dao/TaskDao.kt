package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.udnahc.opentasks.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<Task>>

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

    @Upsert
    suspend fun upsert(task: Task)

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<Task>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'DONE' AND deadline IS NOT NULL AND deadline >= :startUtc AND deadline < :endUtc ORDER BY deadline ASC")
    suspend fun getTasksInDateRange(
        startUtc: Long,
        endUtc: Long
    ): List<Task>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'DONE' ORDER BY deadline ASC, updatedAt DESC")
    suspend fun getActiveTasksOnce(): List<Task>
}
