package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Transaction
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentFileCleanup
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Update
    suspend fun update(attachment: Attachment)

    @Delete
    suspend fun delete(attachment: Attachment)

    @Upsert
    suspend fun upsert(attachment: Attachment)

    @Transaction
    suspend fun mergeRemoteIfNewer(remote: Attachment): RemoteMergeResult {
        val local = findByIdAnyState(remote.id)
        if (local != null && local.updatedAt >= remote.updatedAt) return RemoteMergeResult.KeptLocal
        upsert(remote)
        return RemoteMergeResult.Applied
    }

    /**
     * Download failure and policy state are derived from a remote row. Re-read
     * the row inside the writer transaction so a newer local edit or tombstone
     * keeps its metadata, ordering, ownership, and unsynced state.
     */
    @Transaction
    suspend fun mergeRemoteWithRetainedFilesIfNewer(
        remote: Attachment,
        syncState: AttachmentSyncState,
        lastSyncError: String,
    ): RemoteMergeResult {
        val local = findByIdAnyState(remote.id)
        if (local != null && local.updatedAt >= remote.updatedAt) return RemoteMergeResult.KeptLocal
        upsert(
            remote.retainingFileMetadataFrom(local).copy(
                syncState = syncState,
                lastSyncError = lastSyncError,
                isSynced = false,
            )
        )
        return RemoteMergeResult.Applied
    }

    /**
     * A same-file retry may clear a download failure at an equal timestamp,
     * but no other equal-timestamp local state may be replaced.
     */
    @Transaction
    suspend fun mergeSameFileRemoteIfNewer(remote: Attachment): RemoteMergeResult {
        val local = findByIdAnyState(remote.id)
        val isRemoteDownloadRetry = local?.let {
            !it.isDeleted &&
                it.syncState == AttachmentSyncState.FAILED &&
                (it.lastSyncError == "download_failed" || it.lastSyncError?.startsWith("download_http_") == true)
        } == true
        if (local != null && (local.updatedAt > remote.updatedAt ||
                (local.updatedAt == remote.updatedAt && !isRemoteDownloadRetry))) {
            return RemoteMergeResult.KeptLocal
        }
        upsert(
            remote.retainingFileMetadataFrom(local).copy(
                syncState = AttachmentSyncState.SYNCED,
                lastSyncError = null,
                isSynced = true,
            )
        )
        return RemoteMergeResult.Applied
    }

    /** Returns the exact persisted predecessor whose files may be removed after commit. */
    @Transaction
    suspend fun mergeRemoteTombstoneIfNewer(remote: Attachment): AttachmentTombstoneMergeResult {
        val local = findByIdAnyState(remote.id)
        if (local != null && local.updatedAt >= remote.updatedAt) {
            return AttachmentTombstoneMergeResult.KeptLocal
        }
        upsert(
            local?.let {
                remote.copy(
                    localPath = it.localPath,
                    thumbnailPath = it.thumbnailPath,
                )
            } ?: remote
        )
        return AttachmentTombstoneMergeResult.Applied(local)
    }

    /**
     * The image bytes are written before this transaction.  Re-read the row at
     * the writer boundary before making those paths visible so a concurrent
     * local edit or tombstone wins and the caller can remove the unused files.
     */
    @Transaction
    suspend fun installDownloadedRemoteIfNewer(remote: Attachment): AttachmentDownloadInstallResult {
        val local = findByIdAnyState(remote.id)
        val isEqualTimestampDownloadRetry = local?.let {
            it.updatedAt == remote.updatedAt &&
                it.syncState == com.udnahc.opentasks.data.model.AttachmentSyncState.FAILED &&
                (it.lastSyncError == "download_failed" || it.lastSyncError?.startsWith("download_http_") == true)
        } == true
        if (local != null && (local.updatedAt > remote.updatedAt ||
                (local.updatedAt == remote.updatedAt && !isEqualTimestampDownloadRetry))) {
            return AttachmentDownloadInstallResult.KeptLocal
        }
        val replacementPaths = setOf(remote.localPath, remote.thumbnailPath)
        val predecessorCleanup = local
            ?.let { listOf(it.localPath, it.thumbnailPath) }
            .orEmpty()
            .filter { it.isNotBlank() && it !in replacementPaths }
            .distinct()
            .map(::AttachmentFileCleanup)
        if (predecessorCleanup.isNotEmpty()) {
            upsertAttachmentFileCleanup(predecessorCleanup)
        }
        upsert(remote)
        if (findByIdAnyState(remote.id) != remote) {
            throw IllegalStateException("Attachment remote install verification failed")
        }
        return AttachmentDownloadInstallResult.Applied(local)
    }

    @Upsert
    suspend fun upsertAttachmentFileCleanup(entries: List<AttachmentFileCleanup>)

    @Query("SELECT path FROM attachment_file_cleanup ORDER BY path ASC")
    suspend fun getAttachmentFileCleanupPaths(): List<String>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM attachments
            WHERE localPath = :path OR thumbnailPath = :path
        )
        """
    )
    suspend fun isAttachmentFilePathReferenced(path: String): Boolean

    @Query("DELETE FROM attachment_file_cleanup WHERE path = :path")
    suspend fun deleteAttachmentFileCleanupPath(path: String): Int

    @Query("DELETE FROM attachment_file_cleanup")
    suspend fun deleteAllAttachmentFileCleanup()

    @Query(
        """
        SELECT * FROM attachments
        WHERE ownerType = :ownerType AND ownerId = :ownerId AND kind = :kind AND isDeleted = 0
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    fun observeForOwner(ownerType: String, ownerId: String, kind: String): Flow<List<Attachment>>

    @Query(
        """
        SELECT * FROM attachments
        WHERE ownerType = 'task' AND kind = 'image' AND isDeleted = 0
        ORDER BY ownerId ASC, sortOrder ASC, createdAt ASC
        """
    )
    fun observeActiveTaskImagesOrdered(): Flow<List<Attachment>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId AND kind = :kind")
    suspend fun maxSortOrder(ownerType: String, ownerId: String, kind: String): Int

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun findByIdAnyState(id: String): Attachment?

    /** Includes tombstones so a task graph cannot discard an already-remote child relation. */
    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun getForOwnerAnyState(ownerType: String, ownerId: String): List<Attachment>

    @Query("SELECT * FROM attachments WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Attachment>

    @Query("SELECT EXISTS(SELECT 1 FROM attachments WHERE ownerType = 'task' AND ownerId = :taskId AND pbId IS NOT NULL)")
    suspend fun hasRemoteIdentityForTask(taskId: String): Boolean

    @Query("SELECT * FROM attachments")
    suspend fun getAllOnce(): List<Attachment>

    @Query("UPDATE attachments SET isSynced = 1, syncState = 'SYNCED', lastSyncError = NULL WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = :isDeleted")
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long, isDeleted: Boolean): Int

    @Query("UPDATE attachments SET syncState = :syncState, lastSyncError = :error, isSynced = 0 WHERE id = :id")
    suspend fun markSyncFailed(id: String, syncState: String, error: String?)

    @Query("UPDATE attachments SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(id: String, pbId: String)

    /** Recovers a committed remote create without acknowledging or replacing the local deletion. */
    @Query(
        """
        UPDATE attachments
        SET pbId = :pbId
        WHERE id = :id
            AND updatedAt = :updatedAt
            AND isDeleted = 1
            AND pbId IS NULL
        """
    )
    suspend fun adoptRemoteIdentityForTombstoneIfUnchanged(
        id: String,
        updatedAt: Long,
        pbId: String,
    ): Int

    /** Keeps local file paths intact while moving an attachment to a new server. */
    @Query("UPDATE attachments SET pbId = NULL, remoteFileName = NULL, isSynced = 0, syncState = 'LOCAL_ONLY', lastSyncError = NULL")
    suspend fun resetSyncMetadataForServerSeed()

    @Query("UPDATE attachments SET remoteFileName = :remoteFileName WHERE id = :id")
    suspend fun updateRemoteFileName(id: String, remoteFileName: String?)

    /** A successful multipart response may not acknowledge a newer local edit. */
    @Query("UPDATE attachments SET pbId = COALESCE(:pbId, pbId), remoteFileName = :remoteFileName, isSynced = 1, syncState = 'SYNCED', lastSyncError = NULL WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = 0")
    suspend fun confirmActiveSyncedIfUnchanged(
        id: String,
        updatedAt: Long,
        pbId: String?,
        remoteFileName: String?,
    ): Int

    /** Response-gated tombstone bookkeeping; a concurrent local edit remains unsynced. */
    @Query("UPDATE attachments SET pbId = COALESCE(:pbId, pbId), remoteFileName = NULL, lastSyncError = NULL, isSynced = 1, syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = 1")
    suspend fun confirmTombstoneSyncedIfUnchanged(id: String, updatedAt: Long, pbId: String?): Int

    @Query("UPDATE attachments SET isSynced = 0, syncState = 'LOCAL_ONLY', lastSyncError = NULL WHERE id = :id")
    suspend fun markUnsynced(id: String)

    @Query("UPDATE attachments SET isDeleted = 1, isSynced = 0, syncState = 'LOCAL_ONLY', lastSyncError = NULL, updatedAt = :updatedAt WHERE ownerType = :ownerType AND ownerId = :ownerId AND isDeleted = 0")
    suspend fun tombstoneActiveForOwner(ownerType: String, ownerId: String, updatedAt: Long)

    @Query("SELECT * FROM attachments WHERE isDeleted = 1 AND (localPath != '' OR thumbnailPath != '')")
    suspend fun getTombstonesWithRetainedFilePaths(): List<Attachment>

    @Query(
        """
        UPDATE attachments
        SET localPath = '', thumbnailPath = ''
        WHERE id = :id
            AND isDeleted = 1
            AND updatedAt = :updatedAt
            AND localPath = :expectedLocalPath
            AND thumbnailPath = :expectedThumbnailPath
        """
    )
    suspend fun clearTombstoneFilePathsIfUnchanged(
        id: String,
        updatedAt: Long,
        expectedLocalPath: String,
        expectedThumbnailPath: String,
    ): Int

    @Query(
        """
        DELETE FROM attachments
        WHERE id = :id
            AND isDeleted = 1
            AND updatedAt = :updatedAt
            AND pbId IS NULL
            AND localPath = ''
            AND thumbnailPath = ''
        """
    )
    suspend fun hardDeleteNeverSyncedTombstoneIfUnchanged(
        id: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}

sealed interface AttachmentDownloadInstallResult {
    data class Applied(val replaced: Attachment?) : AttachmentDownloadInstallResult
    data object KeptLocal : AttachmentDownloadInstallResult
}

sealed interface AttachmentTombstoneMergeResult {
    data class Applied(val replaced: Attachment?) : AttachmentTombstoneMergeResult
    data object KeptLocal : AttachmentTombstoneMergeResult
}

private fun Attachment.retainingFileMetadataFrom(local: Attachment?): Attachment =
    local?.let {
        copy(
            localPath = it.localPath,
            thumbnailPath = it.thumbnailPath,
            mimeType = it.mimeType,
            fileName = it.fileName,
            fileSizeBytes = it.fileSizeBytes,
            width = it.width,
            height = it.height,
        )
    } ?: this
