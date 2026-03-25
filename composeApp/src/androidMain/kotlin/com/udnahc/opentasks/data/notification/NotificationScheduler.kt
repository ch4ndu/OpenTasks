package com.udnahc.opentasks.data.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.lighthousegames.logging.logging

private val log = logging("NotificationScheduler")

actual class NotificationScheduler(private val context: Context) {

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Task Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for task reminders"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        // Ongoing channel (lower importance, no sound)
        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            "All-Day Tasks",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing notifications for all-day tasks"
        }
        manager.createNotificationChannel(ongoingChannel)
    }

    actual fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        log.d { "Scheduling alarm for task=$taskId reminderId=$reminderId at $triggerAtMillis (exact=$canExact)" }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId(taskId, reminderId))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId(taskId, reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    actual fun cancel(taskId: String, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId(taskId, reminderId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    actual fun cancelReminders(taskId: String) {
        for (i in 0 until MAX_REMINDERS_PER_TASK) {
            cancel(taskId, i)
        }
    }

    actual fun cancelAll(taskId: String) {
        cancelReminders(taskId)
        stopOngoing(taskId)
    }

    actual fun startOngoing(taskId: String, title: String) {
        log.d { "Starting ongoing notification for task=$taskId" }
        val intent = Intent(context, OngoingNotificationService::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Android 12+ blocks foreground service starts from the background
            log.e { "Cannot start ongoing notification from background: ${e.message}" }
        }
    }

    actual fun stopOngoing(taskId: String) {
        log.d { "Stopping ongoing notification for task=$taskId" }
        val intent = Intent(context, OngoingNotificationService::class.java).apply {
            action = ACTION_STOP_ONGOING
            putExtra(EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val ONGOING_CHANNEL_ID = "all_day_tasks"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val ACTION_STOP_ONGOING = "com.udnahc.opentasks.STOP_ONGOING"
        const val ACTION_MARK_DONE = "com.udnahc.opentasks.ACTION_MARK_DONE"
        const val ACTION_GOT_IT = "com.udnahc.opentasks.ACTION_GOT_IT"
        private const val MAX_REMINDERS_PER_TASK = 100

        fun notificationId(taskId: String, reminderId: Int): Int =
            (taskId.hashCode().and(0x7FFFFFFF) / 100 * 100 + reminderId)
    }
}
