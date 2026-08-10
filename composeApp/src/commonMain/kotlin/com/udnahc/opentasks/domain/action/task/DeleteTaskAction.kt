package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.repository.TaskAttachmentFilePaths
import com.udnahc.opentasks.data.repository.TaskGraphDeletionResult
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging
import kotlin.coroutines.cancellation.CancellationException

private val log = logging("DeleteTaskAction")

class DeleteTaskAction(
    private val repository: TaskRepository,
    private val fileStorage: AttachmentFileStorage,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    private val mutationGate: AccountMutationGate,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(taskId: String): TaskWriteResult = withActionBoundary {
        log.d { "Soft-deleting task: $taskId" }
        val deleted = coordinator.deleteGraph(taskId)
        val result = when (deleted) {
            TaskGraphDeletionResult.Missing -> TaskWriteResult.Missing
            is TaskGraphDeletionResult.Deleted -> TaskWriteResult.Updated(deleted.task)
        }
        if (deleted is TaskGraphDeletionResult.Deleted) {
            cleanupNeverUploadedAttachmentFiles(deleted.neverUploadedFilePaths)
            rebuildReminderQueueAction?.afterRecordChange { scheduleTaskRemindersAction(taskId) }
                ?: scheduleTaskRemindersAction(taskId)
        }
        result
    }

    private suspend fun <T> withActionBoundary(block: suspend () -> T): T =
        accountBoundaryExecutor?.withForegroundBoundary { block() }
            ?: mutationGate.withExclusive(block)

    private suspend fun cleanupNeverUploadedAttachmentFiles(files: List<TaskAttachmentFilePaths>) {
        files.forEach { paths ->
            deleteBestEffort(paths.localPath)
            deleteBestEffort(paths.thumbnailPath)
        }
    }

    private suspend fun deleteBestEffort(path: String) {
        if (path.isBlank()) return
        try {
            fileStorage.delete(path)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Attachment cleanup is best effort after the graph tombstone commits.
        }
    }
}
