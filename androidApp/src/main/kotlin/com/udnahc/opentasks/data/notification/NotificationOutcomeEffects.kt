package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import kotlinx.coroutines.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("NotificationOutcomeEffects")

internal enum class NotificationCleanupPlan {
    OBSOLETE_THROUGH_OCCURRENCE,
    EXACT_OCCURRENCE,
    ALL_EVENT_IDENTITIES,
    EXACT_SEMANTIC_KEY,
}

internal data class NotificationEffectPlan(
    val cleanup: NotificationCleanupPlan,
    val refreshWidgets: Boolean,
    val retryReminderMaintenance: Boolean,
)

/** Maps only committed task outcomes to independent post-commit effects. */
internal fun notificationEffectPlan(
    mutation: CommittedMutation<TaskWriteResult>,
): NotificationEffectPlan? = when (mutation.value) {
    is TaskWriteResult.Updated -> NotificationEffectPlan(
        cleanup = NotificationCleanupPlan.OBSOLETE_THROUGH_OCCURRENCE,
        refreshWidgets = true,
        retryReminderMaintenance = mutation.postCommitWarning?.phase ==
            PostCommitWarningPhase.REMINDER_MAINTENANCE ||
            mutation.postCommitWarning?.phase == PostCommitWarningPhase.COMBINED,
    )
    TaskWriteResult.NoOp -> NotificationEffectPlan(
        cleanup = NotificationCleanupPlan.EXACT_OCCURRENCE,
        refreshWidgets = false,
        retryReminderMaintenance = false,
    )
    TaskWriteResult.Missing -> NotificationEffectPlan(
        cleanup = NotificationCleanupPlan.ALL_EVENT_IDENTITIES,
        refreshWidgets = false,
        retryReminderMaintenance = false,
    )
    TaskWriteResult.StaleOccurrence -> NotificationEffectPlan(
        cleanup = NotificationCleanupPlan.EXACT_SEMANTIC_KEY,
        refreshWidgets = false,
        retryReminderMaintenance = false,
    )
    is TaskWriteResult.CompletionChoiceRequired -> null
}

/**
 * Refreshes each widget family independently. A widget provider failure is
 * best-effort maintenance and must not change the already-committed task
 * outcome or suppress the other families.
 */
internal suspend fun refreshNotificationWidgetsIndependently(
    refreshTaskWidget: suspend () -> Unit,
    refreshCalendarWidget: suspend () -> Unit,
    refreshWeekWidget: suspend () -> Unit,
) {
    runWidgetRefresh("task") { refreshTaskWidget() }
    runWidgetRefresh("calendar") { refreshCalendarWidget() }
    runWidgetRefresh("week") { refreshWeekWidget() }
}

private suspend fun runWidgetRefresh(
    name: String,
    refresh: suspend () -> Unit,
) {
    try {
        refresh()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        log.e(error) { "Notification-driven $name widget refresh failed" }
    }
}
