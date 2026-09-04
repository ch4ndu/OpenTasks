package com.udnahc.opentasks.domain.action.attachment

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentTombstoneFileCleanup
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.withSyncState
import com.udnahc.opentasks.data.repository.AttachmentRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

class RemoveTaskImageAction(
    private val repository: AttachmentRepository,
    private val fileStorage: AttachmentFileStorage,
    private val mutationGate: AccountMutationGate,
    private val tombstoneFileCleanup: AttachmentTombstoneFileCleanup? = null,
) {
    suspend operator fun invoke(attachment: Attachment) = mutationGate.withExclusive {
        val deleted = attachment.copy(
            isDeleted = true,
            updatedAt = localNow(),
        ).withSyncState(AttachmentSyncState.LOCAL_ONLY)
        repository.update(deleted)
        tombstoneFileCleanup?.let {
            it.retryAllRetainingRows()
            return@withExclusive
        }
        currentCoroutineContext().ensureActive()
        try {
            fileStorage.delete(attachment.localPath)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // File cleanup remains best effort after the tombstone is durable.
        }
        currentCoroutineContext().ensureActive()
        try {
            fileStorage.delete(attachment.thumbnailPath)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // File cleanup remains best effort after the tombstone is durable.
        }
    }
}
