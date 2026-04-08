package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
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
)
