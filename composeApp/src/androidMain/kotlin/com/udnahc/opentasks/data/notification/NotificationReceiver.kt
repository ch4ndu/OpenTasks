package com.udnahc.opentasks.data.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.udnahc.opentasks.MainActivity
import com.udnahc.opentasks.R
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("NotificationReceiver")

class NotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val taskRepository: TaskRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID)
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(NotificationScheduler.EXTRA_BODY) ?: ""
        val notificationId = intent.getIntExtra(NotificationScheduler.EXTRA_NOTIFICATION_ID, 0)

        if (taskId != null && !taskId.startsWith(COUNTDOWN_ID_PREFIX)) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val task = taskRepository.getTaskById(taskId)
                    if (task == null || task.status == TaskStatus.DONE || task.isDeleted) {
                        log.d { "Skipping notification for inactive task $taskId" }
                        return@launch
                    }
                    showNotification(context, taskId, title, body, notificationId)
                } catch (e: Exception) {
                    // Fail-open: show notification if DB lookup fails
                    log.e(e) { "Task lookup failed, showing notification anyway" }
                    showNotification(context, taskId, title, body, notificationId)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            showNotification(context, taskId, title, body, notificationId)
        }
    }

    private fun showNotification(
        context: Context,
        eventId: String?,
        title: String,
        body: String,
        notificationId: Int,
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

        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            log.e(e) { "Failed to show notification (permission denied)" }
        }
    }
}
