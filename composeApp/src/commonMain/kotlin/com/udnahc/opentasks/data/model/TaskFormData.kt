package com.udnahc.opentasks.data.model

data class TaskFormData(
    val title: String,
    val content: String,
    val priority: TaskPriority = TaskPriority.NONE,
    val deadline: Long? = null,
    val reminderDays: Int = 0,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val categoryId: Long = 1L,
    val isCompleted: Boolean = false,
    val location: String = "",
    val url: String = "",
    val organizer: String = "",
    val eventStatus: String = "",
    val attendees: String = "",
)
