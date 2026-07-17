package com.udnahc.opentasks.data.model

import com.udnahc.opentasks.data.attachment.PickedImage

data class TaskFormData(
    val title: String,
    val content: String,
    val subtasks: String = "",
    val priority: TaskPriority = TaskPriority.NONE,
    val deadline: Long? = null,
    val endDeadline: Long? = null,
    val isAllDay: Boolean = false,
    val reminderDays: Int = 0,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val categoryId: String = AppConstants.DEFAULT_INBOX_ID,
    val section: String? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val location: String = "",
    val url: String = "",
    val organizer: String = "",
    val eventStatus: String = "",
    val attendees: String = "",
    val durationReminders: String = "",
    val dateReminders: String = "",
    val pendingImages: List<PickedImage> = emptyList(),
)
