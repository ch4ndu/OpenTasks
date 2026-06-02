package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.NoteRepository
import org.lighthousegames.logging.logging

private val log = logging("AddNoteAction")

class AddNoteAction(private val repository: NoteRepository) {
    suspend operator fun invoke(
        title: String,
        content: String
    ) {
        log.d { "Adding note: '$title'" }
        val now = localNow()
        repository.insert(
            Note(
                title = title,
                content = content,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
