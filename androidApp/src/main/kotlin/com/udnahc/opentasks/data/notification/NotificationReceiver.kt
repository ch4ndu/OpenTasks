package com.udnahc.opentasks.data.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.udnahc.opentasks.MainActivity
import com.udnahc.opentasks.R
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("NotificationReceiver")

class NotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val taskRepository: TaskRepository by inject()
    private val countdownRepository: CountdownRepository by inject()
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction by inject()
    private val scheduleCountdownRemindersAction: ScheduleCountdownRemindersAction by inject()
    private val notificationScheduler: NotificationScheduler by inject()
    private val accountBoundaryExecutor: AccountBoundaryExecutor by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: return
        val semanticKey = intent.getStringExtra(NotificationScheduler.EXTRA_SEMANTIC_KEY) ?: return
        val delivery = DeliveryCommandPayload(
            eventId = eventId,
            semanticKey = semanticKey,
            occurrenceUtcMillis = intent.occurrenceDeadlineUtcMillis(),
            accountId = intent.getStringExtra(NotificationScheduler.EXTRA_ACCOUNT_ID),
            boundaryEpoch = intent.getLongExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, 0L),
        ).validatedDeliveryCommand() ?: run {
            log.w { "Skipping notification delivery with an invalid canonical payload" }
            return
        }
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(NotificationScheduler.EXTRA_BODY).orEmpty()
        val notificationAtUtcMillis = intent.notificationAtUtcMillis()
        val allowMarkDone = intent.getBooleanExtra(NotificationScheduler.EXTRA_ALLOW_MARK_DONE, false)
        val rescheduleAfterFire = intent.getBooleanExtra(NotificationScheduler.EXTRA_RESCHEDULE_AFTER_FIRE, false)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val delivered = accountBoundaryExecutor.withActiveCacheBoundary(
                    expectedAccountId = delivery.accountId,
                    expectedBoundaryEpoch = delivery.boundaryEpoch,
                ) {
                    try {
                        deliverNotification(
                            context = context,
                            delivery = delivery,
                            title = title,
                            body = body,
                            notificationAtUtcMillis = notificationAtUtcMillis,
                            allowMarkDone = allowMarkDone,
                            rescheduleAfterFire = rescheduleAfterFire,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        try {
                            notificationScheduler.cancelAllAccountReminders()
                        } catch (cleanupError: CancellationException) {
                            throw cleanupError
                        } catch (_: Exception) {
                            // Preserve the original delivery failure for the outer logger.
                        }
                        throw error
                    }
                }
                if (delivered == null) {
                    log.d { "Skipping notification delivery without a matching active cache session" }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.e(error) { "Notification session validation failed for ${delivery.eventId}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliverNotification(
        context: Context,
        delivery: ValidatedDeliveryCommand,
        title: String,
        body: String,
        notificationAtUtcMillis: Long,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
    ) {
        runValidatedReminderDelivery(
            resolveCurrent = {
                if (delivery.eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
                    resolveCountdownDelivery(
                        context = context,
                        delivery = delivery,
                        title = title,
                        body = body,
                        notificationAtUtcMillis = notificationAtUtcMillis,
                        rescheduleAfterFire = rescheduleAfterFire,
                    )
                } else {
                    resolveTaskDelivery(
                        context = context,
                        delivery = delivery,
                        title = title,
                        body = body,
                        notificationAtUtcMillis = notificationAtUtcMillis,
                        allowMarkDone = allowMarkDone,
                        rescheduleAfterFire = rescheduleAfterFire,
                    )
                }
            },
            cleanupPriorDisplays = {
                notificationScheduler.cancelDisplayedReminders(
                    eventId = delivery.eventId,
                    exceptSemanticKey = delivery.semanticKey,
                )
            },
            discardExact = { notificationScheduler.cancel(delivery.semanticKey) },
            discardAll = { notificationScheduler.cancelAll(delivery.eventId) },
            logOperationalFailure = { phase, error ->
                log.e(error) { "Reminder $phase failed for ${delivery.eventId}" }
            },
        )
    }

    private suspend fun resolveTaskDelivery(
        context: Context,
        delivery: ValidatedDeliveryCommand,
        title: String,
        body: String,
        notificationAtUtcMillis: Long,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
    ): ReminderDeliveryResolution {
        val task = taskRepository.getTaskByIdUtc(delivery.eventId)
        if (task == null || task.status == TaskStatus.DONE || task.isDeleted) {
            log.d { "Skipping notification for inactive task ${delivery.eventId}" }
            return ReminderDeliveryResolution.DiscardAll
        }
        if (!scheduleTaskRemindersAction.isValidOccurrence(task, delivery.occurrenceUtcMillis)) {
            log.d { "Skipping stale notification for task ${delivery.eventId}" }
            return ReminderDeliveryResolution.DiscardExact
        }
        return ReminderDeliveryResolution.Deliver(
            prepareCurrentDisplay = { notificationScheduler.markAlarmDisplayed(delivery.semanticKey) },
            chainNextOccurrence = {
                if (rescheduleAfterFire && task.recurrenceType != RecurrenceType.NONE) {
                    scheduleTaskRemindersAction.invokeAfterOccurrence(
                        delivery.eventId,
                        delivery.occurrenceUtcMillis,
                    )
                }
            },
            displayCurrent = { notificationId ->
                if (allowMarkDone && task.isAllDay) {
                    notificationScheduler.startOngoing(
                        identity = ReminderIdentity(
                            eventId = delivery.eventId,
                            occurrenceUtcMillis = delivery.occurrenceUtcMillis,
                            kind = ReminderKind.ONGOING,
                            ordinal = 0,
                        ),
                        title = title,
                    )
                    notificationScheduler.cancel(delivery.semanticKey)
                    true
                } else {
                    showNotification(
                        context = context,
                        eventId = delivery.eventId,
                        semanticKey = delivery.semanticKey,
                        title = title,
                        body = body,
                        notificationId = notificationId,
                        occurrenceDeadlineUtcMillis = delivery.occurrenceUtcMillis,
                        notificationAtUtcMillis = notificationAtUtcMillis,
                        allowMarkDone = allowMarkDone,
                        accountId = delivery.accountId,
                        boundaryEpoch = delivery.boundaryEpoch,
                    )
                }
            },
        )
    }

    private suspend fun resolveCountdownDelivery(
        context: Context,
        delivery: ValidatedDeliveryCommand,
        title: String,
        body: String,
        notificationAtUtcMillis: Long,
        rescheduleAfterFire: Boolean,
    ): ReminderDeliveryResolution {
        val countdownId = delivery.eventId.removePrefix(COUNTDOWN_ID_PREFIX)
        val countdown = countdownRepository.getCountdownByIdUtc(countdownId)
        if (countdown == null || countdown.isCompleted || countdown.isDeleted) {
            log.d { "Skipping notification for inactive countdown $countdownId" }
            return ReminderDeliveryResolution.DiscardAll
        }
        if (!scheduleCountdownRemindersAction.isValidOccurrence(countdown, delivery.occurrenceUtcMillis)) {
            log.d { "Skipping stale notification for countdown $countdownId" }
            return ReminderDeliveryResolution.DiscardExact
        }
        return ReminderDeliveryResolution.Deliver(
            prepareCurrentDisplay = { notificationScheduler.markAlarmDisplayed(delivery.semanticKey) },
            chainNextOccurrence = {
                if (rescheduleAfterFire) {
                    scheduleCountdownRemindersAction.invokeAfterOccurrence(
                        countdownId,
                        delivery.occurrenceUtcMillis,
                    )
                }
            },
            displayCurrent = { notificationId ->
                showNotification(
                    context = context,
                    eventId = delivery.eventId,
                    semanticKey = delivery.semanticKey,
                    title = title,
                    body = body,
                    notificationId = notificationId,
                    occurrenceDeadlineUtcMillis = delivery.occurrenceUtcMillis,
                    notificationAtUtcMillis = notificationAtUtcMillis,
                    allowMarkDone = false,
                    accountId = delivery.accountId,
                    boundaryEpoch = delivery.boundaryEpoch,
                )
            },
        )
    }

    private fun showNotification(
        context: Context,
        eventId: String,
        semanticKey: String,
        title: String,
        body: String,
        notificationId: Int,
        occurrenceDeadlineUtcMillis: Long,
        notificationAtUtcMillis: Long,
        allowMarkDone: Boolean,
        accountId: String,
        boundaryEpoch: Long,
    ): Boolean {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = NotificationScheduler.pendingIntentUri(semanticKey, NotificationScheduler.ROLE_TAP)
            putExtra(NotificationScheduler.EXTRA_TASK_ID, eventId)
            putExtra(NotificationScheduler.EXTRA_SEMANTIC_KEY, semanticKey)
            putExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC, occurrenceDeadlineUtcMillis)
            putExtra(NotificationScheduler.EXTRA_NOTIFICATION_AT_UTC, notificationAtUtcMillis)
            putExtra(NotificationScheduler.EXTRA_ACCOUNT_ID, accountId)
            putExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, boundaryEpoch)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationBuilder = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)

        if (allowMarkDone && !eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
            notificationBuilder.addAction(
                0,
                context.getString(R.string.notification_action_mark_done),
                NotificationScheduler.markDonePendingIntent(
                    context,
                    eventId,
                    semanticKey,
                    notificationId,
                    occurrenceDeadlineUtcMillis,
                    accountId,
                    boundaryEpoch,
                ),
            )
        }
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
            true
        } catch (error: SecurityException) {
            log.e(error) { "Failed to show notification (permission denied)" }
            false
        }
    }

    private fun Intent.occurrenceDeadlineUtcMillis(): Long? =
        if (hasExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC)) {
            getLongExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC, 0L)
        } else {
            null
        }

    private fun Intent.notificationAtUtcMillis(): Long =
        if (hasExtra(NotificationScheduler.EXTRA_NOTIFICATION_AT_UTC)) {
            getLongExtra(NotificationScheduler.EXTRA_NOTIFICATION_AT_UTC, 0L)
        } else {
            System.currentTimeMillis()
        }
}
