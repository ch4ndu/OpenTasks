package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("ToggleTaskCompleteAction")

class ToggleTaskCompleteAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(
        taskId: String,
        completeSeries: Boolean = false,
        occurrenceDeadlineLocalMillis: Long? = null,
    ): CommittedMutation<TaskWriteResult> = accountBoundaryExecutor.withForegroundActionBoundary {
        val intent = when {
            completeSeries -> TaskWriteIntent.CompleteSeries(occurrenceDeadlineLocalMillis)
            occurrenceDeadlineLocalMillis != null -> TaskWriteIntent.CompleteOccurrence(occurrenceDeadlineLocalMillis)
            else -> TaskWriteIntent.ToggleCompletion
        }
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
        result.withPostCommitWarning(reminderWarning, PostCommitWarningPhase.REMINDER_MAINTENANCE)
    }
}
