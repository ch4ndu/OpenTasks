package com.udnahc.opentasks.data.notification

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.udnahc.opentasks.MainActivity
import com.udnahc.opentasks.R

class OngoingNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == NotificationScheduler.ACTION_STOP_ONGOING) {
            // Must call startForeground before stopping to satisfy the contract
            // in case this service was started via startForegroundService()
            val placeholder = NotificationCompat.Builder(this, NotificationScheduler.ONGOING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_stopping))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(1, placeholder)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val taskId = intent?.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: ""
        val title = intent?.getStringExtra(NotificationScheduler.EXTRA_TITLE)
            ?: getString(R.string.notification_all_day_task)
        val notificationId = NotificationScheduler.notificationId(taskId, 99)

        // Tap → open app and navigate to task details
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Completion action
        val markDoneIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationScheduler.ACTION_MARK_DONE
            putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId + 1,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Dismiss action
        val gotItIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationScheduler.ACTION_GOT_IT
            putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
        }
        val gotItPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId + 2,
            gotItIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NotificationScheduler.ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.notification_all_day_task_in_progress))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPendingIntent)
            .addAction(0, getString(R.string.notification_action_mark_done), markDonePendingIntent)
            .addAction(0, getString(R.string.notification_action_got_it), gotItPendingIntent)
            .build()

        startForeground(notificationId, notification)
        return START_STICKY
    }
}
