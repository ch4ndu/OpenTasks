package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.ExternalLaunchResult
import org.lighthousegames.logging.logging

private val log = logging("NotificationPermissionChecker")

actual class NotificationPermissionChecker {
    actual val capability: NotificationCapability = NotificationCapability.NOT_SUPPORTED
    actual suspend fun isGranted(): Boolean = true
    actual suspend fun exactReminderStatus(): ExactReminderPermissionStatus =
        ExactReminderPermissionStatus.NOT_REQUIRED
    actual fun openSettings(): ExternalLaunchResult = settingsLaunchFailure()
    actual fun openExactReminderSettings(): ExternalLaunchResult = settingsLaunchFailure()
}

private fun settingsLaunchFailure(): ExternalLaunchResult {
    log.e { "Settings launch unsupported" }
    return ExternalLaunchResult.FAILURE
}
