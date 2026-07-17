package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.AttachmentDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.withSyncState
import com.udnahc.opentasks.data.sync.SyncTrigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AttachmentRepositoryImpl(
    private val dao: AttachmentDao,
    private val syncTrigger: SyncTrigger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AttachmentRepository {

    override fun observeForOwner(ownerType: String, ownerId: String, kind: String): Flow<List<Attachment>> =
        dao.observeForOwner(ownerType, ownerId, kind)
            .map { attachments -> attachments.map { it.withLocalTimestamps() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeTaskImageSummaries(): Flow<List<AttachmentSummary>> =
        dao.observeActiveTaskImagesOrdered()
            .map { attachments ->
                attachments
                    .groupBy { it.ownerType to it.ownerId }
                    .map { (owner, ownerAttachments) ->
                        AttachmentSummary(
                            ownerType = owner.first,
                            ownerId = owner.second,
                            imageCount = ownerAttachments.size,
                            firstThumbnailPath = ownerAttachments.firstOrNull()?.thumbnailPath,
                            worstSyncState = ownerAttachments.worstSyncState(),
                        )
                    }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun getByIdAnyState(id: String): Attachment? =
        withContext(ioDispatcher) { dao.findByIdAnyState(id)?.withLocalTimestamps() }

    override suspend fun getActiveForOwnerAnyState(ownerType: String, ownerId: String): List<Attachment> =
        withContext(ioDispatcher) {
            dao.getActiveForOwnerAnyState(ownerType, ownerId).map { it.withLocalTimestamps() }
        }

    override suspend fun nextSortOrder(ownerType: String, ownerId: String, kind: String): Int =
        withContext(ioDispatcher) { dao.maxSortOrder(ownerType, ownerId, kind) + 1 }

    override suspend fun insert(attachment: Attachment): Long {
        val result = withContext(ioDispatcher) {
            dao.insert(attachment.withDefaultTimestamps().enforceSyncInvariant().withUtcTimestamps())
        }
        syncTrigger.triggerSync()
        return result
    }

    override suspend fun update(attachment: Attachment) {
        withContext(ioDispatcher) {
            dao.update(attachment.enforceSyncInvariant().withUtcTimestamps())
        }
        syncTrigger.triggerSync()
    }

    override suspend fun delete(attachment: Attachment) {
        val deleted = attachment.copy(
            isDeleted = true,
            updatedAt = localNow(),
        ).withSyncState(AttachmentSyncState.LOCAL_ONLY)
        update(deleted)
    }

    override suspend fun hardDelete(attachment: Attachment) {
        require(attachment.pbId == null) { "Cannot hard-delete a remotely identified attachment" }
        withContext(ioDispatcher) {
            dao.delete(attachment.withUtcTimestamps())
        }
    }

    override suspend fun tombstoneActiveForOwner(ownerType: String, ownerId: String) {
        withContext(ioDispatcher) {
            dao.tombstoneActiveForOwner(ownerType, ownerId, localToUtc(localNow()))
        }
        syncTrigger.triggerSync()
    }

    private fun Attachment.withDefaultTimestamps(): Attachment {
        val now = localNow()
        return copy(
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = if (updatedAt == 0L) now else updatedAt,
        )
    }

    private fun Attachment.withLocalTimestamps(): Attachment = copy(
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt),
    )

    private fun Attachment.withUtcTimestamps(): Attachment = copy(
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )

    private fun Attachment.enforceSyncInvariant(): Attachment =
        if (syncState == AttachmentSyncState.SYNCED) copy(isSynced = true) else copy(isSynced = false)

    private fun List<Attachment>.worstSyncState(): AttachmentSyncState? {
        val states = map { it.syncState }.toSet()
        return when {
            AttachmentSyncState.BLOCKED in states -> AttachmentSyncState.BLOCKED
            AttachmentSyncState.FAILED in states -> AttachmentSyncState.FAILED
            AttachmentSyncState.NEEDS_DOWNLOAD in states -> AttachmentSyncState.NEEDS_DOWNLOAD
            AttachmentSyncState.LOCAL_ONLY in states -> AttachmentSyncState.LOCAL_ONLY
            AttachmentSyncState.SYNCED in states -> AttachmentSyncState.SYNCED
            else -> null
        }
    }
}
