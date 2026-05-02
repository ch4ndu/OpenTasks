package com.udnahc.opentasks.data.notification

actual class NotificationPermissionChecker {
    actual suspend fun isGranted(): Boolean = true
    actual suspend fun exactReminderStatus(): ExactReminderPermissionStatus =
        ExactReminderPermissionStatus.NOT_REQUIRED
    actual fun openSettings() {}
    actual fun openExactReminderSettings() {}
}
