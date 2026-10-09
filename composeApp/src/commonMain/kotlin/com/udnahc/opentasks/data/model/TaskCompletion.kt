package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Historical snapshots deliberately have no foreign key to a live task or list. */
@Entity(tableName = "task_completions", indices = [Index("isDeleted", "completedAt"), Index("isSynced"), Index("pbId")])
data class TaskCompletion(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val categoryId: String,
    val categoryName: String = "",
    val completedAt: Long,
    val occurrenceAt: Long? = null,
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
