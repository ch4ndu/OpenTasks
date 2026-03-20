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
        notifyBeforeValue: Int = 0,
        notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int = 0,
        isUrgent: Boolean = false,
        isImportant: Boolean = false,
        listId: Long = 1L,
    ) {
        val now = utcNow()
        repository.insert(
            Task(
                title = title,
                content = content,
                priority = priority,
                deadline = deadline,
                notifyBeforeValue = notifyBeforeValue,
                notifyBeforeUnit = notifyBeforeUnit,
                recurrenceType = recurrenceType,
                recurrenceInterval = recurrenceInterval,
                isUrgent = isUrgent,
                isImportant = isImportant,
                listId = listId,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
