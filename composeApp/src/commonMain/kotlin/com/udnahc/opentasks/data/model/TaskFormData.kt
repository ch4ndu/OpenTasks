package com.udnahc.opentasks.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class TaskFormData(
    val title: String,
    val content: String,
    val priority: TaskPriority = TaskPriority.NONE,
    val deadline: Long? = null,
    val endDeadline: Long? = null,
    val isAllDay: Boolean = false,
    val reminderDays: Int = 0,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val categoryId: String = "00000000-0000-0000-0000-000000000001",
    val isCompleted: Boolean = false,
    val location: String = "",
    val url: String = "",
    val organizer: String = "",
    val eventStatus: String = "",
    val attendees: String = "",
    val durationReminders: String = "",
    val dateReminders: String = "",
)
