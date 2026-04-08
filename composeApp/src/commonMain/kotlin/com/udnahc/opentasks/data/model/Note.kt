package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = uuid4(),
    val title: String = "",
    val content: String = "",
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
