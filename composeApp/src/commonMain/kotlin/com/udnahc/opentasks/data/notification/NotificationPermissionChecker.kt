package com.udnahc.opentasks.data.notification

enum class ExactReminderPermissionStatus {
    GRANTED,
    NOT_GRANTED,
    NOT_REQUIRED,
}

expect class NotificationPermissionChecker {
    suspend fun isGranted(): Boolean
    suspend fun exactReminderStatus(): ExactReminderPermissionStatus
    fun openSettings()
    fun openExactReminderSettings()
}
