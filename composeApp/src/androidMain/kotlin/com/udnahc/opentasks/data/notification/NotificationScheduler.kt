package com.udnahc.opentasks.data.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

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
        taskId: Long,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    actual fun cancel(taskId: Long, reminderId: Int) {
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

    actual fun cancelAll(taskId: Long) {
        // Cancel up to 100 possible reminder slots per task
        for (i in 0 until MAX_REMINDERS_PER_TASK) {
            cancel(taskId, i)
        }
        stopOngoing(taskId)
    }

    actual fun startOngoing(taskId: Long, title: String) {
        val intent = Intent(context, OngoingNotificationService::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    actual fun stopOngoing(taskId: Long) {
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
        private const val MAX_REMINDERS_PER_TASK = 100

        fun notificationId(taskId: Long, reminderId: Int): Int =
            (taskId.toInt() * 100 + reminderId)
    }
}
