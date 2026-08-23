package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarning
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationOutcomeEffectsTest {
    @Test
    fun committedOutcomeReducerPlansCleanupWidgetsAndOnlyMaintenanceRetries() {
        val task = Task(id = "task", title = "Task", content = "Content")
        val updatedWithoutWarning = notificationEffectPlan(
            CommittedMutation(TaskWriteResult.Updated(task)),
        )
        assertEquals(
            NotificationEffectPlan(
                cleanup = NotificationCleanupPlan.OBSOLETE_THROUGH_OCCURRENCE,
                refreshWidgets = true,
                retryReminderMaintenance = false,
            ),
            updatedWithoutWarning,
        )

        PostCommitWarningPhase.entries.forEach { phase ->
            val plan = notificationEffectPlan(
                CommittedMutation(
                    value = TaskWriteResult.Updated(task),
                    postCommitWarning = PostCommitWarning(
                        cause = IllegalStateException("warning"),
                        phase = phase,
                    ),
                ),
            )
            assertTrue(plan?.refreshWidgets == true)
            assertEquals(
                phase == PostCommitWarningPhase.REMINDER_MAINTENANCE ||
                    phase == PostCommitWarningPhase.COMBINED,
                plan.retryReminderMaintenance,
            )
        }

        assertEquals(
            NotificationEffectPlan(NotificationCleanupPlan.EXACT_OCCURRENCE, false, false),
            notificationEffectPlan(CommittedMutation(TaskWriteResult.NoOp)),
        )
        assertEquals(
            NotificationEffectPlan(NotificationCleanupPlan.ALL_EVENT_IDENTITIES, false, false),
            notificationEffectPlan(CommittedMutation(TaskWriteResult.Missing)),
        )
        assertEquals(
            NotificationEffectPlan(NotificationCleanupPlan.EXACT_SEMANTIC_KEY, false, false),
            notificationEffectPlan(CommittedMutation(TaskWriteResult.StaleOccurrence)),
        )
        assertNull(
            notificationEffectPlan(
                CommittedMutation(TaskWriteResult.CompletionChoiceRequired(expectedOccurrence = 100L)),
            ),
        )
    }

    @Test
    fun widgetFamiliesAreRefreshedIndependentlyAfterOneFailure() = runTest {
        val refreshed = mutableListOf<String>()

        refreshNotificationWidgetsIndependently(
            refreshTaskWidget = {
                refreshed += "task"
                error("task widget failed")
            },
            refreshCalendarWidget = { refreshed += "calendar" },
            refreshWeekWidget = { refreshed += "week" },
        )

        assertEquals(listOf("task", "calendar", "week"), refreshed)
    }

    @Test
    fun cancellationIsNotConvertedIntoAWidgetMaintenanceWarning() = runTest {
        assertFailsWith<CancellationException> {
            refreshNotificationWidgetsIndependently(
                refreshTaskWidget = { throw CancellationException("cancelled") },
                refreshCalendarWidget = { error("must not run") },
                refreshWeekWidget = { error("must not run") },
            )
        }
    }
}
