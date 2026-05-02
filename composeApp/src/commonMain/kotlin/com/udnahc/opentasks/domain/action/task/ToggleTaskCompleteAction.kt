package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.computeNextDeadlineLocal
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val log = logging("ToggleTaskCompleteAction")

class ToggleTaskCompleteAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke(task: Task, completeSeries: Boolean = false) {
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
                advanceRecurrence(task)
            }
        } else {
            task.copy(status = if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE, updatedAt = localNow())
        }

        repository.update(updated)
        scheduleTaskRemindersAction(updated.id)
    }

    private fun advanceRecurrence(task: Task): Task {
        val currentDeadline = task.deadline ?: return task.copy(status = TaskStatus.DONE, updatedAt = localNow())
        val nextDeadline = computeNextDeadlineLocal(
            currentDeadlineLocalMillis = currentDeadline,
            recurrenceType = task.recurrenceType.name,
            interval = task.recurrenceInterval,
        )
        val delta = nextDeadline - currentDeadline
        val nextEndDeadline = task.endDeadline?.let { it + delta }
        log.d { "Advancing recurring task ${task.id}: deadline $currentDeadline → $nextDeadline" }
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
