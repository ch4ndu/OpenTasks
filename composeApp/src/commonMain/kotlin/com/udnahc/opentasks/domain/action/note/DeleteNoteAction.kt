package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.NoteRepository

class DeleteNoteAction(private val repository: NoteRepository) {
    suspend operator fun invoke(note: Note) {
        repository.delete(note)
    }
}
