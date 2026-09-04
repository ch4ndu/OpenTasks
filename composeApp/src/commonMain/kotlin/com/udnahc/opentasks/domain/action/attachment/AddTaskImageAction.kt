package com.udnahc.opentasks.domain.action.attachment

import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentTombstoneFileCleanup
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.attachment.StoredAttachmentFile
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.uuid4
import com.udnahc.opentasks.data.model.ATTACHMENT_KIND_IMAGE
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.repository.AttachmentRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("AddTaskImageAction")

class AddTaskImageAction(
    private val repository: AttachmentRepository,
    private val fileStorage: AttachmentFileStorage,
    private val mutationGate: AccountMutationGate,
    private val tombstoneFileCleanup: AttachmentTombstoneFileCleanup? = null,
) {
    suspend operator fun invoke(taskId: String, image: PickedImage): Attachment =
        mutationGate.withExclusive {
            addWithinMutation(taskId, image)
        }

    private suspend fun addWithinMutation(taskId: String, image: PickedImage): Attachment {
        val attachmentId = uuid4()
        var stored: StoredAttachmentFile? = null
        return try {
            val storedFile = fileStorage.storePickedImage(image)
            stored = storedFile
            require(storedFile.fileSizeBytes <= AttachmentFilePolicy.MAX_UPLOAD_BYTES) {
                "Attachment exceeds maximum upload size"
            }
            val now = localNow()
            val attachment = Attachment(
                id = attachmentId,
                ownerType = ATTACHMENT_OWNER_TASK,
                ownerId = taskId,
                kind = ATTACHMENT_KIND_IMAGE,
                localPath = storedFile.localPath,
                thumbnailPath = storedFile.thumbnailPath,
                mimeType = storedFile.mimeType,
                fileName = storedFile.fileName,
                fileSizeBytes = storedFile.fileSizeBytes,
                width = storedFile.width,
                height = storedFile.height,
                sortOrder = repository.nextSortOrder(ATTACHMENT_OWNER_TASK, taskId, ATTACHMENT_KIND_IMAGE),
                syncState = AttachmentSyncState.LOCAL_ONLY,
                isSynced = false,
                createdAt = now,
                updatedAt = now,
            )
            repository.insert(attachment)
            tombstoneFileCleanup?.retryLeasedPaths(storedFile.paths())
            attachment
        } catch (error: CancellationException) {
            val storedFile = stored ?: throw error
            resolveFailedAdd(error, attachmentId, taskId, storedFile)
        } catch (error: Exception) {
            val storedFile = stored ?: throw error
            resolveFailedAdd(error, attachmentId, taskId, storedFile)
        }
    }

    private suspend fun resolveFailedAdd(
        original: Exception,
        attachmentId: String,
        taskId: String,
        stored: StoredAttachmentFile,
    ): Attachment {
        val commitState = withContext(NonCancellable) {
            val persisted = try {
                repository.getByIdAnyState(attachmentId)
            } catch (_: CancellationException) {
                return@withContext AddCommitState.Unknown
            } catch (_: Exception) {
                return@withContext AddCommitState.Unknown
            }
            when {
                persisted == null -> {
                    cleanupUncommittedFiles(stored)
                    AddCommitState.Uncommitted
                }
                persisted.id == attachmentId &&
                    persisted.ownerType == ATTACHMENT_OWNER_TASK &&
                    persisted.ownerId == taskId &&
                    persisted.kind == ATTACHMENT_KIND_IMAGE &&
                    persisted.localPath == stored.localPath &&
                    persisted.thumbnailPath == stored.thumbnailPath -> {
                    tombstoneFileCleanup?.retryLeasedPaths(stored.paths())
                    AddCommitState.Committed(persisted)
                }
                else -> AddCommitState.Unknown
            }
        }
        if (original is CancellationException) throw original
        if (commitState is AddCommitState.Committed) {
            log.w { "Attachment image insert completed with a post-commit failure" }
            return commitState.attachment
        }
        throw original
    }

    private suspend fun cleanupUncommittedFiles(
        stored: StoredAttachmentFile,
    ) {
        tombstoneFileCleanup?.let {
            it.retryLeasedPaths(stored.paths())
            return
        }
        try {
            fileStorage.delete(stored.localPath)
        } catch (_: CancellationException) {
            // The original add failure remains authoritative.
        } catch (_: Exception) {
            // The original add failure remains authoritative.
        }
        try {
            fileStorage.delete(stored.thumbnailPath)
        } catch (_: CancellationException) {
            // The original add failure remains authoritative.
        } catch (_: Exception) {
            // The original add failure remains authoritative.
        }
    }

    private sealed interface AddCommitState {
        data class Committed(val attachment: Attachment) : AddCommitState
        data object Uncommitted : AddCommitState
        data object Unknown : AddCommitState
    }

    private fun StoredAttachmentFile.paths(): List<String> = listOf(localPath, thumbnailPath)
}
