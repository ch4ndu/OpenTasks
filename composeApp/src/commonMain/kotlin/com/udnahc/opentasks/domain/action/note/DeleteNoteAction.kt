package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.NoteRepository
import org.lighthousegames.logging.logging

private val log = logging("DeleteNoteAction")

class DeleteNoteAction(private val repository: NoteRepository) {
    suspend operator fun invoke(note: Note): CommittedMutation<Note> {
        log.d { "Soft-deleting note" }
        return repository.deleteCommitted(note)
    }
}
