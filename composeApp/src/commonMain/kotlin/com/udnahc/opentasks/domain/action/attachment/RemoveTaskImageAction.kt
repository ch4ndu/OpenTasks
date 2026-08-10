package com.udnahc.opentasks.domain.action.attachment

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.withSyncState
import com.udnahc.opentasks.data.repository.AttachmentRepository

class RemoveTaskImageAction(
    private val repository: AttachmentRepository,
    private val fileStorage: AttachmentFileStorage,
    private val mutationGate: AccountMutationGate,
) {
    suspend operator fun invoke(attachment: Attachment) = mutationGate.withExclusive {
        val deleted = attachment.copy(
            isDeleted = true,
            updatedAt = localNow(),
        ).withSyncState(AttachmentSyncState.LOCAL_ONLY)
        repository.update(deleted)
        runCatching { fileStorage.delete(attachment.localPath) }
        runCatching { fileStorage.delete(attachment.thumbnailPath) }
    }
}
