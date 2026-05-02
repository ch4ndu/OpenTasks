package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.TaskTag
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TaskTagRecord(
    val localId: String = "",
    val taskId: String = "",
    val tagId: String = "",
    val isDeleted: Boolean = false,
    @SerialName("localCreatedAt") val createdAtUtc: Long = 0L,
    @SerialName("localUpdatedAt") val updatedAtUtc: Long = 0L,
) : BaseModel()

fun TaskTag.toTaskTagRecord(): TaskTagRecord = TaskTagRecord(
    localId = taskTagLocalId(taskId, tagId),
    taskId = taskId,
    tagId = tagId,
    isDeleted = isDeleted,
    createdAtUtc = createdAt,
    updatedAtUtc = updatedAt,
)

fun TaskTagRecord.toTaskTag(): TaskTag = TaskTag(
    taskId = taskId,
    tagId = tagId,
    pbId = id,
    isSynced = true,
    isDeleted = isDeleted,
    createdAt = createdAtUtc,
    updatedAt = updatedAtUtc,
)

fun taskTagLocalId(taskId: String, tagId: String): String = "$taskId:$tagId"

