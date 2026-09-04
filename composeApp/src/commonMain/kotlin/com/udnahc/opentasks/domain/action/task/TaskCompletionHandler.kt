package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("TaskCompletionHandler")

typealias TaskMutationLauncher = (
    onBoundaryRejected: () -> Unit,
    onFailure: (Throwable) -> Unit,
    action: suspend () -> Unit,
) -> Unit

data class TaskCompletionChoice(
    val taskId: String,
    val expectedOccurrence: Long,
)

class TaskCompletionHandler(
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val scope: CoroutineScope,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val launchMutationDelegate: TaskMutationLauncher? = null,
    private val onMutationBoundaryRejected: () -> Unit = {},
    private val onMutationFailure: (Throwable) -> Unit = {},
    private val onMutationRejected: () -> Unit = {},
) {
    private val _taskPendingSeriesChoice = MutableStateFlow<TaskCompletionChoice?>(null)
    val taskPendingSeriesChoice: StateFlow<TaskCompletionChoice?> = _taskPendingSeriesChoice.asStateFlow()
    private val accountExecutor = accountBoundaryExecutor
    private val completionInFlight = MutableStateFlow(false)

    fun toggleComplete(
        taskId: String,
        status: TaskStatus,
        recurrenceType: RecurrenceType,
        occurrenceDeadlineLocalMillis: Long?,
    ) {
        if (status != TaskStatus.DONE && recurrenceType != RecurrenceType.NONE && occurrenceDeadlineLocalMillis != null) {
            _taskPendingSeriesChoice.value = TaskCompletionChoice(taskId, occurrenceDeadlineLocalMillis)
        } else {
            launchMutation(
                onBoundaryRejected = onMutationBoundaryRejected,
                onFailure = onMutationFailure,
                action = {
                    val result = toggleTaskCompleteAction(taskId).value
                    handleResult(taskId, result)
                },
            )
        }
    }

    fun completeOccurrence() {
        val pending = _taskPendingSeriesChoice.value ?: return
        if (!completionInFlight.compareAndSet(expect = false, update = true)) return
        launchMutation(
            onBoundaryRejected = {
                completionInFlight.value = false
                onMutationBoundaryRejected()
            },
            onFailure = { error ->
                completionInFlight.value = false
                onMutationFailure(error)
            },
        ) {
            try {
                val result = toggleTaskCompleteAction(
                    pending.taskId,
                    occurrenceDeadlineLocalMillis = pending.expectedOccurrence,
                ).value
                handleResult(pending.taskId, result, completedChoice = pending)
            } finally {
                completionInFlight.value = false
            }
        }
    }

    fun completeSeries() {
        val pending = _taskPendingSeriesChoice.value ?: return
        if (!completionInFlight.compareAndSet(expect = false, update = true)) return
        launchMutation(
            onBoundaryRejected = {
                completionInFlight.value = false
                onMutationBoundaryRejected()
            },
            onFailure = { error ->
                completionInFlight.value = false
                onMutationFailure(error)
            },
        ) {
            try {
                val result = toggleTaskCompleteAction(
                    pending.taskId,
                    completeSeries = true,
                    occurrenceDeadlineLocalMillis = pending.expectedOccurrence,
                ).value
                handleResult(pending.taskId, result, completedChoice = pending)
            } finally {
                completionInFlight.value = false
            }
        }
    }

    fun dismissSeriesChoice() {
        if (completionInFlight.value) return
        _taskPendingSeriesChoice.value = null
    }

    private fun handleResult(
        taskId: String,
        result: TaskWriteResult,
        completedChoice: TaskCompletionChoice? = null,
    ) {
        when (result) {
            is TaskWriteResult.Updated -> {
                if (completedChoice != null) {
                    _taskPendingSeriesChoice.compareAndSet(completedChoice, null)
                }
            }
            is TaskWriteResult.CompletionChoiceRequired -> {
                _taskPendingSeriesChoice.value = TaskCompletionChoice(
                    taskId = taskId,
                    expectedOccurrence = result.expectedOccurrence,
                )
            }
            TaskWriteResult.Missing,
            TaskWriteResult.NoOp,
            TaskWriteResult.StaleOccurrence,
            -> onMutationRejected()
        }
    }

    private fun launchMutation(
        onBoundaryRejected: () -> Unit,
        onFailure: (Throwable) -> Unit,
        action: suspend () -> Unit,
    ) {
        val launcher = launchMutationDelegate
        if (launcher != null) {
            launcher(onBoundaryRejected, onFailure, action)
            return
        }
        val expectedBoundary = accountExecutor?.captureForegroundBoundary()
        if (accountExecutor != null && expectedBoundary == null) {
            onBoundaryRejected()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                if (accountExecutor == null) {
                    action()
                } else {
                    accountExecutor.withForegroundBoundary(
                        expectedBoundary ?: throw AccountBoundaryRejectedException(),
                    ) { action() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountBoundaryRejectedException) {
                log.w { "Task completion skipped because the account boundary changed" }
                onBoundaryRejected()
            } catch (error: Exception) {
                log.e { "Task completion failed" }
                onFailure(error)
            }
        }
    }
}
