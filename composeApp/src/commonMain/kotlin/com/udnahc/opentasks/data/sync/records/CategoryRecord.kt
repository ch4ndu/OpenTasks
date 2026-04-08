package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.Category
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CategoryRecord(
    val localId: String = "",
    val name: String = "",
    val icon: String = "inbox",
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
    @SerialName("localCreatedAt") val createdAtUtc: Long = 0L,
    @SerialName("localUpdatedAt") val updatedAtUtc: Long = 0L,
) : BaseModel()

fun Category.toCategoryRecord(): CategoryRecord = CategoryRecord(
    localId = id,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    isDeleted = isDeleted,
    createdAtUtc = createdAt,
    updatedAtUtc = updatedAt,
)

fun CategoryRecord.toCategory(): Category = Category(
    id = localId,
    pbId = id,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    isDeleted = isDeleted,
    isSynced = true,
    createdAt = createdAtUtc,
    updatedAt = updatedAtUtc,
)
