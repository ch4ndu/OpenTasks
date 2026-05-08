package com.udnahc.opentasks.data.notification

actual class NotificationScheduler : ReminderScheduler {
    actual override fun schedule(taskId: String, title: String, body: String, triggerAtMillis: Long, reminderId: Int) {}
    actual override fun cancel(taskId: String, reminderId: Int) {}
    actual override fun cancelReminders(taskId: String) {}
    actual override fun cancelAll(taskId: String) {}
    actual override fun startOngoing(taskId: String, title: String) {}
    actual override fun stopOngoing(taskId: String) {}
}
