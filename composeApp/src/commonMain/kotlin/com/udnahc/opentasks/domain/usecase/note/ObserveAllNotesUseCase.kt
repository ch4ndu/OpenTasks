package com.udnahc.opentasks.domain.usecase.note

import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.NoteRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllNotesUseCase(private val repository: NoteRepository) {
    operator fun invoke(): Flow<List<Note>> = repository.getAllNotes()
}
