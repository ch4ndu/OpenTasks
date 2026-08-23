package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Entity(
    tableName = "tasks",
    indices = [
        Index("isDeleted", "updatedAt"),
        Index("isDeleted", "status", "deadline"),
        Index("isDeleted", "deadline"),
        Index("categoryId"),
        Index("sourceExternalId"),
        Index("isSynced"),
        Index("pbId"),
    ],
)
data class Task(
    @PrimaryKey val id: String = uuid4(),
    val title: String,
    val content: String,
    val subtasks: String = "",
    val priority: TaskPriority = TaskPriority.NONE,
    val deadline: Long? = null,
    val endDeadline: Long? = null,
    val notifyBeforeValue: Int = 0,
    val notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceInterval: Int = 0,
    /** Original local day-of-month for monthly/yearly recurrence advancement. */
    val recurrenceAnchorDay: Int? = null,
    val status: TaskStatus = TaskStatus.TODO,
    /** Local completion time. It is meaningful only while [status] is DONE. */
    val completedAt: Long? = null,
    val isStarred: Boolean = false,
    val section: String? = null,
    val isUrgent: Boolean = false,
    val isImportant: Boolean = false,
    val categoryId: String = AppConstants.DEFAULT_INBOX_ID,
    val isAllDay: Boolean = false,
    val sourceExternalId: String? = null,
    val location: String = "",
    val url: String = "",
    val organizer: String = "",
    val eventStatus: String = "",
    val attendees: String = "",
    val durationReminders: String = "",
    val dateReminders: String = "",
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
