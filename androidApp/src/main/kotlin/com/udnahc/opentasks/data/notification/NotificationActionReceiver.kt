package com.udnahc.opentasks.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.domain.action.task.MarkTaskNotificationDoneAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("NotificationActionReceiver")

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationScheduler: NotificationScheduler by inject()
    private val markTaskNotificationDoneAction: MarkTaskNotificationDoneAction by inject()
    private val allDayNotificationDismissalStore: AllDayNotificationDismissalStore by inject()
    private val accountBoundaryExecutor: AccountBoundaryExecutor by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: return
        val semanticKey = intent.getStringExtra(NotificationScheduler.EXTRA_SEMANTIC_KEY) ?: return
        log.d { "Notification action received: ${intent.action} for task $taskId" }

        when (intent.action) {
            NotificationScheduler.ACTION_GOT_IT -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val handled = accountBoundaryExecutor.withAuthenticatedBoundary(
                            expectedAccountId = intent.accountId(),
                            expectedBoundaryEpoch = intent.boundaryEpoch(),
                        ) {
                            allDayNotificationDismissalStore.dismissToday(taskId)
                            notificationScheduler.cancel(semanticKey)
                        }
                        if (handled == null) {
                            log.d { "Skipping all-day notification dismissal without a matching authenticated account session for task $taskId" }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.e(e) { "Failed to dismiss all-day notification for task $taskId" }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            NotificationScheduler.ACTION_MARK_DONE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val handled = accountBoundaryExecutor.withAuthenticatedBoundary(
                            expectedAccountId = intent.accountId(),
                            expectedBoundaryEpoch = intent.boundaryEpoch(),
                        ) {
                            markTaskNotificationDoneAction(taskId, intent.occurrenceDeadlineUtcMillis())
                            notificationScheduler.cancelDisplayedReminders(taskId, exceptSemanticKey = semanticKey)
                            notificationScheduler.cancel(semanticKey)
                            notificationScheduler.stopOngoing(taskId)
                        }
                        if (handled == null) {
                            log.d { "Skipping Mark Done action without a matching authenticated account session for task $taskId" }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.e(e) { "Failed to handle Mark Done action for task $taskId" }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun Intent.occurrenceDeadlineUtcMillis(): Long? =
        if (hasExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC)) {
            getLongExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC, 0L)
        } else {
            null
        }

    private fun Intent.accountId(): String? = getStringExtra(NotificationScheduler.EXTRA_ACCOUNT_ID)

    private fun Intent.boundaryEpoch(): Long =
        getLongExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, 0L)
}
