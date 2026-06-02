package com.udnahc.opentasks.data.notification

interface ReminderScheduler {
    fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
        occurrenceDeadlineUtcMillis: Long? = null,
        allowMarkDone: Boolean = false,
        rescheduleAfterFire: Boolean = false,
    )

    fun cancel(
        taskId: String,
        reminderId: Int
    )

    fun cancelReminders(taskId: String)
    fun cancelAll(taskId: String)
    fun startOngoing(
        taskId: String,
        title: String,
        occurrenceDeadlineUtcMillis: Long? = null,
    )

    fun stopOngoing(taskId: String)
}

expect class NotificationScheduler : ReminderScheduler {
    override fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
        occurrenceDeadlineUtcMillis: Long?,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
    )

    override fun cancel(
        taskId: String,
        reminderId: Int
    )

    override fun cancelReminders(taskId: String)
    override fun cancelAll(taskId: String)
    override fun startOngoing(
        taskId: String,
        title: String,
        occurrenceDeadlineUtcMillis: Long?,
    )

    override fun stopOngoing(taskId: String)
}
