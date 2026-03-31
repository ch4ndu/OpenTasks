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

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: String): Tag?

    @Query("SELECT * FROM tags WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    @Query("SELECT t.* FROM tags t INNER JOIN task_tags tt ON t.id = tt.tagId WHERE tt.taskId = :taskId AND t.isDeleted = 0")
    fun getTagsForTask(taskId: String): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskTag(taskTag: TaskTag)

    @Delete
    suspend fun deleteTaskTag(taskTag: TaskTag)

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

    @Query("UPDATE tags SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(id: String, pbId: String)

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsOnce(): List<Tag>
}
