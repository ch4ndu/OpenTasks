package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(
    observeAllNotes: ObserveAllNotesUseCase,
    private val addNoteAction: AddNoteAction,
    private val updateNoteAction: UpdateNoteAction,
    private val deleteNoteAction: DeleteNoteAction,
) : ViewModel() {

    val notes: StateFlow<List<Note>> = observeAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) { addNoteAction(title, content) }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { updateNoteAction(note) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { deleteNoteAction(note) }
    }
}
