package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.domain.action.task.*
import com.udnahc.opentasks.domain.usecase.task.*
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class WeeklyReviewState(val step: ReviewStep = ReviewStep.INBOX, val rows: List<WorkflowTaskRow> = emptyList(), val loading: Boolean = true, val error: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyReviewViewModel(
    tasks: ObserveAllTasksUseCase,
    categories: ObserveAllCategoriesUseCase,
    day: LocalDaySignal,
    due: TaskDueTextProvider,
    toggle: ToggleTaskCompleteAction,
    private val shortlist: SetTaskShortlistedAction,
    boundary: AccountBoundaryExecutor,
    private val updateStatus: UpdateTaskStatusAction,
) : ViewModel() {
    private val step = MutableStateFlow(ReviewStep.INBOX)
    private val launcher = ForegroundMutationLauncher(boundary, viewModelScope)
    private val failures = TaskMutationFailureEventStore()
    val failure = failures.event
    private val pending = MutableStateFlow<Set<String>>(emptySet())
    val pendingIds: StateFlow<Set<String>> = pending
    private fun failed() = failures.publish(TaskMutationFailureReason.OPERATION_FAILED)
    val completion = TaskCompletionHandler(toggle, viewModelScope, boundary, launcher::launch,
        onMutationBoundaryRejected = ::failed, onMutationFailure = { failed() }, onMutationRejected = ::failed)
    private val readGeneration = MutableStateFlow(0L)
    fun retryLoad() { readGeneration.value += 1L }
    val state = readGeneration.flatMapLatest {
        combine(tasks(), categories(), day.dates, step, day.formattingContexts { due.formattingContextKey }) { all, lists, today, current, _ ->
            WeeklyReviewState(current, workflowRows(reviewTasks(all, current, today), lists, due), loading = false)
        }.onStart { emit(WeeklyReviewState(step = step.value)) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(WeeklyReviewState(step = step.value, loading = false, error = true))
            }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyReviewState())

    fun selectStep(value: ReviewStep) { step.value = value }
    fun consumeFailure(event: TaskMutationFailureEvent) = failures.consume(event)
    fun complete(task: Task) {
        if (state.value.loading || state.value.error) return
        val current = pending.value
        if (task.id in current || !pending.compareAndSet(current, current + task.id)) return
        fun finishFailure() { pending.update { it - task.id }; failed() }
        launcher.launch(onBoundaryRejected = ::finishFailure, onFailure = { finishFailure() }) {
            try {
                when (val result = updateStatus(task.id, TaskStatus.DONE).value) {
                    is TaskWriteResult.Updated, TaskWriteResult.NoOp -> Unit
                    is TaskWriteResult.CompletionChoiceRequired -> completion.requestSeriesChoice(task.id, result.expectedOccurrence)
                    else -> failed()
                }
            } finally { pending.update { it - task.id } }
        }
    }
    fun setShortlisted(task: Task) {
        if (state.value.loading || state.value.error) return
        val current = pending.value
        if (task.id in current || !pending.compareAndSet(current, current + task.id)) return
        fun finishFailure() { pending.update { it - task.id }; failed() }
        launcher.launch(onBoundaryRejected = ::finishFailure, onFailure = { finishFailure() }) {
            try {
                when (shortlist(task.id, !task.isShortlisted)) {
                    is TaskWriteResult.Updated, TaskWriteResult.NoOp -> Unit
                    else -> failed()
                }
            } finally { pending.update { it - task.id } }
        }
    }
}
