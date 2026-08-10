package com.udnahc.opentasks.domain.action.attachment

import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.ATTACHMENT_KIND_IMAGE
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.repository.AttachmentRepository

class AddTaskImageAction(
    private val repository: AttachmentRepository,
    private val fileStorage: AttachmentFileStorage,
    private val mutationGate: AccountMutationGate,
) {
    suspend operator fun invoke(taskId: String, image: PickedImage): Attachment =
        mutationGate.withExclusive {
            addWithinMutation(taskId, image)
        }

    private suspend fun addWithinMutation(taskId: String, image: PickedImage): Attachment {
        val stored = fileStorage.storePickedImage(image)
        try {
            require(stored.fileSizeBytes <= AttachmentFilePolicy.MAX_UPLOAD_BYTES) {
                "Attachment exceeds maximum upload size"
            }
        } catch (e: Exception) {
            fileStorage.delete(stored.localPath)
            fileStorage.delete(stored.thumbnailPath)
            throw e
        }
        val now = localNow()
        val attachment = Attachment(
            ownerType = ATTACHMENT_OWNER_TASK,
            ownerId = taskId,
            kind = ATTACHMENT_KIND_IMAGE,
            localPath = stored.localPath,
            thumbnailPath = stored.thumbnailPath,
            mimeType = stored.mimeType,
            fileName = stored.fileName,
            fileSizeBytes = stored.fileSizeBytes,
            width = stored.width,
            height = stored.height,
            sortOrder = repository.nextSortOrder(ATTACHMENT_OWNER_TASK, taskId, ATTACHMENT_KIND_IMAGE),
            syncState = AttachmentSyncState.LOCAL_ONLY,
            isSynced = false,
            createdAt = now,
            updatedAt = now,
        )
        try {
            repository.insert(attachment)
            return attachment
        } catch (e: Exception) {
            fileStorage.delete(stored.localPath)
            fileStorage.delete(stored.thumbnailPath)
            throw e
        }
    }
}
