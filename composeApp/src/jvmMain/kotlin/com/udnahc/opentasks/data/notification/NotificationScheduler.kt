package com.udnahc.opentasks.data.notification

actual class NotificationScheduler {
    actual fun schedule(taskId: String, title: String, body: String, triggerAtMillis: Long, reminderId: Int) {}
    actual fun cancel(taskId: String, reminderId: Int) {}
    actual fun cancelReminders(taskId: String) {}
    actual fun cancelAll(taskId: String) {}
    actual fun startOngoing(taskId: String, title: String) {}
    actual fun stopOngoing(taskId: String) {}
}
