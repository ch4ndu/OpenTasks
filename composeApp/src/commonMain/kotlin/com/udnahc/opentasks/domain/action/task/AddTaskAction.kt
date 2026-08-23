package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("AddTaskAction")

class AddTaskAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(
        title: String,
        content: String,
        subtasks: String = "",
        priority: TaskPriority = TaskPriority.NONE,
        deadline: Long? = null,
        endDeadline: Long? = null,
        isAllDay: Boolean = false,
        notifyBeforeValue: Int = 0,
        notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int = 0,
        isUrgent: Boolean = false,
        isImportant: Boolean = false,
        categoryId: String = AppConstants.DEFAULT_INBOX_ID,
        section: String? = null,
        location: String = "",
        url: String = "",
        organizer: String = "",
        eventStatus: String = "",
        attendees: String = "",
        durationReminders: String = "",
        dateReminders: String = "",
    ): CommittedMutation<Task> = accountBoundaryExecutor.withForegroundActionBoundary {
        log.d { "Adding task" }
        val now = localNow()
        val task = Task(
            title = title,
            content = content,
            subtasks = subtasks,
            priority = priority,
            deadline = deadline,
            endDeadline = endDeadline,
            isAllDay = isAllDay,
            notifyBeforeValue = notifyBeforeValue,
            notifyBeforeUnit = notifyBeforeUnit,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            isUrgent = isUrgent,
            isImportant = isImportant,
            categoryId = categoryId,
            section = section,
            location = location,
            url = url,
            organizer = organizer,
            eventStatus = eventStatus,
            attendees = attendees,
            durationReminders = durationReminders,
            dateReminders = dateReminders,
            createdAt = now,
            updatedAt = now,
        )
        val persisted = coordinator.create(task)
        log.v { "Task created: id=${persisted.value.id}" }
        val reminderWarning = if (rebuildReminderQueueAction != null) {
            rebuildReminderQueueAction.afterRecordChangeResult(
                scheduleDirectly = { scheduleTaskRemindersAction(persisted.value.id) },
            )
        } else {
            try {
                scheduleTaskRemindersAction(persisted.value.id)
                null
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                error
            }
        }
        persisted.withPostCommitWarning(reminderWarning, PostCommitWarningPhase.REMINDER_MAINTENANCE)
    }
}
