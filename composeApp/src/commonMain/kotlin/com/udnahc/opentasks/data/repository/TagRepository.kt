package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.TaskTag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun getAllTags(): Flow<List<Tag>>
    suspend fun getTagById(id: String): Tag?
    suspend fun getTagByName(name: String): Tag?
    fun getTagsForTask(taskId: String): Flow<List<Tag>>
    suspend fun insertTag(tag: Tag): Long
    suspend fun deleteTag(tag: Tag)
    suspend fun insertTaskTag(taskTag: TaskTag)
    suspend fun deleteTaskTag(taskTag: TaskTag)
}
