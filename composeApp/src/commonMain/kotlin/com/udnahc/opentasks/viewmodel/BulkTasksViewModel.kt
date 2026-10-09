package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.domain.action.task.*
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.task.*
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class BulkTasksState(val rows: List<WorkflowTaskRow> = emptyList(), val categories: List<Category> = emptyList(), val selected: Set<String> = emptySet())
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
    val state = combine(tasks(), categories(), day.dates, selection) { all, lists, today, selected ->
        val visible = all.filter { it.status != TaskStatus.DONE && (filter == null || filter.includes(it, TaskFilterDay(today))) }.sortedFor(TaskSortOption.BY_TITLE)
        BulkTasksState(workflowRows(visible, lists, due), lists, selected.intersect(visible.mapTo(mutableSetOf()) { it.id }))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BulkTasksState())

    fun toggle(id: String) { if (!_busy.value) selection.update { if (id in it) it - id else it + id } }
    fun selectAll() { if (!_busy.value) selection.value = state.value.rows.mapTo(mutableSetOf()) { it.task.id } }
    fun clear() { if (!_busy.value) selection.value = emptySet() }
    fun apply(change: BulkTaskChange) {
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
