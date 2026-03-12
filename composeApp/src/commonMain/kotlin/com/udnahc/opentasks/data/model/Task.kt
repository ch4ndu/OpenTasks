package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val deadline: Long? = null,
    val notifyBeforeValue: Int = 0,
    val notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceInterval: Int = 0,
    val isUrgent: Boolean = false,
    val isImportant: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
