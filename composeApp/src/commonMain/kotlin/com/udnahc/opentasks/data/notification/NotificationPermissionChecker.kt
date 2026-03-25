package com.udnahc.opentasks.data.notification

expect class NotificationPermissionChecker {
    suspend fun isGranted(): Boolean
    fun openSettings()
}
