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
import org.lighthousegames.logging.logging

private val log = logging("NotificationScheduler")

private const val MAIN_ACTIVITY_CLASS = "com.udnahc.opentasks.MainActivity"
private const val NOTIFICATION_RECEIVER_CLASS = "com.udnahc.opentasks.data.notification.NotificationReceiver"
private const val NOTIFICATION_ACTION_RECEIVER_CLASS =
    "com.udnahc.opentasks.data.notification.NotificationActionReceiver"

actual class NotificationScheduler(private val context: Context) : ReminderScheduler {

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.appString("notification_channel_task_reminders"),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.appString("notification_channel_task_reminders_description")
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        // Ongoing channel (lower importance, no sound)
        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            context.appString("notification_channel_all_day_tasks"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.appString("notification_channel_all_day_tasks_description")
        }
        manager.createNotificationChannel(ongoingChannel)
    }

    actual override fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
        occurrenceDeadlineUtcMillis: Long?,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        val notificationId = notificationId(taskId, reminderId)

        log.d { "Scheduling alarm for task=$taskId reminderId=$reminderId at $triggerAtMillis (exact=$canExact)" }

        cancel(taskId, reminderId)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(notificationId)

        val intent = context.appComponentIntent(NOTIFICATION_RECEIVER_CLASS).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_NOTIFICATION_AT_UTC, triggerAtMillis)
            occurrenceDeadlineUtcMillis?.let { putExtra(EXTRA_OCCURRENCE_DEADLINE_UTC, it) }
            putExtra(EXTRA_ALLOW_MARK_DONE, allowMarkDone)
            putExtra(EXTRA_RESCHEDULE_AFTER_FIRE, rescheduleAfterFire)
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
        val intent = context.appComponentIntent(NOTIFICATION_RECEIVER_CLASS)
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
        for (i in REMINDER_NOTIFICATION_IDS) {
            cancel(taskId, i)
            notificationManager.cancel(notificationId(taskId, i))
        }
    }

    actual override fun cancelAll(taskId: String) {
        cancelReminders(taskId)
        stopOngoing(taskId)
    }

    actual override fun startOngoing(
        taskId: String,
        title: String,
        occurrenceDeadlineUtcMillis: Long?,
    ) {
        log.d { "Starting ongoing notification for task=$taskId" }
        logOngoingChannelState()
        showOngoingNotification(taskId, title, occurrenceDeadlineUtcMillis)
    }

    private fun showOngoingNotification(
        taskId: String,
        title: String,
        occurrenceDeadlineUtcMillis: Long?,
    ) {
        val notificationId = notificationId(taskId, ONGOING_REMINDER_ID)
        val notification = buildOngoingNotification(taskId, title, notificationId, occurrenceDeadlineUtcMillis)
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
        occurrenceDeadlineUtcMillis: Long?,
    ): android.app.Notification {
        val tapIntent = context.appComponentIntent(MAIN_ACTIVITY_CLASS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_NOTIFICATION_AT_UTC, System.currentTimeMillis())
            occurrenceDeadlineUtcMillis?.let { putExtra(EXTRA_OCCURRENCE_DEADLINE_UTC, it) }
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val gotItIntent = context.appComponentIntent(NOTIFICATION_ACTION_RECEIVER_CLASS).apply {
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
            .setSmallIcon(context.appDrawable("ic_notification"))
            .setContentTitle(title)
            .setContentText(context.appString("notification_all_day_task_in_progress"))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPendingIntent)
            .addAction(
                0,
                context.appString("notification_action_mark_done"),
                markDonePendingIntent(context, taskId, notificationId, occurrenceDeadlineUtcMillis),
            )
            .addAction(0, context.appString("notification_action_got_it"), gotItPendingIntent)
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

    actual override suspend fun replacePendingReminders(requests: List<ReminderRequest>) {
        requests.forEach { request ->
            schedule(
                taskId = request.eventId,
                title = request.title,
                body = request.body,
                triggerAtMillis = request.triggerAtUtcMillis,
                reminderId = request.reminderId,
                occurrenceDeadlineUtcMillis = request.occurrenceUtcMillis,
                allowMarkDone = request.allowMarkDone,
                rescheduleAfterFire = request.rescheduleAfterFire,
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val ONGOING_CHANNEL_ID = "all_day_tasks"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_OCCURRENCE_DEADLINE_UTC = "occurrence_deadline_utc"
        const val EXTRA_NOTIFICATION_AT_UTC = "notification_at_utc"
        const val EXTRA_ALLOW_MARK_DONE = "allow_mark_done"
        const val EXTRA_RESCHEDULE_AFTER_FIRE = "reschedule_after_fire"
        const val ACTION_MARK_DONE = "com.udnahc.opentasks.ACTION_MARK_DONE"
        const val ACTION_GOT_IT = "com.udnahc.opentasks.ACTION_GOT_IT"
        private const val ONGOING_REMINDER_ID = 99
        private val REMINDER_NOTIFICATION_IDS = 0 until ONGOING_REMINDER_ID

        fun notificationId(taskId: String, reminderId: Int): Int =
            "$taskId:$reminderId".hashCode().and(0x7FFFFFFF)

        fun markDonePendingIntent(
            context: Context,
            taskId: String,
            notificationId: Int,
            occurrenceDeadlineUtcMillis: Long?,
        ): PendingIntent {
            val intent = context.appComponentIntent(NOTIFICATION_ACTION_RECEIVER_CLASS).apply {
                action = ACTION_MARK_DONE
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                occurrenceDeadlineUtcMillis?.let { putExtra(EXTRA_OCCURRENCE_DEADLINE_UTC, it) }
            }
            return PendingIntent.getBroadcast(
                context,
                markDoneRequestCode(notificationId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun cancelDisplayedReminders(
            context: Context,
            eventId: String,
            exceptNotificationId: Int? = null,
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            REMINDER_NOTIFICATION_IDS
                .map { notificationId(eventId, it) }
                .filter { it != exceptNotificationId }
                .forEach(manager::cancel)
        }

        private fun markDoneRequestCode(notificationId: Int): Int =
            "mark_done:$notificationId".hashCode().and(0x7FFFFFFF)
    }
}

private fun Context.appComponentIntent(className: String): Intent =
    Intent().setClassName(packageName, className)

private fun Context.appString(name: String): String = getString(appResourceId("string", name))

private fun Context.appDrawable(name: String): Int = appResourceId("drawable", name)

private fun Context.appResourceId(type: String, name: String): Int =
    resources.getIdentifier(name, type, packageName)
