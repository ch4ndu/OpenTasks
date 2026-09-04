package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.ExternalLaunchResult

enum class NotificationCapability {
    SUPPORTED,
    NOT_SUPPORTED,
}

enum class ExactReminderPermissionStatus {
    GRANTED,
    NOT_GRANTED,
    NOT_REQUIRED,
}

expect class NotificationPermissionChecker {
    val capability: NotificationCapability
    suspend fun isGranted(): Boolean
    suspend fun exactReminderStatus(): ExactReminderPermissionStatus
    fun openSettings(): ExternalLaunchResult
    fun openExactReminderSettings(): ExternalLaunchResult
}
