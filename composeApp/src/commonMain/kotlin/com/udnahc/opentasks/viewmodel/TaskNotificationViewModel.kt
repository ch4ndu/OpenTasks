package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.NotificationDeepLinkEvent
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.notification.ReminderCommandRejectedException
import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderKind
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.domain.action.task.DismissTaskNotificationAction
import com.udnahc.opentasks.domain.action.task.MarkTaskNotificationDoneAction
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging

private val log = logging("TaskNotificationViewModel")

data class TaskNotificationUiState(
    val event: NotificationDeepLinkEvent? = null,
    val task: Task? = null,
    val taskTitle: String = "",
    val notificationTimeText: String = "",
    val dueText: String = "",
    val isBusy: Boolean = false,
    val hasActionError: Boolean = false,
)

enum class TaskNotificationSheetFeedback {
    SAVED_WARNING,
    OBSOLETE,
    TASK_MISSING,
    STALE,
}

data class TaskNotificationSheetDecision(
    val close: Boolean,
    val feedback: TaskNotificationSheetFeedback? = null,
)

/** Purely maps committed task outcomes to the shared sheet's close/message behavior. */
fun taskNotificationSheetDecision(
    mutation: CommittedMutation<TaskWriteResult>,
): TaskNotificationSheetDecision = when (mutation.value) {
    is TaskWriteResult.Updated -> TaskNotificationSheetDecision(
        close = true,
        feedback = mutation.postCommitWarning?.let { TaskNotificationSheetFeedback.SAVED_WARNING },
    )
    TaskWriteResult.NoOp -> TaskNotificationSheetDecision(
        close = true,
        feedback = TaskNotificationSheetFeedback.OBSOLETE,
    )
    TaskWriteResult.Missing -> TaskNotificationSheetDecision(
        close = true,
        feedback = TaskNotificationSheetFeedback.TASK_MISSING,
    )
    TaskWriteResult.StaleOccurrence -> TaskNotificationSheetDecision(
        close = true,
        feedback = TaskNotificationSheetFeedback.STALE,
    )
    is TaskWriteResult.CompletionChoiceRequired -> TaskNotificationSheetDecision(close = false)
}

class TaskNotificationViewModel(
    private val observeTaskById: ObserveTaskByIdUseCase,
    private val markTaskNotificationDoneAction: MarkTaskNotificationDoneAction,
    private val dismissTaskNotificationAction: DismissTaskNotificationAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
) : ViewModel() {

    private val _event = MutableStateFlow<NotificationDeepLinkEvent?>(null)
    private val _isBusy = MutableStateFlow(false)
    private val _hasActionError = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val task = _event
        .flatMapLatest { event ->
            if (event != null) observeTaskById(event.eventId) else flowOf(null)
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<TaskNotificationUiState> =
        combine(_event, task, _isBusy, _hasActionError) { event, task, isBusy, hasActionError ->
            TaskNotificationUiState(
                event = event,
                task = task,
                taskTitle = task?.title.orEmpty(),
                notificationTimeText = event?.notificationAtUtcMillis.formatUtcDateTime(),
                dueText = task.formatDueText(),
                isBusy = isBusy,
                hasActionError = hasActionError,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskNotificationUiState())

    fun setNotificationEvent(event: NotificationDeepLinkEvent) {
        _hasActionError.value = false
        _event.value = event
    }

    fun clearNotificationEvent() {
        _event.value = null
        _hasActionError.value = false
    }

    fun clearActionError() {
        _hasActionError.value = false
    }

    /** Compatibility overload for existing non-host callers. */
    fun markDone(onComplete: () -> Unit) {
        markDone(
            onTaskUpdated = {},
            onResult = { mutation ->
                if (mutation.value is TaskWriteResult.Updated) onComplete()
            },
        )
    }

    /** Runs Mark Done and preserves the committed result for the shared sheet. */
    fun markDone(
        onTaskUpdated: suspend (AccountBoundary) -> Unit,
        onResult: (CommittedMutation<TaskWriteResult>) -> Unit,
    ) {
        val event = _event.value ?: return
        if (_isBusy.value) return
        val semanticKey = event.markDoneSemanticKey(uiState.value.task)
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) {
            _hasActionError.value = true
            return
        }
        runAction {
            val (mutation, boundary) = withContext(ioDispatcher) {
                if (accountBoundaryExecutor == null) {
                    markTaskNotificationDoneAction(
                        taskId = event.eventId,
                        occurrenceDeadlineUtcMillis = event.occurrenceDeadlineUtcMillis,
                        semanticKey = semanticKey,
                        accountId = event.accountId,
                        boundaryEpoch = event.boundaryEpoch,
                    ) to null
                } else {
                    var committedBoundary: AccountBoundary? = null
                    val captured = expectedBoundary ?: throw AccountBoundaryRejectedException()
                    val mutation = accountBoundaryExecutor.withForegroundBoundary(captured) { boundary ->
                        if (event.accountId != boundary.accountId ||
                            event.boundaryEpoch != boundary.boundaryEpoch
                        ) {
                            throw AccountBoundaryRejectedException()
                        }
                        committedBoundary = boundary
                        markTaskNotificationDoneAction(
                            taskId = event.eventId,
                            occurrenceDeadlineUtcMillis = event.occurrenceDeadlineUtcMillis,
                            semanticKey = semanticKey,
                            accountId = boundary.accountId,
                            boundaryEpoch = boundary.boundaryEpoch,
                        )
                    }
                    mutation to committedBoundary
                }
            }
            if (mutation.value is TaskWriteResult.Updated && boundary != null) {
                try {
                    onTaskUpdated(boundary)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    log.e { "Notification widget callback failed after committed task update" }
                }
            }
            try {
                onResult(mutation)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                log.e { "Notification result callback failed after a committed task update" }
            }
        }
    }

    fun gotIt(onResult: () -> Unit) {
        val event = _event.value ?: return
        if (_isBusy.value) return
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) {
            _hasActionError.value = true
            return
        }
        runAction {
            withContext(ioDispatcher) {
                accountBoundaryExecutor.withForegroundActionBoundary(expectedBoundary) {
                    val boundary = expectedBoundary
                    if (boundary != null &&
                        (event.accountId != boundary.accountId ||
                            event.boundaryEpoch != boundary.boundaryEpoch)
                    ) {
                        throw AccountBoundaryRejectedException()
                    }
                    dismissTaskNotificationAction(
                        taskId = event.eventId,
                        semanticKey = event.semanticKey,
                        occurrenceDeadlineUtcMillis = event.occurrenceDeadlineUtcMillis,
                        accountId = boundary?.accountId ?: event.accountId,
                        boundaryEpoch = boundary?.boundaryEpoch ?: event.boundaryEpoch,
                    )
                }
            }
            try {
                onResult()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                log.e { "Notification dismissal callback failed after the reminder was dismissed" }
            }
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            _hasActionError.value = false
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountBoundaryRejectedException) {
                _hasActionError.value = true
                log.w { "Notification action skipped because the foreground account boundary changed" }
            } catch (error: ReminderCommandRejectedException) {
                _hasActionError.value = true
                log.w { "Notification action rejected by its identity contract" }
            } catch (_: Exception) {
                _hasActionError.value = true
                log.e { "Notification action failed before a committed result" }
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun Long?.formatUtcDateTime(): String {
        val utcMillis = this ?: return ""
        val localMillis = utcToLocal(utcMillis)
        return "${dateTimeFormatter.formatShortDate(localMillis)}, ${dateTimeFormatter.formatTime(localMillis)}"
    }

    private fun Task?.formatDueText(): String {
        val task = this ?: return ""
        val deadline = task.deadline ?: return ""
        return if (task.isAllDay) {
            dateTimeFormatter.formatShortDate(deadline)
        } else {
            "${dateTimeFormatter.formatShortDate(deadline)}, ${dateTimeFormatter.formatTime(deadline)}"
        }
    }

    /** Ongoing Mark Done keeps the canonical identity after validating its task context. */
    private fun NotificationDeepLinkEvent.markDoneSemanticKey(task: Task?): String? {
        val identity = semanticKey?.let(ReminderIdentity::fromSemanticKey) ?: return null
        if (identity.kind != ReminderKind.ONGOING) return identity.semanticKey
        if (task?.id != eventId || task.isAllDay != true) return null
        return identity.semanticKey
    }
}
