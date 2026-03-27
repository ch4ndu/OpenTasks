package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.computeNextDeadlineUtc
import com.udnahc.opentasks.data.extensions.utcNow
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
            task.copy(isCompleted = !task.isCompleted, updatedAt = utcNow())
        }

        repository.update(updated)
        scheduleTaskRemindersAction(updated)
    }

    private fun advanceRecurrence(task: Task): Task {
        val currentDeadline = task.deadline ?: return task.copy(isCompleted = true, updatedAt = utcNow())
        val nextDeadline = computeNextDeadlineUtc(
            currentDeadlineUtcMillis = currentDeadline,
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
            updatedAt = utcNow(),
        )
    }

    private fun Task.shouldAdvanceRecurrence(): Boolean =
        recurrenceType != RecurrenceType.NONE && deadline != null
}
