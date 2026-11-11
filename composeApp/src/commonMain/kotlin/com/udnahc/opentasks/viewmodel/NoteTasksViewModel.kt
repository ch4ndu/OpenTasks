package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class NoteTasksState(val loading: Boolean = true, val note: Note? = null, val rows: List<WorkflowTaskRow> = emptyList(), val error: Boolean = false)
@OptIn(ExperimentalCoroutinesApi::class)
class NoteTasksViewModel(noteId: String, note: ObserveNoteByIdUseCase, tasks: ObserveAllTasksUseCase, categories: ObserveAllCategoriesUseCase, due: TaskDueTextProvider, day: LocalDaySignal) : ViewModel() {
    private val readGeneration = MutableStateFlow(0L)
    fun retryLoad() { readGeneration.value += 1L }
    val state = readGeneration.flatMapLatest {
        combine(note(noteId), tasks(), categories(), day.formattingContexts { due.formattingContextKey }) { source, all, lists, _ ->
            NoteTasksState(false, source, workflowRows(all.filter { it.sourceNoteId == noteId }.sortedByDescending { it.updatedAt }, lists, due))
        }.onStart { emit(NoteTasksState()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(NoteTasksState(loading = false, error = true))
            }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoteTasksState())
}
