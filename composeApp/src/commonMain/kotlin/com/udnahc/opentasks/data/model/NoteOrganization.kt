package com.udnahc.opentasks.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteOrganization(
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val tagIds: List<String> = emptyList(),
    val linkedNoteIds: List<String> = emptyList(),
)

fun Note.organization() = NoteOrganization(isPinned, isArchived, tagIds, linkedNoteIds)
fun Note.withOrganization(value: NoteOrganization) = copy(
    isPinned = value.isPinned, isArchived = value.isArchived,
    tagIds = value.tagIds.distinct(), linkedNoteIds = value.linkedNoteIds.distinct().filter { it != id },
)
