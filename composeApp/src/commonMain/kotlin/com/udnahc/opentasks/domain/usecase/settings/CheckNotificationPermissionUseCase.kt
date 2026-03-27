package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.notification.NotificationPermissionChecker

class CheckNotificationPermissionUseCase(
    private val notificationPermissionChecker: NotificationPermissionChecker,
) {
    suspend operator fun invoke(): Boolean =
        notificationPermissionChecker.isGranted()

    fun openSettings() {
        notificationPermissionChecker.openSettings()
    }
}
