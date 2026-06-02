package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.TaskTag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert
    suspend fun insertTag(tag: Tag): Long

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id = :id AND isDeleted = 0")
    suspend fun getTagById(id: String): Tag?

    /** Unfiltered lookup including soft-deleted rows. For sync use only. */
    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findTagByIdAnyState(id: String): Tag?

    @Query("SELECT * FROM tags WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    @Query("SELECT t.* FROM tags t INNER JOIN task_tags tt ON t.id = tt.tagId WHERE tt.taskId = :taskId AND t.isDeleted = 0 AND tt.isDeleted = 0")
    fun getTagsForTask(taskId: String): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskTag(taskTag: TaskTag)

    @Update
    suspend fun updateTaskTag(taskTag: TaskTag)

    @Delete
    suspend fun hardDeleteTaskTag(taskTag: TaskTag)

    @Upsert
    suspend fun upsertTaskTag(taskTag: TaskTag)

    @Query("SELECT * FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun findTaskTagByIdAnyState(
        taskId: String,
        tagId: String
    ): TaskTag?

    @Query("SELECT * FROM task_tags WHERE isSynced = 0")
    suspend fun getUnsyncedTaskTags(): List<TaskTag>

    @Query("SELECT * FROM task_tags")
    suspend fun getAllTaskTagsOnce(): List<TaskTag>

    @Query("UPDATE task_tags SET isSynced = 1 WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun markTaskTagSynced(
        taskId: String,
        tagId: String
    )

    @Query("UPDATE task_tags SET isSynced = 1 WHERE taskId = :taskId AND tagId = :tagId AND updatedAt = :updatedAt AND isDeleted = :isDeleted")
    suspend fun markTaskTagSyncedIfUnchanged(
        taskId: String,
        tagId: String,
        updatedAt: Long,
        isDeleted: Boolean
    ): Int

    @Query("UPDATE task_tags SET isSynced = 0 WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun markTaskTagUnsynced(
        taskId: String,
        tagId: String
    )

    @Query("UPDATE task_tags SET pbId = :pbId WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun updateTaskTagPbId(
        taskId: String,
        tagId: String,
        pbId: String
    )

    @Query("DELETE FROM task_tags")
    suspend fun deleteAllTaskTags()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Update
    suspend fun update(tag: Tag)

    @Upsert
    suspend fun upsert(tag: Tag)

    @Query("SELECT * FROM tags WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Tag>

    @Query("UPDATE tags SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE tags SET isSynced = 1 WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = :isDeleted")
    suspend fun markSyncedIfUnchanged(
        id: String,
        updatedAt: Long,
        isDeleted: Boolean
    ): Int

    @Query("UPDATE tags SET isSynced = 0 WHERE id = :id")
    suspend fun markUnsynced(id: String)

    @Query("UPDATE tags SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(
        id: String,
        pbId: String
    )

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsOnce(): List<Tag>
}
