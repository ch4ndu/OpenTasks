package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.computeNextDeadlineLocal
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val log = logging("ToggleTaskCompleteAction")

class ToggleTaskCompleteAction(
    private val repository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke(task: Task) {
        val markingComplete = !task.isCompleted
        log.d { "Toggling task ${task.id} complete=$markingComplete" }

        val updated = if (markingComplete && task.shouldAdvanceRecurrence()) {
            advanceRecurrence(task)
        } else {
            task.copy(isCompleted = !task.isCompleted, updatedAt = localNow())
        }

        repository.update(updated)
        scheduleTaskRemindersAction(updated.id)
    }

    private fun advanceRecurrence(task: Task): Task {
        val currentDeadline = task.deadline ?: return task.copy(isCompleted = true, updatedAt = localNow())
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
            isCompleted = false,
            updatedAt = localNow(),
        )
    }

    private fun Task.shouldAdvanceRecurrence(): Boolean =
        recurrenceType != RecurrenceType.NONE && deadline != null
}
