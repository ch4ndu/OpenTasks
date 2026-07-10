package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.AttachmentRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("DeleteTaskAction")

class DeleteTaskAction(
    private val repository: TaskRepository,
    private val attachmentRepository: AttachmentRepository,
    private val fileStorage: AttachmentFileStorage,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
) {
    suspend operator fun invoke(task: Task) {
        log.d { "Soft-deleting task: ${task.id}" }
        val deleted = task.copy(isDeleted = true, updatedAt = localNow())
        cleanupTaskAttachmentFilesAndNeverUploadedRows(deleted.id)
        attachmentRepository.tombstoneActiveForOwner(ATTACHMENT_OWNER_TASK, deleted.id)
        repository.update(deleted)
        rebuildReminderQueueAction?.afterRecordChange { scheduleTaskRemindersAction(deleted.id) }
            ?: scheduleTaskRemindersAction(deleted.id)
    }

    private suspend fun cleanupTaskAttachmentFilesAndNeverUploadedRows(taskId: String) {
        val attachments = attachmentRepository.getActiveForOwnerAnyState(ATTACHMENT_OWNER_TASK, taskId)
        attachments.forEach { attachment ->
            runCatching { fileStorage.delete(attachment.localPath) }
            runCatching { fileStorage.delete(attachment.thumbnailPath) }
            if (attachment.pbId == null) {
                attachmentRepository.hardDelete(attachment)
            }
        }
    }
}
