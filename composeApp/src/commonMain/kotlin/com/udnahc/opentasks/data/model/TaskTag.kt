package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    indices = [
        Index("tagId"),
        Index("isSynced"),
        Index("pbId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskTag(
    val taskId: String,
    val tagId: String,
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
