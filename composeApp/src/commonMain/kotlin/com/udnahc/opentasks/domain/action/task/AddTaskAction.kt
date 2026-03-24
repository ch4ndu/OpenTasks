package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.repository.TaskRepository

class AddTaskAction(private val repository: TaskRepository) {
    suspend operator fun invoke(
        title: String,
        content: String,
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
        categoryId: String = "00000000-0000-0000-0000-000000000001",
        location: String = "",
        url: String = "",
        organizer: String = "",
        eventStatus: String = "",
        attendees: String = "",
        durationReminders: String = "",
        dateReminders: String = "",
    ): Task {
        val now = utcNow()
        val task = Task(
                title = title,
                content = content,
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
        repository.insert(task)
        return task
    }
}
