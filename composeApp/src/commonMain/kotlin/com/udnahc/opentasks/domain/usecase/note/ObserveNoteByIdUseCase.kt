package com.udnahc.opentasks.domain.usecase.note

import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.NoteRepository
import kotlinx.coroutines.flow.Flow

class ObserveNoteByIdUseCase(private val repository: NoteRepository) {
    operator fun invoke(id: String): Flow<Note?> = repository.observeNoteById(id)
}
