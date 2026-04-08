package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.Tag
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TagRecord(
    val localId: String = "",
    val name: String = "",
    val color: String? = null,
    val isDeleted: Boolean = false,
    @SerialName("localCreatedAt") val createdAtUtc: Long = 0L,
    @SerialName("localUpdatedAt") val updatedAtUtc: Long = 0L,
) : BaseModel()

fun TagRecord.toTag(): Tag = Tag(
    id = localId,
    name = name,
    color = color,
    pbId = id,
    isSynced = true,
    isDeleted = isDeleted,
    createdAt = createdAtUtc,
    updatedAt = updatedAtUtc,
)

fun Tag.toTagRecord(): TagRecord = TagRecord(
    localId = id,
    name = name,
    color = color,
    isDeleted = isDeleted,
    createdAtUtc = createdAt,
    updatedAtUtc = updatedAt,
)
