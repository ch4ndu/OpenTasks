package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.sync.SyncTrigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TagRepositoryImpl(
    private val tagDao: TagDao,
    private val syncTrigger: SyncTrigger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TagRepository {

    override fun getAllTags(): Flow<List<Tag>> =
        tagDao.getAllTags()
            .map { tags -> tags.map { it.withLocalTimestamps() } }
            .flowOn(Dispatchers.Default)

    override suspend fun getTagById(id: String): Tag? =
        withContext(ioDispatcher) { tagDao.getTagById(id)?.withLocalTimestamps() }

    override suspend fun getTagByName(name: String): Tag? =
        withContext(ioDispatcher) { tagDao.getTagByName(name)?.withLocalTimestamps() }

    override fun getTagsForTask(taskId: String): Flow<List<Tag>> =
        tagDao.getTagsForTask(taskId)
            .map { tags -> tags.map { it.withLocalTimestamps() } }
            .flowOn(Dispatchers.Default)

    override suspend fun insertTag(tag: Tag): Long {
        val result = withContext(ioDispatcher) {
            tagDao.insertTag(tag.withDefaultTimestamps().withUtcTimestamps())
        }
        syncTrigger.triggerSync()
        return result
    }

    override suspend fun deleteTag(tag: Tag) {
        withContext(ioDispatcher) {
            tagDao.update(tag.withUtcTimestamps().copy(isDeleted = true, isSynced = false))
        }
        syncTrigger.triggerSync()
    }

    override suspend fun insertTaskTag(taskTag: TaskTag) {
        val existing = withContext(ioDispatcher) {
            tagDao.findTaskTagByIdAnyState(taskTag.taskId, taskTag.tagId)?.withLocalTimestamps()
        }
        val now = localNow()
        val current = existing ?: taskTag
        val stamped = current.copy(
            isDeleted = false,
            isSynced = false,
            createdAt = if (current.createdAt == 0L) now else current.createdAt,
            updatedAt = now,
        ).withUtcTimestamps()
        withContext(ioDispatcher) { tagDao.upsertTaskTag(stamped) }
        syncTrigger.triggerSync()
    }

    override suspend fun deleteTaskTag(taskTag: TaskTag) {
        val existing = withContext(ioDispatcher) {
            tagDao.findTaskTagByIdAnyState(taskTag.taskId, taskTag.tagId)?.withLocalTimestamps()
        } ?: taskTag
        val now = localNow()
        withContext(ioDispatcher) {
            tagDao.updateTaskTag(existing.copy(
                isDeleted = true,
                isSynced = false,
                createdAt = if (existing.createdAt == 0L) now else existing.createdAt,
                updatedAt = now,
            ).withUtcTimestamps())
        }
        syncTrigger.triggerSync()
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
