package com.udnahc.opentasks.data.notification

expect class NotificationScheduler {
    fun schedule(taskId: Long, title: String, body: String, triggerAtMillis: Long, reminderId: Int)
    fun cancel(taskId: Long, reminderId: Int)
    fun cancelAll(taskId: Long)
    fun startOngoing(taskId: Long, title: String)
    fun stopOngoing(taskId: Long)
}
