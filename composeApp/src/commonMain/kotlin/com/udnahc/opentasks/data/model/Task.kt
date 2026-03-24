package com.udnahc.opentasks.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Immutable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = uuid4(),
    val title: String,
    val content: String,
    val priority: TaskPriority = TaskPriority.NONE,
    val deadline: Long? = null,
    val endDeadline: Long? = null,
    val notifyBeforeValue: Int = 0,
    val notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceInterval: Int = 0,
    val isCompleted: Boolean = false,
    val isUrgent: Boolean = false,
    val isImportant: Boolean = false,
    val categoryId: String = "00000000-0000-0000-0000-000000000001",
    val isAllDay: Boolean = false,
    val sourceExternalId: String? = null,
    val location: String = "",
    val url: String = "",
    val organizer: String = "",
    val eventStatus: String = "",
    val attendees: String = "",
    val durationReminders: String = "",
    val dateReminders: String = "",
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
