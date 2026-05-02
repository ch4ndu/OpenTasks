package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_EVENT_ID_KEY
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970

actual class NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    actual fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
    ) {
        val identifier = requestId(taskId, reminderId)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(mapOf(NOTIFICATION_DEEP_LINK_EVENT_ID_KEY to taskId))
        }

        val triggerDate = NSDate.dateWithTimeIntervalSince1970(triggerAtMillis / 1000.0)
        val calendar = NSCalendar.currentCalendar
        val components = calendar.components(
            NSCalendar.currentCalendar.let {
                platform.Foundation.NSCalendarUnitYear or
                    platform.Foundation.NSCalendarUnitMonth or
                    platform.Foundation.NSCalendarUnitDay or
                    platform.Foundation.NSCalendarUnitHour or
                    platform.Foundation.NSCalendarUnitMinute or
                    platform.Foundation.NSCalendarUnitSecond
            },
            fromDate = triggerDate,
        )

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = components,
            repeats = false,
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = trigger,
        )

        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    actual fun cancel(taskId: String, reminderId: Int) {
        center.removePendingNotificationRequestsWithIdentifiers(
            listOf(requestId(taskId, reminderId))
        )
    }

    actual fun cancelReminders(taskId: String) {
        val ids = (0 until 100).map { requestId(taskId, it) } + legacyOngoingIds(taskId)
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    actual fun cancelAll(taskId: String) {
        cancelReminders(taskId)
        stopOngoing(taskId)
    }

    actual fun startOngoing(taskId: String, title: String) {
        stopOngoing(taskId)
    }

    actual fun stopOngoing(taskId: String) {
        val ids = legacyOngoingIds(taskId)
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    private fun requestId(taskId: String, reminderId: Int): String =
        "task_${taskId}_reminder_$reminderId"

    private fun legacyOngoingIds(taskId: String): List<String> =
        (8..22 step 2).map { "task_${taskId}_ongoing_$it" }
}
