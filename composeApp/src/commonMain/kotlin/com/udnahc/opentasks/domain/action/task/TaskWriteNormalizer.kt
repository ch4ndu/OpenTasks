package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskStatus

/** Pure persisted-truth normalization shared by creation, imports, and mutations. */
class TaskWriteNormalizer {
    fun create(task: Task, now: Long): Task = normalize(previous = null, proposal = task, now = now)

    fun overlayForm(previous: Task, form: TaskFormData): Task = previous.copy(
        title = form.title,
        content = form.content,
        subtasks = form.subtasks,
        priority = form.priority,
        deadline = form.deadline,
        endDeadline = form.endDeadline,
        isAllDay = form.isAllDay,
        notifyBeforeValue = form.reminderDays,
        notifyBeforeUnit = if (form.reminderDays > 0) com.udnahc.opentasks.data.model.NotifyBeforeUnit.DAYS else com.udnahc.opentasks.data.model.NotifyBeforeUnit.NONE,
        recurrenceType = form.recurrence,
        categoryId = form.categoryId,
        section = form.section,
        status = form.status,
        location = form.location,
        url = form.url,
        organizer = form.organizer,
        eventStatus = form.eventStatus,
        attendees = form.attendees,
        durationReminders = form.durationReminders,
        dateReminders = form.dateReminders,
    )

    fun normalize(
        previous: Task?,
        proposal: Task,
        now: Long,
        preserveProposalAnchor: Boolean = false,
    ): Task {
        val recurrenceCanAnchor = proposal.recurrenceType == RecurrenceType.MONTHLY ||
            proposal.recurrenceType == RecurrenceType.YEARLY
        val deadlineChanged = previous?.deadline != proposal.deadline ||
            previous?.recurrenceType != proposal.recurrenceType
        val anchor = when {
            !recurrenceCanAnchor || proposal.deadline == null -> null
            preserveProposalAnchor -> proposal.recurrenceAnchorDay?.takeIf { it in 1..31 }
                ?: previous?.recurrenceAnchorDay?.takeIf { it in 1..31 }
                ?: extractDay(proposal.deadline)
            previous == null || deadlineChanged -> extractDay(proposal.deadline)
            else -> previous.recurrenceAnchorDay?.takeIf { it in 1..31 }
        }
        val completedAt = when {
            proposal.status != TaskStatus.DONE -> null
            previous?.status == TaskStatus.DONE -> previous.completedAt
            previous == null -> proposal.completedAt ?: now
            else -> now
        }
        return proposal.copy(
            recurrenceAnchorDay = anchor,
            completedAt = completedAt,
            createdAt = previous?.createdAt ?: proposal.createdAt.takeIf { it != 0L } ?: now,
            updatedAt = previous?.let { maxOf(now, it.updatedAt + 1) } ?: now,
            isSynced = false,
        )
    }
}
