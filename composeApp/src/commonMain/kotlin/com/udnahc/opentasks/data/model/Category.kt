package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = uuid4(),
    val name: String,
    val icon: String = "inbox",
    val sortOrder: Int = 0,
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
