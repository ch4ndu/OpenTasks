package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.domain.action.task.SetTaskShortlistedAction
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveShortlistUseCase
import com.udnahc.opentasks.domain.usecase.task.PlainTaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.task.ShortlistSnapshot
import com.udnahc.opentasks.domain.usecase.task.ShortlistSuggestion
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.task.sortedFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ShortlistRow(
    val task: Task,
    val metaText: String,
    val isOverdue: Boolean,
)

data class ShortlistSuggestionGroup(
    val suggestion: ShortlistSuggestion,
    val rows: List<ShortlistRow>,
)

data class ShortlistUiState(
    val toTackle: List<ShortlistRow> = emptyList(),
    val dueOutsideCount: Int = 0,
)

data class ShortlistChooserState(
    val suggestions: List<ShortlistSuggestionGroup> = emptyList(),
    val allTasks: List<ShortlistRow> = emptyList(),
)

class ShortlistViewModel(
    observeShortlist: ObserveShortlistUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    observeTaskSortOption: ObserveTaskSortOptionUseCase,
    toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val setTaskShortlistedAction: SetTaskShortlistedAction,
    private val taskDueTextProvider: TaskDueTextProvider = PlainTaskDueTextProvider,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) : ViewModel() {

    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        viewModelScope,
    )
    private val taskMutationFailureEvents = TaskMutationFailureEventStore()
    val taskMutationFailureEvent = taskMutationFailureEvents.event
    private val completionHandler = TaskCompletionHandler(
        toggleTaskCompleteAction,
        viewModelScope,
        accountBoundaryExecutor,
        mutationLauncher::launch,
        onMutationBoundaryRejected = {
            taskMutationFailureEvents.publish(TaskMutationFailureReason.BOUNDARY_CHANGED)
        },
        onMutationFailure = {
            taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
        },
        onMutationRejected = {
            taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
        },
    )
    val taskPendingSeriesChoice = completionHandler.taskPendingSeriesChoice

    private val _pendingTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingTaskIds: StateFlow<Set<String>> = _pendingTaskIds

    private val sources = combine(
        observeShortlist(),
        observeAllCategories(),
        observeTaskSortOption(),
    ) { snapshot, categories, sort ->
        RowSources(snapshot, categories.associate { it.id to it.name }, sort)
    }

    val uiState: StateFlow<ShortlistUiState> = sources
        .map { source ->
            ShortlistUiState(
                toTackle = source.rows(source.snapshot.shortlisted),
                dueOutsideCount = source.snapshot.dueOutsideCount,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShortlistUiState())

    val chooserState: StateFlow<ShortlistChooserState> = sources
        .map { source ->
            ShortlistChooserState(
                suggestions = source.snapshot.suggestions.map { (suggestion, tasks) ->
                    ShortlistSuggestionGroup(suggestion, source.rows(tasks))
                },
                allTasks = source.rows(source.snapshot.candidates),
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShortlistChooserState())

    private inner class RowSources(
        val snapshot: ShortlistSnapshot,
        private val categoryNames: Map<String, String>,
        private val sort: TaskSortOption,
    ) {
        suspend fun rows(tasks: List<Task>): List<ShortlistRow> =
            tasks.sortedBy { it.id }.sortedFor(sort).map { task ->
                ShortlistRow(
                    task = task,
                    metaText = listOf(
                        categoryNames[task.categoryId].orEmpty(),
                        taskDueTextProvider.matrixDueText(task),
                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                    isOverdue = task.id in snapshot.overdueIds,
                )
            }
    }

    fun toggleComplete(task: Task) = completionHandler.toggleComplete(
        task.id,
        task.status,
        task.recurrenceType,
        task.deadline,
    )
    fun completeOccurrence() = completionHandler.completeOccurrence()
    fun completeSeries() = completionHandler.completeSeries()
    fun dismissSeriesChoice() = completionHandler.dismissSeriesChoice()

    fun consumeTaskMutationFailureEvent(event: TaskMutationFailureEvent): Boolean =
        taskMutationFailureEvents.consume(event)

    fun setShortlisted(taskId: String, shortlisted: Boolean) {
        if (taskId in _pendingTaskIds.value) return
        _pendingTaskIds.update { it + taskId }
        val release = { _pendingTaskIds.update { it - taskId } }
        mutationLauncher.launch(
            onBoundaryRejected = {
                release()
                taskMutationFailureEvents.publish(TaskMutationFailureReason.BOUNDARY_CHANGED)
            },
            onFailure = {
                taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
            },
        ) {
            try {
                when (setTaskShortlistedAction(taskId, shortlisted)) {
                    is TaskWriteResult.Updated,
                    TaskWriteResult.NoOp,
                    -> Unit
                    is TaskWriteResult.CompletionChoiceRequired,
                    TaskWriteResult.Missing,
                    TaskWriteResult.StaleOccurrence,
                    -> taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
                }
            } finally {
                release()
            }
        }
    }
}
