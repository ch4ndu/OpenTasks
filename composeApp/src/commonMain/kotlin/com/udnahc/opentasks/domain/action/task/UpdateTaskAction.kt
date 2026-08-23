package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("UpdateTaskAction")

class UpdateTaskAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(
        taskId: String,
        intent: TaskWriteIntent,
    ): CommittedMutation<TaskWriteResult> =
        accountBoundaryExecutor.withForegroundActionBoundary {
            invokeCommitted(taskId, intent)
        }

    /** Executes the same write under a boundary captured by a platform action. */
    suspend fun invokeWithinBoundary(
        expectedBoundary: AccountBoundary,
        taskId: String,
        intent: TaskWriteIntent,
    ): CommittedMutation<TaskWriteResult> =
        if (accountBoundaryExecutor == null) {
            invokeCommitted(taskId, intent)
        } else {
            accountBoundaryExecutor.withForegroundBoundary(expectedBoundary) {
                invokeCommitted(taskId, intent)
            }
        }

    private suspend fun invokeCommitted(
        taskId: String,
        intent: TaskWriteIntent,
    ): CommittedMutation<TaskWriteResult> {
        log.d { "Updating task: $taskId" }
        val result = coordinator.write(taskId, intent)
        val reminderWarning = if (result.value is TaskWriteResult.Updated) {
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
        } else {
            null
        }
        return result.withPostCommitWarning(reminderWarning, PostCommitWarningPhase.REMINDER_MAINTENANCE)
    }
}
