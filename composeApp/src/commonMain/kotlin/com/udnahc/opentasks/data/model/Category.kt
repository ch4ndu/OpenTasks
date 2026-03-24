package com.udnahc.opentasks.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Immutable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = uuid4(),
    val name: String,
    val icon: String = "inbox",
    val sortOrder: Int = 0,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
)
