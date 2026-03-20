package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.NoteRepository

class UpdateNoteAction(private val repository: NoteRepository) {
    suspend operator fun invoke(note: Note) {
        repository.update(note.copy(updatedAt = utcNow()))
    }
}
