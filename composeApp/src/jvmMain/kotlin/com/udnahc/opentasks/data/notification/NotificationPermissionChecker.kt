package com.udnahc.opentasks.data.notification

actual class NotificationPermissionChecker {
    actual suspend fun isGranted(): Boolean = true
    actual fun openSettings() {}
}
