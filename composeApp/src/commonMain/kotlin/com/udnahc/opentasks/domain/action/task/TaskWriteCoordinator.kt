package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.computeNextDeadlineLocal
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskMutationResult
import com.udnahc.opentasks.data.repository.TaskGraphDeletionResult
import com.udnahc.opentasks.data.repository.TaskRepository

sealed interface TaskWriteResult {
    data class Updated(val task: Task) : TaskWriteResult
    data class CompletionChoiceRequired(val expectedOccurrence: Long) : TaskWriteResult
    data object Missing : TaskWriteResult
    data object StaleOccurrence : TaskWriteResult
    data object NoOp : TaskWriteResult
}

/**
 * Converts narrow intents into one persisted-truth transaction. No caller-owned
 * task snapshot is accepted as write authority.
 */
class TaskWriteCoordinator(
    private val repository: TaskRepository,
    private val normalizer: TaskWriteNormalizer = TaskWriteNormalizer(),
) {
    suspend fun create(task: Task): Task {
        val normalized = normalizer.create(task, localNow())
        repository.insert(normalized)
        return normalized
    }

    suspend fun deleteGraph(id: String): TaskGraphDeletionResult = repository.deleteGraph(id)

    suspend fun write(id: String, intent: TaskWriteIntent): TaskWriteResult {
        if (intent == TaskWriteIntent.Delete) {
            return when (val deleted = deleteGraph(id)) {
                TaskGraphDeletionResult.Missing -> TaskWriteResult.Missing
                is TaskGraphDeletionResult.Deleted -> TaskWriteResult.Updated(deleted.task)
            }
        }
        val now = localNow()
        var outcome: TaskWriteResult = TaskWriteResult.NoOp
        val stored = repository.mutateExisting(id) { previous ->
            val proposal = when (intent) {
                is TaskWriteIntent.FormUpdate -> {
                    val form = normalizer.overlayForm(previous, intent.formData)
                    if (requiresCompletionChoice(previous, form.status)) {
                        outcome = TaskWriteResult.CompletionChoiceRequired(previous.deadline ?: return@mutateExisting null)
                        null
                    } else form
                }
                is TaskWriteIntent.ApplyFormAndComplete -> {
                    if (!isCurrentRecurringOccurrence(previous, intent.expectedOccurrence)) {
                        outcome = TaskWriteResult.StaleOccurrence
                        null
                    } else completeForm(previous, intent, now)
                }
                is TaskWriteIntent.SetStatus -> {
                    if (requiresCompletionChoice(previous, intent.status)) {
                        outcome = TaskWriteResult.CompletionChoiceRequired(previous.deadline ?: return@mutateExisting null)
                        null
                    } else previous.copy(status = intent.status)
                }
                TaskWriteIntent.ToggleStar -> previous.copy(isStarred = !previous.isStarred)
                TaskWriteIntent.ToggleCompletion -> {
                    if (previous.status == TaskStatus.DONE) previous.copy(status = TaskStatus.TODO)
                    else if (previous.recurrenceType != RecurrenceType.NONE && previous.deadline != null) {
                        outcome = TaskWriteResult.CompletionChoiceRequired(previous.deadline)
                        null
                    } else previous.copy(status = TaskStatus.DONE)
                }
                is TaskWriteIntent.CompleteOccurrence -> {
                    if (!isCurrentOccurrence(previous, intent.expectedOccurrence)) {
                        outcome = TaskWriteResult.StaleOccurrence
                        null
                    } else completeOccurrence(previous)
                }
                is TaskWriteIntent.CompleteSeries -> {
                    if (intent.expectedOccurrence != null && !isCurrentOccurrence(previous, intent.expectedOccurrence)) {
                        outcome = TaskWriteResult.StaleOccurrence
                        null
                    } else completeSeries(previous)
                }
                is TaskWriteIntent.NotificationMarkDone -> {
                    if (previous.status == TaskStatus.DONE) {
                        outcome = TaskWriteResult.NoOp
                        null
                    } else if (intent.expectedOccurrence != null && !isCurrentOccurrence(previous, intent.expectedOccurrence)) {
                        outcome = TaskWriteResult.StaleOccurrence
                        null
                    } else if (previous.recurrenceType != RecurrenceType.NONE && previous.deadline != null) {
                        completeOccurrence(previous)
                    } else previous.copy(status = TaskStatus.DONE)
                }
                TaskWriteIntent.Delete -> error("Delete must use the graph deletion boundary")
            }
            proposal?.let {
                normalizer.normalize(
                    previous = previous,
                    proposal = it,
                    now = now,
                    preserveProposalAnchor = intent is TaskWriteIntent.ApplyFormAndComplete ||
                        intent is TaskWriteIntent.CompleteOccurrence ||
                        intent is TaskWriteIntent.NotificationMarkDone,
                )
            }
        }
        return when (stored) {
            TaskMutationResult.Missing -> TaskWriteResult.Missing
            is TaskMutationResult.Existing -> stored.next?.let(TaskWriteResult::Updated) ?: outcome
        }
    }

    private fun requiresCompletionChoice(previous: Task, requestedStatus: TaskStatus): Boolean =
        previous.status != TaskStatus.DONE && requestedStatus == TaskStatus.DONE &&
            previous.recurrenceType != RecurrenceType.NONE && previous.deadline != null

    private fun isCurrentOccurrence(task: Task, expected: Long): Boolean =
        task.status != TaskStatus.DONE && task.deadline == expected

    private fun isCurrentRecurringOccurrence(task: Task, expected: Long): Boolean =
        isCurrentOccurrence(task, expected) && task.recurrenceType != RecurrenceType.NONE

    private fun completeForm(
        previous: Task,
        intent: TaskWriteIntent.ApplyFormAndComplete,
        now: Long,
    ): Task {
        val form = normalizer.normalize(
            previous = previous,
            proposal = normalizer.overlayForm(previous, intent.formData),
            now = now,
        )
        return if (intent.scope == FormCompletionScope.SERIES ||
            form.recurrenceType == RecurrenceType.NONE || form.deadline == null) {
            completeSeries(form)
        } else {
            completeOccurrence(form)
        }
    }

    private fun completeOccurrence(task: Task): Task {
        val deadline = task.deadline ?: return task.copy(status = TaskStatus.DONE)
        if (task.recurrenceType == RecurrenceType.NONE) return task.copy(status = TaskStatus.DONE)
        val anchor = task.recurrenceAnchorDay ?: when (task.recurrenceType) {
            RecurrenceType.MONTHLY, RecurrenceType.YEARLY -> extractDay(deadline)
            else -> null
        }
        val nextDeadline = computeNextDeadlineLocal(
            currentDeadlineLocalMillis = deadline,
            recurrenceType = task.recurrenceType.name,
            interval = task.recurrenceInterval,
            anchorDay = anchor,
        )
        val duration = task.endDeadline?.let { it - deadline }
        return task.copy(
            deadline = nextDeadline,
            endDeadline = duration?.let { nextDeadline + it },
            recurrenceAnchorDay = anchor,
            status = TaskStatus.TODO,
            completedAt = null,
        )
    }

    private fun completeSeries(task: Task): Task = task.copy(
        status = TaskStatus.DONE,
        recurrenceType = RecurrenceType.NONE,
        recurrenceInterval = 0,
        recurrenceAnchorDay = null,
    )
}
