package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.action.task.*
import com.udnahc.opentasks.domain.usecase.task.*
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class WeeklyReviewState(val step: ReviewStep = ReviewStep.INBOX, val rows: List<WorkflowTaskRow> = emptyList())

class WeeklyReviewViewModel(
    tasks: ObserveAllTasksUseCase,
    categories: ObserveAllCategoriesUseCase,
    day: LocalDaySignal,
    due: TaskDueTextProvider,
    toggle: ToggleTaskCompleteAction,
    private val shortlist: SetTaskShortlistedAction,
    boundary: AccountBoundaryExecutor,
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
    val state = combine(tasks(), categories(), day.dates, step) { all, lists, today, current ->
        WeeklyReviewState(current, workflowRows(reviewTasks(all, current, today), lists, due))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyReviewState())

    fun selectStep(value: ReviewStep) { step.value = value }
    fun consumeFailure(event: TaskMutationFailureEvent) = failures.consume(event)
    fun complete(task: Task) = completion.toggleComplete(task.id, task.status, task.recurrenceType, task.deadline)
    fun setShortlisted(task: Task) {
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
