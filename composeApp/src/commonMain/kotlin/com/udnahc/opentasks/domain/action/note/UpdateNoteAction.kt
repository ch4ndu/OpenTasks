package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.NoteRepository
import org.lighthousegames.logging.logging

private val log = logging("UpdateNoteAction")

class UpdateNoteAction(private val repository: NoteRepository) {
    suspend operator fun invoke(note: Note): CommittedMutation<Note> {
        log.d { "Updating note" }
        return repository.updateCommitted(note.copy(updatedAt = localNow()))
    }
}
