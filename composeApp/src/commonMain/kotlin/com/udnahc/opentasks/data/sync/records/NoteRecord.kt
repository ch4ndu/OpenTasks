package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.Note
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class NoteRecord(
    val localId: String = "",
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    @Serializable(with = com.udnahc.opentasks.data.sync.NoteIdListSerializer::class)
    val tagIds: List<String> = emptyList(),
    @Serializable(with = com.udnahc.opentasks.data.sync.NoteIdListSerializer::class)
    val linkedNoteIds: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    @SerialName("localCreatedAt") val createdAtUtc: Long = 0L,
    @SerialName("localUpdatedAt") val updatedAtUtc: Long = 0L,
) : BaseModel()

fun Note.toNoteRecord(): NoteRecord = NoteRecord(
    localId = id,
    title = title,
    content = content,
    isPinned = isPinned,
    isArchived = isArchived,
    tagIds = tagIds,
    linkedNoteIds = linkedNoteIds,
    isDeleted = isDeleted,
    createdAtUtc = createdAt,
    updatedAtUtc = updatedAt,
)

fun NoteRecord.toNote(): Note = Note(
    id = localId,
    pbId = id,
    title = title,
    content = content,
    isPinned = isPinned,
    isArchived = isArchived,
    tagIds = tagIds,
    linkedNoteIds = linkedNoteIds,
    isDeleted = isDeleted,
    isSynced = true,
    createdAt = createdAtUtc,
    updatedAt = updatedAtUtc,
)
