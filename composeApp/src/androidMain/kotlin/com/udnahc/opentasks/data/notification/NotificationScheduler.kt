package com.udnahc.opentasks.data.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.udnahc.opentasks.MainActivity
import com.udnahc.opentasks.R
import org.lighthousegames.logging.logging

private val log = logging("NotificationScheduler")

actual class NotificationScheduler(private val context: Context) : ReminderScheduler {

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_task_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_task_reminders_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        // Ongoing channel (lower importance, no sound)
        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            context.getString(R.string.notification_channel_all_day_tasks),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_all_day_tasks_description)
        }
        manager.createNotificationChannel(ongoingChannel)
    }

    actual override fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        val notificationId = notificationId(taskId, reminderId)

        log.d { "Scheduling alarm for task=$taskId reminderId=$reminderId at $triggerAtMillis (exact=$canExact)" }

        cancel(taskId, reminderId)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(notificationId)

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
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

    actual override fun cancel(taskId: String, reminderId: Int) {
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

    actual override fun cancelReminders(taskId: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Loop 0..98 — skip 99 (ongoing foreground service, managed by stopOngoing())
        for (i in 0 until MAX_REMINDERS_PER_TASK - 1) {
            cancel(taskId, i)
            notificationManager.cancel(notificationId(taskId, i))
        }
    }

    actual override fun cancelAll(taskId: String) {
        cancelReminders(taskId)
        stopOngoing(taskId)
    }

    actual override fun startOngoing(taskId: String, title: String) {
        log.d { "Starting ongoing notification for task=$taskId" }
        logOngoingChannelState()
        showOngoingNotification(taskId, title)
    }

    private fun showOngoingNotification(taskId: String, title: String) {
        val notificationId = notificationId(taskId, ONGOING_REMINDER_ID)
        val notification = buildOngoingNotification(taskId, title, notificationId)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            log.d { "Posted ongoing notification for task=$taskId notificationId=$notificationId" }
        } catch (e: SecurityException) {
            log.e(e) { "Failed to post ongoing notification for task=$taskId" }
        }
    }

    private fun buildOngoingNotification(
        taskId: String,
        title: String,
        notificationId: Int,
    ): android.app.Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markDoneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val gotItIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_GOT_IT
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val gotItPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            gotItIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_all_day_task_in_progress))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPendingIntent)
            .addAction(0, context.getString(R.string.notification_action_mark_done), markDonePendingIntent)
            .addAction(0, context.getString(R.string.notification_action_got_it), gotItPendingIntent)
            .build()
    }

    private fun logOngoingChannelState() {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            log.d { "All-day notification channel state: notificationsEnabled=$notificationsEnabled" }
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(ONGOING_CHANNEL_ID)
        if (channel == null) {
            log.d { "All-day notification channel state: missing, notificationsEnabled=$notificationsEnabled" }
            return
        }
        log.d {
            "All-day notification channel state: importance=${channel.importance}, " +
                "blocked=${channel.importance == NotificationManager.IMPORTANCE_NONE}, " +
                "notificationsEnabled=$notificationsEnabled"
        }
    }

    actual override fun stopOngoing(taskId: String) {
        log.d { "Stopping ongoing notification for task=$taskId" }
        val notificationId = notificationId(taskId, ONGOING_REMINDER_ID)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val ONGOING_CHANNEL_ID = "all_day_tasks"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val ACTION_MARK_DONE = "com.udnahc.opentasks.ACTION_MARK_DONE"
        const val ACTION_GOT_IT = "com.udnahc.opentasks.ACTION_GOT_IT"
        private const val MAX_REMINDERS_PER_TASK = 100
        private const val ONGOING_REMINDER_ID = 99

        fun notificationId(taskId: String, reminderId: Int): Int =
            "$taskId:$reminderId".hashCode().and(0x7FFFFFFF)
    }
}
