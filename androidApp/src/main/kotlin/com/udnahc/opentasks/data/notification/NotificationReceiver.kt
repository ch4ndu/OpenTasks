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
import com.udnahc.opentasks.data.extensions.utcToLocal
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
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(NotificationScheduler.EXTRA_BODY).orEmpty()
        val occurrenceDeadlineUtcMillis = intent.occurrenceDeadlineUtcMillis()
        val notificationAtUtcMillis = intent.notificationAtUtcMillis()
        val allowMarkDone = intent.getBooleanExtra(NotificationScheduler.EXTRA_ALLOW_MARK_DONE, false)
        val rescheduleAfterFire = intent.getBooleanExtra(NotificationScheduler.EXTRA_RESCHEDULE_AFTER_FIRE, false)
        val accountId = intent.getStringExtra(NotificationScheduler.EXTRA_ACCOUNT_ID)
        val boundaryEpoch = intent.getLongExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, 0L)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val delivered = accountBoundaryExecutor.withAuthenticatedBoundary(
                    expectedAccountId = accountId,
                    expectedBoundaryEpoch = boundaryEpoch,
                ) {
                    try {
                        deliverNotification(
                            context = context,
                            eventId = eventId,
                            semanticKey = semanticKey,
                            title = title,
                            body = body,
                            occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                            notificationAtUtcMillis = notificationAtUtcMillis,
                            allowMarkDone = allowMarkDone,
                            rescheduleAfterFire = rescheduleAfterFire,
                            accountId = accountId.orEmpty(),
                            boundaryEpoch = boundaryEpoch,
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
                    log.d { "Skipping notification delivery without a matching authenticated account session" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Notification session validation failed for $eventId" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliverNotification(
        context: Context,
        eventId: String,
        semanticKey: String,
        title: String,
        body: String,
        occurrenceDeadlineUtcMillis: Long?,
        notificationAtUtcMillis: Long,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
        accountId: String,
        boundaryEpoch: Long,
    ) {
        val notificationId = try {
            notificationScheduler.markAlarmDisplayed(semanticKey) ?: return
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            log.e(e) { "Unable to resolve reminder allocation for $eventId" }
            return
        }
        try {
            notificationScheduler.cancelDisplayedReminders(eventId, exceptSemanticKey = semanticKey)
            if (eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
                handleCountdownNotification(
                    context = context,
                    eventId = eventId,
                    semanticKey = semanticKey,
                    title = title,
                    body = body,
                    notificationId = notificationId,
                    occurrenceTargetUtcMillis = occurrenceDeadlineUtcMillis,
                    notificationAtUtcMillis = notificationAtUtcMillis,
                    rescheduleAfterFire = rescheduleAfterFire,
                    accountId = accountId,
                    boundaryEpoch = boundaryEpoch,
                )
                return
            }
            val task = taskRepository.getTaskById(eventId)
            if (task == null || task.status == TaskStatus.DONE || task.isDeleted) {
                notificationScheduler.cancelAll(eventId)
                log.d { "Skipping notification for inactive task $eventId" }
                return
            }
            val occurrenceDeadlineLocalMillis = occurrenceDeadlineUtcMillis?.let(::utcToLocal)
            val taskDeadline = task.deadline
            if (
                occurrenceDeadlineLocalMillis != null &&
                taskDeadline != null &&
                taskDeadline > occurrenceDeadlineLocalMillis
            ) {
                notificationScheduler.cancel(semanticKey)
                log.d { "Skipping stale notification for task $eventId" }
                return
            }
            if (
                rescheduleAfterFire &&
                occurrenceDeadlineUtcMillis != null &&
                task.recurrenceType != RecurrenceType.NONE
            ) {
                scheduleTaskRemindersAction.invokeAfterOccurrence(eventId, occurrenceDeadlineUtcMillis)
            }
            if (allowMarkDone && task.isAllDay && occurrenceDeadlineUtcMillis != null) {
                notificationScheduler.startOngoing(
                    identity = com.udnahc.opentasks.data.notification.ReminderIdentity(
                        eventId = eventId,
                        occurrenceUtcMillis = occurrenceDeadlineUtcMillis,
                        kind = com.udnahc.opentasks.data.notification.ReminderKind.ONGOING,
                        ordinal = 0,
                    ),
                    title = title,
                )
                notificationScheduler.cancel(semanticKey)
                return
            }
            if (!showNotification(
                    context = context,
                    eventId = eventId,
                    semanticKey = semanticKey,
                    title = title,
                    body = body,
                    notificationId = notificationId,
                    occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                    notificationAtUtcMillis = notificationAtUtcMillis,
                    allowMarkDone = allowMarkDone,
                    accountId = accountId,
                    boundaryEpoch = boundaryEpoch,
                )
            ) {
                notificationScheduler.cancel(semanticKey)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            // Preserve reminder delivery if an app-data lookup fails; the
            // alarm's semantic allocation remains the notification authority.
            log.e(e) { "Notification validation failed, showing reminder for $eventId" }
            if (!showNotification(
                    context = context,
                    eventId = eventId,
                    semanticKey = semanticKey,
                    title = title,
                    body = body,
                    notificationId = notificationId,
                    occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                    notificationAtUtcMillis = notificationAtUtcMillis,
                    allowMarkDone = allowMarkDone,
                    accountId = accountId,
                    boundaryEpoch = boundaryEpoch,
                )
            ) {
                notificationScheduler.cancel(semanticKey)
            }
        }
    }

    private suspend fun handleCountdownNotification(
        context: Context,
        eventId: String,
        semanticKey: String,
        title: String,
        body: String,
        notificationId: Int,
        occurrenceTargetUtcMillis: Long?,
        notificationAtUtcMillis: Long,
        rescheduleAfterFire: Boolean,
        accountId: String,
        boundaryEpoch: Long,
    ) {
        val countdownId = eventId.removePrefix(COUNTDOWN_ID_PREFIX)
        val countdown = countdownRepository.getCountdownByIdUtc(countdownId)
        if (countdown == null || countdown.isCompleted || countdown.isDeleted) {
            notificationScheduler.cancelAll(eventId)
            log.d { "Skipping notification for inactive countdown $countdownId" }
            return
        }
        if (
            occurrenceTargetUtcMillis != null &&
            !scheduleCountdownRemindersAction.isValidOccurrence(countdown, occurrenceTargetUtcMillis)
        ) {
            notificationScheduler.cancel(semanticKey)
            log.d { "Skipping stale notification for countdown $countdownId" }
            return
        }
        if (rescheduleAfterFire && occurrenceTargetUtcMillis != null) {
            scheduleCountdownRemindersAction.invokeAfterOccurrence(countdownId, occurrenceTargetUtcMillis)
        }
        if (!showNotification(
                context = context,
                eventId = eventId,
                semanticKey = semanticKey,
                title = title,
                body = body,
                notificationId = notificationId,
                occurrenceDeadlineUtcMillis = occurrenceTargetUtcMillis,
                notificationAtUtcMillis = notificationAtUtcMillis,
                allowMarkDone = false,
                accountId = accountId,
                boundaryEpoch = boundaryEpoch,
            )
        ) {
            notificationScheduler.cancel(semanticKey)
        }
    }

    private fun showNotification(
        context: Context,
        eventId: String,
        semanticKey: String,
        title: String,
        body: String,
        notificationId: Int,
        occurrenceDeadlineUtcMillis: Long?,
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
            occurrenceDeadlineUtcMillis?.let {
                putExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC, it)
            }
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
            occurrenceDeadlineUtcMillis?.let { occurrence ->
                notificationBuilder.addAction(
                    0,
                    context.getString(R.string.notification_action_mark_done),
                    NotificationScheduler.markDonePendingIntent(
                        context,
                        eventId,
                        semanticKey,
                        notificationId,
                        occurrence,
                        accountId,
                        boundaryEpoch,
                    ),
                )
            }
        }
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
            true
        } catch (e: SecurityException) {
            log.e(e) { "Failed to show notification (permission denied)" }
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
