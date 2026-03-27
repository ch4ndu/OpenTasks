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
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val exactAlarmsAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else true
        return notificationsEnabled && exactAlarmsAllowed
    }

    actual fun openSettings() {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val intent = if (!notificationsEnabled) {
            // Open app notification settings
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Open exact alarm settings
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
