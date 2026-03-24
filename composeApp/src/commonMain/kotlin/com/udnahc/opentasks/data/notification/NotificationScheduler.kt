package com.udnahc.opentasks.data.notification

expect class NotificationScheduler {
    fun schedule(taskId: String, title: String, body: String, triggerAtMillis: Long, reminderId: Int)
    fun cancel(taskId: String, reminderId: Int)
    fun cancelReminders(taskId: String)
    fun cancelAll(taskId: String)
    fun startOngoing(taskId: String, title: String)
    fun stopOngoing(taskId: String)
}
