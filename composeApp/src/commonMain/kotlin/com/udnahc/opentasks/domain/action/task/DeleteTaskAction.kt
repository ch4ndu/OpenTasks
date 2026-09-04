package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentTombstoneFileCleanup
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.repository.TaskAttachmentFilePaths
import com.udnahc.opentasks.data.repository.TaskGraphDeletionResult
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.data.repository.mapValue
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
    private val tombstoneFileCleanup: AttachmentTombstoneFileCleanup? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(taskId: String): CommittedMutation<TaskWriteResult> = withActionBoundary {
        log.d { "Soft-deleting task: $taskId" }
        val deleted = coordinator.deleteGraph(taskId)
        val result = when (val value = deleted.value) {
            TaskGraphDeletionResult.Missing -> TaskWriteResult.Missing
            is TaskGraphDeletionResult.Deleted -> TaskWriteResult.Updated(value.task)
        }
        val reminderWarning = when (val deletion = deleted.value) {
            is TaskGraphDeletionResult.Deleted -> {
                if (tombstoneFileCleanup != null) {
                    tombstoneFileCleanup.retryAllRetainingRows()
                } else {
                    cleanupNeverUploadedAttachmentFiles(deletion.neverUploadedFilePaths)
                }
                if (rebuildReminderQueueAction != null) {
                    rebuildReminderQueueAction.afterRecordChangeResult(
                        scheduleDirectly = { scheduleTaskRemindersAction(taskId) },
                    )
                } else {
                    try {
                        scheduleTaskRemindersAction(taskId)
                        null
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        error
                    }
                }
            }
            TaskGraphDeletionResult.Missing -> null
        }
        deleted.mapValue { result }.withPostCommitWarning(
            reminderWarning,
            PostCommitWarningPhase.REMINDER_MAINTENANCE,
        )
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
