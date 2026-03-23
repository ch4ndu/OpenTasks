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
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val taskId = intent?.getLongExtra(NotificationScheduler.EXTRA_TASK_ID, 0L) ?: 0L
        val title = intent?.getStringExtra(NotificationScheduler.EXTRA_TITLE) ?: "All-day task"
        val notificationId = NotificationScheduler.notificationId(taskId, 99)

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val deleteIntent = Intent(this, OngoingNotificationService::class.java).apply {
            putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
            putExtra(NotificationScheduler.EXTRA_TITLE, title)
        }
        val deletePendingIntent = PendingIntent.getService(
            this,
            notificationId + 1,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NotificationScheduler.ONGOING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("All-day task in progress")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .build()

        startForeground(notificationId, notification)
        return START_STICKY
    }
}
