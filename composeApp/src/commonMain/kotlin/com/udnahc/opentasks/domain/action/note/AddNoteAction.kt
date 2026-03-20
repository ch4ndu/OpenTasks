package com.udnahc.opentasks.domain.action.note

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.NoteRepository

class AddNoteAction(private val repository: NoteRepository) {
    suspend operator fun invoke(title: String, content: String) {
        val now = utcNow()
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
