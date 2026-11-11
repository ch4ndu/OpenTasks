package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.domain.action.task.*
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.task.*
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class BulkTasksState(val rows: List<WorkflowTaskRow> = emptyList(), val categories: List<Category> = emptyList(), val selected: Set<String> = emptySet(), val loading: Boolean = true, val error: Boolean = false)
@OptIn(ExperimentalCoroutinesApi::class)
class BulkTasksViewModel(
    filter: TaskListFilter?,
    tasks: ObserveAllTasksUseCase,
    categories: ObserveAllCategoriesUseCase,
    day: LocalDaySignal,
    due: TaskDueTextProvider,
    private val update: BulkUpdateTasksAction,
    boundary: AccountBoundaryExecutor,
) : ViewModel() {
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val launcher = ForegroundMutationLauncher(boundary, viewModelScope)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _result = MutableStateFlow<BulkTaskResult?>(null)
    val result: StateFlow<BulkTaskResult?> = _result
    private val readGeneration = MutableStateFlow(0L)
    fun retryLoad() { readGeneration.value += 1L }
    private data class Projection(val state: BulkTasksState, val visibleIds: Set<String> = emptySet())
    private val projection = readGeneration.flatMapLatest {
        combine(tasks(), categories(), day.dates, day.formattingContexts { due.formattingContextKey }) { all, lists, today, _ ->
            val visible = all.filter { it.status != TaskStatus.DONE && (filter == null || filter.includes(it, TaskFilterDay(today))) }.sortedFor(TaskSortOption.BY_TITLE)
            Projection(BulkTasksState(workflowRows(visible, lists, due), lists, loading = false), visible.mapTo(mutableSetOf()) { it.id })
        }.onStart { emit(Projection(BulkTasksState())) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(Projection(BulkTasksState(loading = false, error = true)))
            }
    }.flowOn(Dispatchers.Default)
    val state = combine(projection, selection) { projected, selected ->
        projected.state.copy(selected = selected.intersect(projected.visibleIds))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BulkTasksState())
    private fun canMutate() = !_busy.value && !state.value.loading && !state.value.error

    fun toggle(id: String) { if (canMutate()) selection.update { if (id in it) it - id else it + id } }
    fun selectAll() { if (canMutate()) selection.value = state.value.rows.mapTo(mutableSetOf()) { it.task.id } }
    fun clear() { if (canMutate()) selection.value = emptySet() }
    fun apply(change: BulkTaskChange) {
        if (!canMutate()) return
        val ids = state.value.selected
        if (ids.isEmpty() || !_busy.compareAndSet(false, true)) return
        _result.value = null
        fun failed() { _result.value = BulkTaskResult(emptySet(), ids); _busy.value = false }
        launcher.launch(onBoundaryRejected = ::failed, onFailure = { failed() }) {
            try {
                val result = update(ids, change)
                selection.update { it - result.succeeded }
                _result.value = result
            } finally { _busy.value = false }
        }
    }
}
