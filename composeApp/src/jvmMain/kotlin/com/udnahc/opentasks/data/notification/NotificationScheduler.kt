package com.udnahc.opentasks.data.notification

actual class NotificationScheduler {
    actual fun schedule(taskId: Long, title: String, body: String, triggerAtMillis: Long, reminderId: Int) {}
    actual fun cancel(taskId: Long, reminderId: Int) {}
    actual fun cancelAll(taskId: Long) {}
    actual fun startOngoing(taskId: Long, title: String) {}
    actual fun stopOngoing(taskId: Long) {}
}
