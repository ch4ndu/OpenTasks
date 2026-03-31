package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl(
    private val tagDao: TagDao,
    private val triggerSyncAction: TriggerSyncAction,
) : TagRepository {

    override fun getAllTags(): Flow<List<Tag>> =
        tagDao.getAllTags().map { tags -> tags.map { it.withLocalTimestamps() } }

    override suspend fun getTagById(id: String): Tag? =
        tagDao.getTagById(id)?.withLocalTimestamps()

    override suspend fun getTagByName(name: String): Tag? =
        tagDao.getTagByName(name)?.withLocalTimestamps()

    override fun getTagsForTask(taskId: String): Flow<List<Tag>> =
        tagDao.getTagsForTask(taskId).map { tags -> tags.map { it.withLocalTimestamps() } }

    override suspend fun insertTag(tag: Tag): Long {
        val result = tagDao.insertTag(tag.withUtcTimestamps())
        triggerSyncAction()
        return result
    }

    override suspend fun deleteTag(tag: Tag) {
        tagDao.update(tag.withUtcTimestamps().copy(isDeleted = true, isSynced = false))
        triggerSyncAction()
    }

    // TaskTag is local-only; no sync adapter exists. Do not trigger sync.
    override suspend fun insertTaskTag(taskTag: TaskTag) = tagDao.insertTaskTag(taskTag)

    // TaskTag is local-only; no sync adapter exists. Do not trigger sync.
    override suspend fun deleteTaskTag(taskTag: TaskTag) = tagDao.deleteTaskTag(taskTag)

    private fun Tag.withLocalTimestamps() = copy(
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt),
    )

    private fun Tag.withUtcTimestamps() = copy(
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )
}
