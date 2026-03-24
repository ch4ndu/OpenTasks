package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.Category
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.Serializable

@Serializable
class CategoryRecord(
    val localId: String = "",
    val name: String = "",
    val icon: String = "inbox",
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
    val localCreatedAt: Long = 0L,
) : BaseModel()

fun Category.toCategoryRecord(): CategoryRecord = CategoryRecord(
    localId = id,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    isDeleted = isDeleted,
    localCreatedAt = createdAt,
)

fun CategoryRecord.toCategory(): Category = Category(
    id = localId,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    isDeleted = isDeleted,
    isSynced = true,
    createdAt = localCreatedAt,
)
