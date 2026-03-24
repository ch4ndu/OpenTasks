package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.Note
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.Serializable

@Serializable
class NoteRecord(
    val localId: String = "",
    val title: String = "",
    val content: String = "",
    val isDeleted: Boolean = false,
    val localCreatedAt: Long = 0L,
    val localUpdatedAt: Long = 0L,
) : BaseModel()

fun Note.toNoteRecord(): NoteRecord = NoteRecord(
    localId = id,
    title = title,
    content = content,
    isDeleted = isDeleted,
    localCreatedAt = createdAt,
    localUpdatedAt = updatedAt,
)

fun NoteRecord.toNote(): Note = Note(
    id = localId,
    title = title,
    content = content,
    isDeleted = isDeleted,
    isSynced = true,
    createdAt = localCreatedAt,
    updatedAt = localUpdatedAt,
)
