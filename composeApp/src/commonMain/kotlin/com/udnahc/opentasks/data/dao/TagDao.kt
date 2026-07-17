package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Transaction
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert
    suspend fun insertTag(tag: Tag): Long

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT id FROM tags WHERE isDeleted = 0")
    suspend fun getActiveTagIds(): List<String>

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

    /**
     * Restores an assignment without racing an existing row's original creation time.
     * The caller supplies storage (UTC) timestamps.
     */
    @Transaction
    suspend fun restoreTaskTagPreservingCreatedAt(taskTag: TaskTag) {
        val existing = findTaskTagByIdAnyState(taskTag.taskId, taskTag.tagId)
        val createdAt = existing?.createdAt?.takeIf { it != 0L }
            ?: taskTag.createdAt.takeIf { it != 0L }
            ?: taskTag.updatedAt
        upsertTaskTag(
            taskTag.copy(
                pbId = existing?.pbId ?: taskTag.pbId,
                isDeleted = false,
                isSynced = false,
                createdAt = createdAt,
            )
        )
    }

    /** Tombstones an assignment while preserving its original creation time in one transaction. */
    @Transaction
    suspend fun tombstoneTaskTagPreservingCreatedAt(taskTag: TaskTag) {
        val existing = findTaskTagByIdAnyState(taskTag.taskId, taskTag.tagId)
        val createdAt = existing?.createdAt?.takeIf { it != 0L }
            ?: taskTag.createdAt.takeIf { it != 0L }
            ?: taskTag.updatedAt
        upsertTaskTag(
            taskTag.copy(
                pbId = existing?.pbId ?: taskTag.pbId,
                isDeleted = true,
                isSynced = false,
                createdAt = createdAt,
            )
        )
    }

    @Query("SELECT EXISTS(SELECT 1 FROM tasks WHERE id = :taskId AND isDeleted = 0)")
    suspend fun hasActiveTask(taskId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE id = :tagId AND isDeleted = 0)")
    suspend fun hasActiveTag(tagId: String): Boolean

    @Transaction
    suspend fun mergeRemoteTaskTagIfNewer(remote: TaskTag): RemoteMergeResult {
        if (!hasActiveTask(remote.taskId) || !hasActiveTag(remote.tagId)) {
            return RemoteMergeResult.MissingParent
        }
        val local = findTaskTagByIdAnyState(remote.taskId, remote.tagId)
        if (local != null && local.updatedAt >= remote.updatedAt) return RemoteMergeResult.KeptLocal
        upsertTaskTag(remote)
        return RemoteMergeResult.Applied
    }

    @Query("SELECT * FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun findTaskTagByIdAnyState(
        taskId: String,
        tagId: String
    ): TaskTag?

    @Query("SELECT * FROM task_tags WHERE isSynced = 0")
    suspend fun getUnsyncedTaskTags(): List<TaskTag>

    /** Includes tombstones so graph deletion can decide whether a parent must remain durable. */
    @Query("SELECT * FROM task_tags WHERE taskId = :taskId")
    suspend fun getTaskTagsForTaskAnyState(taskId: String): List<TaskTag>

    @Query("UPDATE task_tags SET isDeleted = 1, isSynced = 0, updatedAt = :updatedAt WHERE taskId = :taskId AND isDeleted = 0")
    suspend fun tombstoneActiveTaskTagsForTask(taskId: String, updatedAt: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM task_tags WHERE taskId = :taskId AND pbId IS NOT NULL)")
    suspend fun hasRemoteIdentityTaskTag(taskId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM task_tags WHERE tagId = :tagId AND pbId IS NOT NULL)")
    suspend fun hasRemoteIdentityTaskTagForTag(tagId: String): Boolean

    /**
     * Protect the foreign-key cascade when a local-only tag tombstone still
     * owns task-tag rows that must be synchronized to an existing remote row.
     */
    @Transaction
    suspend fun deleteTagIfNoRemoteTaskTags(tag: Tag): Boolean {
        if (hasRemoteIdentityTaskTagForTag(tag.id)) return false
        deleteTag(tag)
        return true
    }

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

    @Query("UPDATE task_tags SET pbId = NULL, isSynced = 0")
    suspend fun resetTaskTagSyncMetadataForServerSeed()

    @Query("DELETE FROM task_tags")
    suspend fun deleteAllTaskTags()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Update
    suspend fun update(tag: Tag)

    @Upsert
    suspend fun upsert(tag: Tag)

    @Transaction
    suspend fun mergeRemoteIfNewer(remote: Tag): RemoteMergeResult {
        val local = findTagByIdAnyState(remote.id)
        if (local != null && local.updatedAt >= remote.updatedAt) return RemoteMergeResult.KeptLocal
        upsert(remote)
        return RemoteMergeResult.Applied
    }

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

    @Query("UPDATE tags SET pbId = NULL, isSynced = 0")
    suspend fun resetTagSyncMetadataForServerSeed()

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsOnce(): List<Tag>
}
