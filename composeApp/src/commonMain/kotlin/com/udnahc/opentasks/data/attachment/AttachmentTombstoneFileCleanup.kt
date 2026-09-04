package com.udnahc.opentasks.data.attachment

import com.udnahc.opentasks.data.dao.AttachmentDao
import com.udnahc.opentasks.data.model.Attachment
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

class AttachmentTombstoneFileCleanup(
    private val dao: AttachmentDao,
    private val fileStorage: AttachmentFileStorage,
    private val leaseRecorder: AttachmentFileLeaseRecorder = AttachmentFileLeaseRecorder(dao),
) {
    suspend fun retryAllRetainingRows() {
        val tombstones = try {
            dao.getTombstonesWithRetainedFilePaths()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        for (tombstone in tombstones) {
            currentCoroutineContext().ensureActive()
            retryRetainingRow(tombstone)
        }
        retryRecordedFileCleanup()
    }

    suspend fun retryRecordedFileCleanup() {
        val paths = try {
            leaseRecorder.listedPaths()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
        retryLeasedPaths(paths)
    }

    suspend fun retryLeasedPaths(paths: Iterable<String>) {
        for (path in paths.filter(String::isNotBlank).distinct()) {
            currentCoroutineContext().ensureActive()
            cleanupLeasedPath(path)
        }
    }

    suspend fun retryRetainingRow(tombstone: Attachment) {
        clearPathsAfterFilesAreAbsent(tombstone)
    }

    suspend fun cleanupThenHardDeleteNeverSynced(tombstone: Attachment) {
        if (!tombstone.isDeleted || tombstone.pbId != null) return
        if (!clearPathsAfterFilesAreAbsent(tombstone)) return
        currentCoroutineContext().ensureActive()
        try {
            dao.hardDeleteNeverSyncedTombstoneIfUnchanged(
                id = tombstone.id,
                updatedAt = tombstone.updatedAt,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The tombstone remains available for a later retry.
        }
    }

    private suspend fun clearPathsAfterFilesAreAbsent(tombstone: Attachment): Boolean {
        if (!tombstone.isDeleted) return false
        val localFileAbsent = deleteThenProveAbsent(tombstone.localPath)
        val thumbnailAbsent = deleteThenProveAbsent(tombstone.thumbnailPath)
        if (!localFileAbsent || !thumbnailAbsent) return false
        currentCoroutineContext().ensureActive()
        return try {
            dao.clearTombstoneFilePathsIfUnchanged(
                id = tombstone.id,
                updatedAt = tombstone.updatedAt,
                expectedLocalPath = tombstone.localPath,
                expectedThumbnailPath = tombstone.thumbnailPath,
            ) > 0
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun deleteThenProveAbsent(path: String): Boolean {
        currentCoroutineContext().ensureActive()
        if (path.isBlank()) return true
        try {
            fileStorage.delete(path)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The existence check below is the authoritative cleanup proof.
        }
        currentCoroutineContext().ensureActive()
        return try {
            !fileStorage.exists(path)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun cleanupLeasedPath(path: String) {
        val isReferenced = try {
            leaseRecorder.isReferenced(path)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
        if (!isReferenced && !deleteThenProveAbsent(path)) return
        currentCoroutineContext().ensureActive()
        try {
            leaseRecorder.release(path)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keeping the durable entry makes the cleanup retryable.
        }
    }
}
