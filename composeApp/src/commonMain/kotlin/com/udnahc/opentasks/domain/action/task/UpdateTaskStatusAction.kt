package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction

class UpdateTaskStatusAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(
        taskId: String,
        newStatus: TaskStatus
    ): CommittedMutation<TaskWriteResult> = accountBoundaryExecutor.withForegroundActionBoundary {
        val result = coordinator.write(taskId, TaskWriteIntent.SetStatus(newStatus))
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
        result.withPostCommitWarning(reminderWarning, PostCommitWarningPhase.REMINDER_MAINTENANCE)
    }
}
