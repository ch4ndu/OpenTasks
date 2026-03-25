package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.NoteRepository
import org.lighthousegames.logging.logging

private val log = logging("DeleteNoteAction")

class DeleteNoteAction(private val repository: NoteRepository) {
    suspend operator fun invoke(note: Note) {
        log.d { "Soft-deleting note: ${note.id}" }
        val deleted = note.copy(isDeleted = true, updatedAt = utcNow())
        repository.update(deleted)
    }
}
