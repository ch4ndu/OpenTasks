package com.udnahc.opentasks.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import com.udnahc.opentasks.domain.action.task.DismissTaskNotificationAction
import com.udnahc.opentasks.domain.action.task.MarkTaskNotificationDoneAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.widget.CalendarWidget
import com.udnahc.opentasks.widget.TaskWidget
import com.udnahc.opentasks.widget.WeekWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("NotificationActionReceiver")

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationScheduler: NotificationScheduler by inject()
    private val markTaskNotificationDoneAction: MarkTaskNotificationDoneAction by inject()
    private val dismissTaskNotificationAction: DismissTaskNotificationAction by inject()
    private val accountBoundaryExecutor: AccountBoundaryExecutor by inject()
    private val rebuildReminderQueueAction: RebuildReminderQueueAction by inject()
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val command = when (action) {
            NotificationScheduler.ACTION_MARK_DONE -> ReminderCommand.MARK_DONE
            NotificationScheduler.ACTION_GOT_IT -> ReminderCommand.GOT_IT
            else -> return
        }
        val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID)
        val semanticKey = intent.getStringExtra(NotificationScheduler.EXTRA_SEMANTIC_KEY)
        val accountId = intent.getStringExtra(NotificationScheduler.EXTRA_ACCOUNT_ID)
        val boundaryEpoch = intent.getLongExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, 0L)
        val occurrence = intent.occurrenceDeadlineUtcMillis()
        val validation = validateReminderCommand(
            command = command,
            semanticKey = semanticKey,
            eventId = taskId,
            occurrenceUtcMillis = occurrence,
            accountId = accountId,
            boundaryEpoch = boundaryEpoch,
        )
        val accepted = validation as? ReminderCommandValidation.Accepted
        if (accepted == null) {
            log.w { "Rejected notification action because its reminder identity was invalid" }
            return
        }
        val validatedTaskId = taskId ?: return
        val validatedSemanticKey = semanticKey ?: return
        val validatedAccountId = accountId ?: return
        val validatedOccurrence = accepted.identity.occurrenceUtcMillis

        log.d { "Notification action received: $action for task $validatedTaskId" }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val boundary = accountBoundaryExecutor.withActiveCacheBoundary(
                    expectedAccountId = validatedAccountId,
                    expectedBoundaryEpoch = boundaryEpoch,
                ) { activeBoundary -> activeBoundary }
                if (boundary == null) {
                    log.d { "Skipping notification action without a matching active cache session" }
                } else {
                    when (command) {
                        ReminderCommand.GOT_IT -> accountBoundaryExecutor.withForegroundBoundary(boundary) {
                            handleGotIt(
                                taskId = validatedTaskId,
                                semanticKey = validatedSemanticKey,
                                occurrenceUtcMillis = validatedOccurrence,
                                accountId = validatedAccountId,
                                boundaryEpoch = boundary.boundaryEpoch,
                            )
                        }
                        ReminderCommand.MARK_DONE -> handleMarkDone(
                            taskId = validatedTaskId,
                            semanticKey = validatedSemanticKey,
                            occurrenceUtcMillis = validatedOccurrence,
                            accountId = validatedAccountId,
                            boundary = boundary,
                            context = context,
                        )
                        else -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Boundary, validation, and pre-commit failures intentionally
                // have no cleanup or widget side effects.
                log.e(error) { "Failed to handle notification action" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleGotIt(
        taskId: String,
        semanticKey: String,
        occurrenceUtcMillis: Long,
        accountId: String,
        boundaryEpoch: Long,
    ) {
        try {
            dismissTaskNotificationAction(
                taskId = taskId,
                semanticKey = semanticKey,
                occurrenceDeadlineUtcMillis = occurrenceUtcMillis,
                accountId = accountId,
                boundaryEpoch = boundaryEpoch,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.e(error) { "Failed to dismiss all-day notification for $taskId" }
        }
    }

    private suspend fun handleMarkDone(
        taskId: String,
        semanticKey: String,
        occurrenceUtcMillis: Long,
        accountId: String,
        boundary: AccountBoundary,
        context: Context,
    ) {
        val committed = markTaskNotificationDoneAction(
            taskId = taskId,
            occurrenceDeadlineUtcMillis = occurrenceUtcMillis,
            semanticKey = semanticKey,
            accountId = accountId,
            boundaryEpoch = boundary.boundaryEpoch,
            expectedBoundary = boundary,
        )
        accountBoundaryExecutor.withForegroundBoundary(boundary) {
            val effects = notificationEffectPlan(committed) ?: return@withForegroundBoundary
            when (effects.cleanup) {
                NotificationCleanupPlan.OBSOLETE_THROUGH_OCCURRENCE ->
                    cleanupObsoleteOccurrence(taskId, occurrenceUtcMillis)
                NotificationCleanupPlan.EXACT_OCCURRENCE ->
                    cleanupExactOccurrence(taskId, occurrenceUtcMillis)
                NotificationCleanupPlan.ALL_EVENT_IDENTITIES -> cleanupMissingTask(taskId)
                NotificationCleanupPlan.EXACT_SEMANTIC_KEY -> cleanupExactSemanticKey(semanticKey)
            }
            if (effects.retryReminderMaintenance) {
                retryTargetedReminderMaintenance(taskId)
            }
            if (effects.refreshWidgets) {
                refreshNotificationWidgets(context, boundary)
            }
        }
    }

    private suspend fun retryTargetedReminderMaintenance(taskId: String) {
        try {
            val retryWarning = rebuildReminderQueueAction.afterRecordChangeResult(
                scheduleDirectly = { scheduleTaskRemindersAction(taskId) },
            )
            if (retryWarning != null) {
                log.w(retryWarning) { "Targeted reminder maintenance retry failed for $taskId" }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.e(error) { "Targeted reminder maintenance retry failed for $taskId" }
        }
    }

    private suspend fun cleanupObsoleteOccurrence(taskId: String, occurrenceUtcMillis: Long) {
        try {
            notificationScheduler.cancelObsoleteOccurrenceReminders(taskId, occurrenceUtcMillis)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.e(error) { "Failed to clean obsolete reminders for $taskId" }
        }
    }

    private suspend fun cleanupExactOccurrence(taskId: String, occurrenceUtcMillis: Long) {
        try {
            notificationScheduler.cancelOccurrenceReminders(taskId, occurrenceUtcMillis)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.e(error) { "Failed to clean obsolete occurrence for $taskId" }
        }
    }

    private suspend fun cleanupMissingTask(taskId: String) {
        try {
            notificationScheduler.cancelAll(taskId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.e(error) { "Failed to clean reminders for missing task $taskId" }
        }
    }

    private suspend fun cleanupExactSemanticKey(semanticKey: String) {
        try {
            notificationScheduler.cancel(semanticKey)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.e(error) { "Failed to clean stale reminder identity" }
        }
    }

    private suspend fun refreshNotificationWidgets(
        context: Context,
        boundary: AccountBoundary,
    ) {
        refreshNotificationWidgetsIndependently(
            refreshTaskWidget = { TaskWidget.refreshAllWidgetsWithinBoundary(context, boundary) },
            refreshCalendarWidget = { CalendarWidget.refreshAllWidgetsWithinBoundary(context, boundary) },
            refreshWeekWidget = { WeekWidget.refreshAllWidgetsWithinBoundary(context, boundary) },
        )
    }

    private fun Intent.occurrenceDeadlineUtcMillis(): Long? =
        if (hasExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC)) {
            getLongExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC, 0L)
        } else {
            null
        }
}
