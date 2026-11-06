package com.udnahc.opentasks.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Entity(
    tableName = "notes",
    indices = [
        Index("isDeleted", "updatedAt"),
        Index("isSynced"),
        Index("pbId"),
    ],
)
data class Note(
    @PrimaryKey val id: String = uuid4(),
    val title: String = "",
    val content: String = "",
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'") val tagIds: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "'[]'") val linkedNoteIds: List<String> = emptyList(),
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
