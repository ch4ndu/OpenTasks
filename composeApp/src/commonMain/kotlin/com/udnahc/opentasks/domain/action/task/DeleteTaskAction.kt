package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.repository.TaskAttachmentFilePaths
import com.udnahc.opentasks.data.repository.TaskGraphDeletionResult
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("DeleteTaskAction")

class DeleteTaskAction(
    private val repository: TaskRepository,
    private val fileStorage: AttachmentFileStorage,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(taskId: String): TaskWriteResult {
        log.d { "Soft-deleting task: $taskId" }
        val deleted = coordinator.deleteGraph(taskId)
        val result = when (deleted) {
            TaskGraphDeletionResult.Missing -> TaskWriteResult.Missing
            is TaskGraphDeletionResult.Deleted -> TaskWriteResult.Updated(deleted.task)
        }
        if (deleted !is TaskGraphDeletionResult.Deleted) return result
        cleanupNeverUploadedAttachmentFiles(deleted.neverUploadedFilePaths)
        rebuildReminderQueueAction?.afterRecordChange { scheduleTaskRemindersAction(taskId) }
            ?: scheduleTaskRemindersAction(taskId)
        return result
    }

    private suspend fun cleanupNeverUploadedAttachmentFiles(files: List<TaskAttachmentFilePaths>) {
        files.forEach { paths ->
            if (paths.localPath.isNotBlank()) runCatching { fileStorage.delete(paths.localPath) }
            if (paths.thumbnailPath.isNotBlank()) runCatching { fileStorage.delete(paths.thumbnailPath) }
        }
    }
}
