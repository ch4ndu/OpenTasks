package com.udnahc.opentasks.data.notification

interface ReminderScheduler {
    fun schedule(taskId: String, title: String, body: String, triggerAtMillis: Long, reminderId: Int)
    fun cancel(taskId: String, reminderId: Int)
    fun cancelReminders(taskId: String)
    fun cancelAll(taskId: String)
    fun startOngoing(taskId: String, title: String)
    fun stopOngoing(taskId: String)
}

expect class NotificationScheduler : ReminderScheduler {
    override fun schedule(taskId: String, title: String, body: String, triggerAtMillis: Long, reminderId: Int)
    override fun cancel(taskId: String, reminderId: Int)
    override fun cancelReminders(taskId: String)
    override fun cancelAll(taskId: String)
    override fun startOngoing(taskId: String, title: String)
    override fun stopOngoing(taskId: String)
}
