package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class NoteTasksState(val loading: Boolean = true, val note: Note? = null, val rows: List<WorkflowTaskRow> = emptyList())
class NoteTasksViewModel(noteId: String, note: ObserveNoteByIdUseCase, tasks: ObserveAllTasksUseCase, categories: ObserveAllCategoriesUseCase, due: TaskDueTextProvider) : ViewModel() {
    val state = combine(note(noteId), tasks(), categories()) { source, all, lists ->
        NoteTasksState(false, source, workflowRows(all.filter { it.sourceNoteId == noteId }.sortedByDescending { it.updatedAt }, lists, due))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoteTasksState())
}
