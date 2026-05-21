package com.udnahc.opentasks.data.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.udnahc.opentasks.MainActivity
import com.udnahc.opentasks.R
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("NotificationReceiver")

class NotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val taskRepository: TaskRepository by inject()
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction by inject()
    private val notificationScheduler: NotificationScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID)
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(NotificationScheduler.EXTRA_BODY) ?: ""
        val notificationId = intent.getIntExtra(NotificationScheduler.EXTRA_NOTIFICATION_ID, 0)
        val occurrenceDeadlineUtcMillis = intent.occurrenceDeadlineUtcMillis()
        val allowMarkDone = intent.getBooleanExtra(NotificationScheduler.EXTRA_ALLOW_MARK_DONE, false)
        val rescheduleAfterFire = intent.getBooleanExtra(NotificationScheduler.EXTRA_RESCHEDULE_AFTER_FIRE, false)

        taskId?.let {
            NotificationScheduler.cancelDisplayedReminders(
                context = context,
                eventId = it,
                exceptNotificationId = notificationId,
            )
        }

        if (taskId != null && !taskId.startsWith(COUNTDOWN_ID_PREFIX)) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val task = taskRepository.getTaskById(taskId)
                    if (task == null || task.status == TaskStatus.DONE || task.isDeleted) {
                        NotificationScheduler.cancelDisplayedReminders(context, taskId)
                        log.d { "Skipping notification for inactive task $taskId" }
                        return@launch
                    }
                    val occurrenceDeadlineLocalMillis = occurrenceDeadlineUtcMillis?.let { utcToLocal(it) }
                    if (
                        occurrenceDeadlineLocalMillis != null &&
                        task.deadline != null &&
                        task.deadline > occurrenceDeadlineLocalMillis
                    ) {
                        NotificationScheduler.cancelDisplayedReminders(context, taskId)
                        log.d { "Skipping stale notification for task $taskId" }
                        return@launch
                    }
                    if (
                        rescheduleAfterFire &&
                        occurrenceDeadlineUtcMillis != null &&
                        task.recurrenceType != RecurrenceType.NONE
                    ) {
                        scheduleTaskRemindersAction.invokeAfterOccurrence(taskId, occurrenceDeadlineUtcMillis)
                    }
                    if (allowMarkDone && task.isAllDay && occurrenceDeadlineUtcMillis != null) {
                        notificationScheduler.startOngoing(
                            taskId = taskId,
                            title = title,
                            occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                        )
                        return@launch
                    }
                    showNotification(
                        context = context,
                        eventId = taskId,
                        title = title,
                        body = body,
                        notificationId = notificationId,
                        occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                        allowMarkDone = allowMarkDone,
                    )
                } catch (e: Exception) {
                    // Fail-open: show notification if DB lookup fails
                    log.e(e) { "Task lookup failed, showing notification anyway" }
                    showNotification(
                        context = context,
                        eventId = taskId,
                        title = title,
                        body = body,
                        notificationId = notificationId,
                        occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                        allowMarkDone = allowMarkDone,
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            showNotification(
                context = context,
                eventId = taskId,
                title = title,
                body = body,
                notificationId = notificationId,
                occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
                allowMarkDone = allowMarkDone,
            )
        }
    }

    private fun showNotification(
        context: Context,
        eventId: String?,
        title: String,
        body: String,
        notificationId: Int,
        occurrenceDeadlineUtcMillis: Long?,
        allowMarkDone: Boolean,
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            eventId?.let { putExtra(NotificationScheduler.EXTRA_TASK_ID, it) }
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        log.d { "Showing notification: title='$title' id=$notificationId" }

        val notificationBuilder = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)

        if (allowMarkDone && eventId != null && !eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
            notificationBuilder.addAction(
                0,
                context.getString(R.string.notification_action_mark_done),
                NotificationScheduler.markDonePendingIntent(
                    context,
                    eventId,
                    notificationId,
                    occurrenceDeadlineUtcMillis,
                ),
            )
        }

        val notification = notificationBuilder.build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            log.e(e) { "Failed to show notification (permission denied)" }
        }
    }

    private fun Intent.occurrenceDeadlineUtcMillis(): Long? =
        if (hasExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC)) {
            getLongExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC, 0L)
        } else {
            null
        }
}
