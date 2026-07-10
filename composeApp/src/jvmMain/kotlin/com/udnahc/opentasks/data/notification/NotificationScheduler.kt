package com.udnahc.opentasks.data.notification

actual class NotificationScheduler : ReminderScheduler {
    actual override fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
        occurrenceDeadlineUtcMillis: Long?,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
    ) {}
    actual override fun cancel(taskId: String, reminderId: Int) {}
    actual override fun cancelReminders(taskId: String) {}
    actual override fun cancelAll(taskId: String) {}
    actual override fun startOngoing(
        taskId: String,
        title: String,
        occurrenceDeadlineUtcMillis: Long?,
    ) {}
    actual override fun stopOngoing(taskId: String) {}
    actual override suspend fun replacePendingReminders(requests: List<ReminderRequest>) {
        requests.forEach { request ->
            schedule(
                request.eventId,
                request.title,
                request.body,
                request.triggerAtUtcMillis,
                request.reminderId,
                request.occurrenceUtcMillis,
                request.allowMarkDone,
                request.rescheduleAfterFire,
            )
        }
    }
}
