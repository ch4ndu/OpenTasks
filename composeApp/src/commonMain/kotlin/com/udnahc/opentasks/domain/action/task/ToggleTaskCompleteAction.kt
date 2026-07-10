package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.computeNextDeadlineLocal
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("ToggleTaskCompleteAction")

class ToggleTaskCompleteAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
) {
    suspend operator fun invoke(
        task: Task,
        completeSeries: Boolean = false,
        occurrenceDeadlineLocalMillis: Long? = null,
    ) {
        if (occurrenceDeadlineLocalMillis != null) {
            if (task.status == TaskStatus.DONE) {
                log.d { "Ignoring stale Mark Done for already completed task ${task.id}" }
                return
            }
            val currentDeadline = task.deadline
            if (currentDeadline != null && currentDeadline > occurrenceDeadlineLocalMillis) {
                log.d {
                    "Ignoring stale Mark Done for task ${task.id}: " +
                            "currentDeadline=$currentDeadline occurrence=$occurrenceDeadlineLocalMillis"
                }
                return
            }
        }

        val markingComplete = task.status != TaskStatus.DONE
        log.d { "Toggling task ${task.id} complete=$markingComplete completeSeries=$completeSeries" }

        val updated = if (markingComplete && task.shouldAdvanceRecurrence()) {
            if (completeSeries) {
                task.copy(
                    status = TaskStatus.DONE,
                    recurrenceType = RecurrenceType.NONE,
                    recurrenceInterval = 0,
                    updatedAt = localNow(),
                )
            } else {
                advanceRecurrence(task, occurrenceDeadlineLocalMillis)
            }
        } else {
            task.copy(
                status = if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE,
                updatedAt = localNow()
            )
        }

        repository.update(updated)
        rebuildReminderQueueAction?.afterRecordChange { scheduleTaskRemindersAction(updated.id) }
            ?: scheduleTaskRemindersAction(updated.id)
    }

    private fun advanceRecurrence(
        task: Task,
        occurrenceDeadlineLocalMillis: Long? = null
    ): Task {
        val currentDeadline =
            task.deadline ?: return task.copy(status = TaskStatus.DONE, updatedAt = localNow())
        val occurrenceDeadline = occurrenceDeadlineLocalMillis ?: currentDeadline
        val nextDeadline = computeNextDeadlineLocal(
            currentDeadlineLocalMillis = occurrenceDeadline,
            recurrenceType = task.recurrenceType.name,
            interval = task.recurrenceInterval,
        )
        val duration = task.endDeadline?.let { it - currentDeadline }
        val nextEndDeadline = duration?.let { nextDeadline + it }
        log.d { "Advancing recurring task ${task.id}: deadline $occurrenceDeadline → $nextDeadline" }
        return task.copy(
            deadline = nextDeadline,
            endDeadline = nextEndDeadline,
            status = TaskStatus.TODO,
            updatedAt = localNow(),
        )
    }

    private fun Task.shouldAdvanceRecurrence(): Boolean =
        recurrenceType != RecurrenceType.NONE && deadline != null
}
