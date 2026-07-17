package com.udnahc.opentasks.data.notification

actual class NotificationScheduler : ReminderScheduler {
    actual override suspend fun schedule(request: ReminderRequest) = Unit
    actual override suspend fun cancel(semanticKey: String) = Unit
    actual override suspend fun cancelPendingReminders(eventId: String) = Unit
    actual override suspend fun cancelReminders(eventId: String) = Unit
    actual override suspend fun cancelAll(eventId: String) = Unit
    actual override suspend fun startOngoing(identity: ReminderIdentity, title: String) = Unit
    actual override suspend fun stopOngoing(eventId: String) = Unit
    actual override suspend fun replacePendingReminders(requests: List<ReminderRequest>) = Unit
}
