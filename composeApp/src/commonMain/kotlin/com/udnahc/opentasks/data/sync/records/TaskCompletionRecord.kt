package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.TaskCompletion
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TaskCompletionRecord(
    val localId: String = "",
    val taskId: String = "",
    val title: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val completedAt: Long = 0L,
    val occurrenceAt: Long = 0L,
    val isDeleted: Boolean = false,
    @SerialName("localCreatedAt") val createdAtUtc: Long = 0L,
    @SerialName("localUpdatedAt") val updatedAtUtc: Long = 0L,
) : BaseModel()

fun TaskCompletion.toTaskCompletionRecord() = TaskCompletionRecord(
    localId = id, taskId = taskId, title = title, categoryId = categoryId,
    categoryName = categoryName, completedAt = completedAt, occurrenceAt = occurrenceAt ?: 0L,
    isDeleted = isDeleted, createdAtUtc = createdAt, updatedAtUtc = updatedAt,
)

fun TaskCompletionRecord.toTaskCompletion() = TaskCompletion(
    id = localId, taskId = taskId, title = title, categoryId = categoryId,
    categoryName = categoryName, completedAt = completedAt, occurrenceAt = occurrenceAt.takeUnless { it == 0L },
    pbId = id, isSynced = true, isDeleted = isDeleted, createdAt = createdAtUtc, updatedAt = updatedAtUtc,
)
