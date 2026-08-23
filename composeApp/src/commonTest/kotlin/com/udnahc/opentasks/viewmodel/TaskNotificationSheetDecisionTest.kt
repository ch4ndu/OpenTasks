package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarning
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskNotificationSheetDecisionTest {
    private val task = Task(id = "task", title = "Task", content = "Content")

    @Test
    fun everyCommittedUpdatedWarningUsesTheSavedWarningFeedback() {
        assertEquals(
            TaskNotificationSheetDecision(close = true),
            taskNotificationSheetDecision(CommittedMutation(TaskWriteResult.Updated(task))),
        )

        PostCommitWarningPhase.entries.forEach { phase ->
            assertEquals(
                TaskNotificationSheetDecision(
                    close = true,
                    feedback = TaskNotificationSheetFeedback.SAVED_WARNING,
                ),
                taskNotificationSheetDecision(
                    CommittedMutation(
                        value = TaskWriteResult.Updated(task),
                        postCommitWarning = PostCommitWarning(
                            cause = IllegalStateException("maintenance failed"),
                            phase = phase,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun nonUpdatedResultsKeepTheirApprovedSheetDecisions() {
        assertEquals(
            TaskNotificationSheetDecision(
                close = true,
                feedback = TaskNotificationSheetFeedback.OBSOLETE,
            ),
            taskNotificationSheetDecision(CommittedMutation(TaskWriteResult.NoOp)),
        )
        assertEquals(
            TaskNotificationSheetDecision(
                close = true,
                feedback = TaskNotificationSheetFeedback.TASK_MISSING,
            ),
            taskNotificationSheetDecision(CommittedMutation(TaskWriteResult.Missing)),
        )
        assertEquals(
            TaskNotificationSheetDecision(
                close = true,
                feedback = TaskNotificationSheetFeedback.STALE,
            ),
            taskNotificationSheetDecision(CommittedMutation(TaskWriteResult.StaleOccurrence)),
        )
        assertEquals(
            TaskNotificationSheetDecision(close = false),
            taskNotificationSheetDecision(
                CommittedMutation(TaskWriteResult.CompletionChoiceRequired(expectedOccurrence = 100L)),
            ),
        )
    }
}
