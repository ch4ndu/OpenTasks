package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.ExternalLaunchResult
import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.data.notification.NotificationCapability
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker

class CheckNotificationPermissionUseCase(
    private val notificationPermissionChecker: NotificationPermissionChecker,
) {
    val capability: NotificationCapability = notificationPermissionChecker.capability

    suspend operator fun invoke(): Boolean =
        notificationPermissionChecker.isGranted()

    suspend fun exactReminderStatus(): ExactReminderPermissionStatus =
        notificationPermissionChecker.exactReminderStatus()

    fun openSettings(): ExternalLaunchResult = notificationPermissionChecker.openSettings()

    fun openExactReminderSettings(): ExternalLaunchResult =
        notificationPermissionChecker.openExactReminderSettings()
}
