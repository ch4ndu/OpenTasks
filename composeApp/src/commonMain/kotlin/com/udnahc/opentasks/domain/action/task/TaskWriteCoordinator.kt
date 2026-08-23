package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.computeNextDeadlineLocal
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.mapValue
import com.udnahc.opentasks.data.repository.TaskGraphDeletionResult
import com.udnahc.opentasks.data.repository.TaskMutationResult
import com.udnahc.opentasks.data.repository.TaskRepository

private const val MAX_OCCURRENCE_ADVANCES_PER_LOOKUP = 4096

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
    suspend fun create(task: Task): CommittedMutation<Task> {
        val normalized = normalizer.create(task, localNow())
        return repository.insert(normalized).mapValue { normalized }
    }

    suspend fun deleteGraph(id: String): CommittedMutation<TaskGraphDeletionResult> =
        repository.deleteGraph(id)

    suspend fun write(id: String, intent: TaskWriteIntent): CommittedMutation<TaskWriteResult> {
        if (intent == TaskWriteIntent.Delete) {
            val deleted = deleteGraph(id)
            val result = when (val value = deleted.value) {
                TaskGraphDeletionResult.Missing -> TaskWriteResult.Missing
                is TaskGraphDeletionResult.Deleted -> TaskWriteResult.Updated(value.task)
            }
            return deleted.mapValue { result }
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
                    } else completeOccurrence(previous, intent.expectedOccurrence)
                }
                is TaskWriteIntent.CompleteSeries -> {
                    if (intent.expectedOccurrence != null &&
                        !isCurrentOccurrence(previous, intent.expectedOccurrence)
                    ) {
                        outcome = TaskWriteResult.StaleOccurrence
                        null
                    } else completeSeries(previous)
                }
                is TaskWriteIntent.NotificationMarkDone -> {
                    if (previous.status == TaskStatus.DONE) {
                        outcome = TaskWriteResult.NoOp
                        null
                    } else if (intent.expectedOccurrence == null ||
                        !isCurrentNotificationOccurrence(previous, intent.expectedOccurrence)
                    ) {
                        outcome = TaskWriteResult.StaleOccurrence
                        null
                    } else if (previous.recurrenceType != RecurrenceType.NONE && previous.deadline != null) {
                        completeOccurrence(previous, intent.expectedOccurrence)
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
        val result = when (val value = stored.value) {
            TaskMutationResult.Missing -> TaskWriteResult.Missing
            is TaskMutationResult.Existing -> value.next?.let(TaskWriteResult::Updated) ?: outcome
        }
        return stored.mapValue { result }
    }

    private fun requiresCompletionChoice(previous: Task, requestedStatus: TaskStatus): Boolean =
        previous.status != TaskStatus.DONE && requestedStatus == TaskStatus.DONE &&
            previous.recurrenceType != RecurrenceType.NONE && previous.deadline != null

    private fun isCurrentOccurrence(task: Task, expected: Long): Boolean =
        task.status != TaskStatus.DONE && expected > 0L && task.deadline == expected

    private fun isCurrentRecurringOccurrence(task: Task, expected: Long): Boolean =
        isCurrentOccurrence(task, expected) && task.recurrenceType != RecurrenceType.NONE

    /**
     * Notification delivery may target an occurrence projected beyond the
     * stored anchor. Ordinary task writes remain exact-anchor operations.
     */
    private fun isCurrentNotificationOccurrence(task: Task, expected: Long): Boolean =
        if (task.recurrenceType == RecurrenceType.NONE) {
            isCurrentOccurrence(task, expected)
        } else {
            isCurrentNotificationRecurringOccurrence(task, expected)
        }

    private fun isCurrentNotificationRecurringOccurrence(task: Task, expected: Long): Boolean =
        task.status != TaskStatus.DONE &&
            task.recurrenceType != RecurrenceType.NONE &&
            isRecurringOccurrenceMember(task, expected)

    private fun isRecurringOccurrenceMember(task: Task, expected: Long): Boolean {
        if (task.status == TaskStatus.DONE || expected <= 0L) return false
        var candidate = task.deadline ?: return false
        repeat(MAX_OCCURRENCE_ADVANCES_PER_LOOKUP) {
            when {
                candidate == expected -> return true
                candidate > expected -> return false
            }
            val next = computeNextDeadlineLocal(
                currentDeadlineLocalMillis = candidate,
                recurrenceType = task.recurrenceType.name,
                interval = task.recurrenceInterval,
                anchorDay = task.recurrenceAnchorDay,
            )
            if (next <= candidate) return false
            candidate = next
        }
        return false
    }

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
            form.recurrenceType == RecurrenceType.NONE || form.deadline == null
        ) {
            completeSeries(form)
        } else {
            completeOccurrence(form, intent.expectedOccurrence)
        }
    }

    private fun completeOccurrence(task: Task, completedOccurrence: Long): Task {
        val storedDeadline = task.deadline ?: return task.copy(status = TaskStatus.DONE)
        if (task.recurrenceType == RecurrenceType.NONE) return task.copy(status = TaskStatus.DONE)
        val anchor = task.recurrenceAnchorDay ?: when (task.recurrenceType) {
            RecurrenceType.MONTHLY, RecurrenceType.YEARLY -> extractDay(storedDeadline)
            else -> null
        }
        val nextDeadline = computeNextDeadlineLocal(
            currentDeadlineLocalMillis = completedOccurrence,
            recurrenceType = task.recurrenceType.name,
            interval = task.recurrenceInterval,
            anchorDay = anchor,
        )
        val duration = task.endDeadline?.let { it - storedDeadline }
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
