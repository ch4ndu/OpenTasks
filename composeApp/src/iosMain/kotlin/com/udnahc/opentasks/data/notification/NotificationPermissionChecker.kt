package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.ExternalLaunchResult
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.lighthousegames.logging.logging

private val log = logging("NotificationPermissionChecker")

actual class NotificationPermissionChecker {
    actual val capability: NotificationCapability = NotificationCapability.SUPPORTED

    actual suspend fun isGranted(): Boolean = suspendCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            cont.resume(settings?.authorizationStatus == UNAuthorizationStatusAuthorized)
        }
    }

    actual suspend fun exactReminderStatus(): ExactReminderPermissionStatus =
        ExactReminderPermissionStatus.NOT_REQUIRED

    actual fun openSettings(): ExternalLaunchResult {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
            ?: return settingsLaunchFailure()
        @Suppress("DEPRECATION")
        return if (UIApplication.sharedApplication.openURL(url)) {
            ExternalLaunchResult.SUCCESS
        } else {
            settingsLaunchFailure()
        }
    }

    actual fun openExactReminderSettings(): ExternalLaunchResult = ExternalLaunchResult.SUCCESS
}

private fun settingsLaunchFailure(): ExternalLaunchResult {
    log.e { "Settings launch failed" }
    return ExternalLaunchResult.FAILURE
}
