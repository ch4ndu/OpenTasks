package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker

class CheckNotificationPermissionUseCase(
    private val notificationPermissionChecker: NotificationPermissionChecker,
) {
    suspend operator fun invoke(): Boolean =
        notificationPermissionChecker.isGranted()

    suspend fun exactReminderStatus(): ExactReminderPermissionStatus =
        notificationPermissionChecker.exactReminderStatus()

    fun openSettings() {
        notificationPermissionChecker.openSettings()
    }

    fun openExactReminderSettings() {
        notificationPermissionChecker.openExactReminderSettings()
    }
}
