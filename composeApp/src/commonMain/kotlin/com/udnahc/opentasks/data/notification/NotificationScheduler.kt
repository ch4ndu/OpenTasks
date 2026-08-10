package com.udnahc.opentasks.data.notification

interface ReminderScheduler {
    suspend fun schedule(request: ReminderRequest)

    suspend fun cancel(semanticKey: String)

    suspend fun cancelPendingReminders(eventId: String)
    suspend fun cancelReminders(eventId: String)
    suspend fun cancelAll(eventId: String)
    suspend fun startOngoing(
        identity: ReminderIdentity,
        title: String,
    )

    suspend fun stopOngoing(eventId: String)
    suspend fun cancelAllAccountReminders()

    suspend fun replacePendingReminders(requests: List<ReminderRequest>) {
        for (request in requests) {
            schedule(request)
        }
    }
}

expect class NotificationScheduler : ReminderScheduler {
    override suspend fun schedule(request: ReminderRequest)

    override suspend fun cancel(semanticKey: String)

    override suspend fun cancelPendingReminders(eventId: String)
    override suspend fun cancelReminders(eventId: String)
    override suspend fun cancelAll(eventId: String)
    override suspend fun startOngoing(
        identity: ReminderIdentity,
        title: String,
    )

    override suspend fun stopOngoing(eventId: String)
    override suspend fun cancelAllAccountReminders()
    override suspend fun replacePendingReminders(requests: List<ReminderRequest>)
}
