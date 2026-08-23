package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.ReminderCommand
import com.udnahc.opentasks.data.notification.ReminderCommandRejectedException
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.notification.ReminderCommandValidation
import com.udnahc.opentasks.data.notification.validateReminderCommand
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.extensions.utcToLocal
import org.lighthousegames.logging.logging

private val taskNotificationLog = logging("TaskNotificationActions")

class MarkTaskNotificationDoneAction(
    private val updateTaskAction: UpdateTaskAction,
) {
    suspend operator fun invoke(
        taskId: String,
        occurrenceDeadlineUtcMillis: Long?,
        semanticKey: String?,
        accountId: String?,
        boundaryEpoch: Long,
        expectedBoundary: AccountBoundary? = null,
    ): CommittedMutation<TaskWriteResult> {
        val validation = validateReminderCommand(
            command = ReminderCommand.MARK_DONE,
            semanticKey = semanticKey,
            eventId = taskId,
            occurrenceUtcMillis = occurrenceDeadlineUtcMillis,
            accountId = accountId,
            boundaryEpoch = boundaryEpoch,
        )
        if (validation !is ReminderCommandValidation.Accepted) {
            throw ReminderCommandRejectedException()
        }
        val intent = TaskWriteIntent.NotificationMarkDone(occurrenceDeadlineUtcMillis?.let(::utcToLocal))
        return expectedBoundary?.let { boundary ->
            updateTaskAction.invokeWithinBoundary(boundary, taskId, intent)
        } ?: updateTaskAction(taskId, intent)
    }
}

class DismissTaskNotificationAction(
    private val taskRepository: TaskRepository,
    private val allDayNotificationDismissalStore: AllDayNotificationDismissalStore,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(
        taskId: String,
        semanticKey: String?,
        occurrenceDeadlineUtcMillis: Long?,
        accountId: String?,
        boundaryEpoch: Long,
    ) {
        val validation = validateReminderCommand(
            command = ReminderCommand.SHEET_DISMISS,
            semanticKey = semanticKey,
            eventId = taskId,
            occurrenceUtcMillis = occurrenceDeadlineUtcMillis,
            accountId = accountId,
            boundaryEpoch = boundaryEpoch,
        )
        if (validation !is ReminderCommandValidation.Accepted) {
            throw ReminderCommandRejectedException()
        }
        val task = taskRepository.getTaskById(taskId)
        if (task?.isAllDay == true) {
            allDayNotificationDismissalStore.dismissToday(taskId)
        }
        reminderScheduler.cancel(semanticKey ?: throw ReminderCommandRejectedException())
    }
}
