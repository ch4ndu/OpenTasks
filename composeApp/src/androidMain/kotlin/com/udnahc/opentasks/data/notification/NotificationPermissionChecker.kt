package com.udnahc.opentasks.data.notification

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

actual class NotificationPermissionChecker(private val context: Context) {

    actual suspend fun isGranted(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    actual suspend fun exactReminderStatus(): ExactReminderPermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ExactReminderPermissionStatus.NOT_REQUIRED
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (alarmManager.canScheduleExactAlarms()) {
            ExactReminderPermissionStatus.GRANTED
        } else {
            ExactReminderPermissionStatus.NOT_GRANTED
        }
    }

    actual fun openSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    actual fun openExactReminderSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
