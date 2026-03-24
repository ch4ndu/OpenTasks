package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.TaskTag
import kotlinx.coroutines.flow.Flow

class TagRepositoryImpl(
    private val tagDao: TagDao,
) : TagRepository {

    override fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    override suspend fun getTagById(id: String): Tag? = tagDao.getTagById(id)

    override suspend fun getTagByName(name: String): Tag? = tagDao.getTagByName(name)

    override fun getTagsForTask(taskId: String): Flow<List<Tag>> = tagDao.getTagsForTask(taskId)

    override suspend fun insertTag(tag: Tag): Long = tagDao.insertTag(tag)

    override suspend fun deleteTag(tag: Tag) = tagDao.deleteTag(tag)

    override suspend fun insertTaskTag(taskTag: TaskTag) = tagDao.insertTaskTag(taskTag)

    override suspend fun deleteTaskTag(taskTag: TaskTag) = tagDao.deleteTaskTag(taskTag)
}
