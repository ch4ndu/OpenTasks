package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.extensions.localNow
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
        val result = tagDao.insertTag(tag.withDefaultTimestamps().withUtcTimestamps())
        triggerSyncAction()
        return result
    }

    override suspend fun deleteTag(tag: Tag) {
        tagDao.update(tag.withUtcTimestamps().copy(isDeleted = true, isSynced = false))
        triggerSyncAction()
    }

    override suspend fun insertTaskTag(taskTag: TaskTag) {
        val existing = tagDao.findTaskTagByIdAnyState(taskTag.taskId, taskTag.tagId)?.withLocalTimestamps()
        val now = localNow()
        val current = existing ?: taskTag
        val stamped = current.copy(
            isDeleted = false,
            isSynced = false,
            createdAt = if (current.createdAt == 0L) now else current.createdAt,
            updatedAt = now,
        ).withUtcTimestamps()
        tagDao.upsertTaskTag(stamped)
        triggerSyncAction()
    }

    override suspend fun deleteTaskTag(taskTag: TaskTag) {
        val existing = tagDao.findTaskTagByIdAnyState(taskTag.taskId, taskTag.tagId)?.withLocalTimestamps() ?: taskTag
        val now = localNow()
        tagDao.updateTaskTag(
            existing.copy(
                isDeleted = true,
                isSynced = false,
                createdAt = if (existing.createdAt == 0L) now else existing.createdAt,
                updatedAt = now,
            ).withUtcTimestamps()
        )
        triggerSyncAction()
    }

    private fun Tag.withDefaultTimestamps(): Tag {
        val now = localNow()
        return copy(
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = if (updatedAt == 0L) now else updatedAt,
        )
    }

    private fun Tag.withLocalTimestamps() = copy(
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt),
    )

    private fun Tag.withUtcTimestamps() = copy(
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )

    private fun TaskTag.withUtcTimestamps() = copy(
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )

    private fun TaskTag.withLocalTimestamps() = copy(
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt),
    )
}
