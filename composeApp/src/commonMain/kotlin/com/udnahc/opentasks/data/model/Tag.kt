package com.udnahc.opentasks.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Immutable
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val id: String = uuid4(),
    val name: String,
    val color: String? = null,
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
