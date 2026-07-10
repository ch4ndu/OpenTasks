package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_EVENT_ID_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class NotificationScheduler : ReminderScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    actual override fun schedule(
        taskId: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        reminderId: Int,
        occurrenceDeadlineUtcMillis: Long?,
        allowMarkDone: Boolean,
        rescheduleAfterFire: Boolean,
    ) {
        val identifier = requestId(taskId, occurrenceDeadlineUtcMillis ?: triggerAtMillis, reminderId)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))

        val userInfo = mutableMapOf<Any?, String>(
            NOTIFICATION_DEEP_LINK_EVENT_ID_KEY to taskId,
            NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY to triggerAtMillis.toString(),
        )
        occurrenceDeadlineUtcMillis?.let {
            userInfo[NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY] = it.toString()
        }

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
            setThreadIdentifier("opentasks_reminder_$taskId")
            setUserInfo(userInfo)
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

        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            val reminderIds = pending.orEmpty().mapNotNull { item ->
                (item as? UNNotificationRequest)?.identifier
            }
                .filter(::isReminderRequestId)
            if (identifier in reminderIds || reminderIds.size < IOS_PENDING_REMINDER_LIMIT) {
                center.addNotificationRequest(request, withCompletionHandler = null)
            }
        }
    }

    actual override fun cancel(taskId: String, reminderId: Int) {
        removeMatchingRequests(taskId) { identifier -> identifier.endsWith("_$reminderId") }
    }

    actual override fun cancelReminders(taskId: String) {
        removeMatchingRequests(taskId) { true }
        val legacyOngoing = legacyOngoingIds(taskId)
        center.removePendingNotificationRequestsWithIdentifiers(legacyOngoing)
        center.removeDeliveredNotificationsWithIdentifiers(legacyOngoing)
    }

    actual override fun cancelAll(taskId: String) {
        cancelReminders(taskId)
        stopOngoing(taskId)
    }

    actual override fun startOngoing(
        taskId: String,
        title: String,
        occurrenceDeadlineUtcMillis: Long?,
    ) {
        stopOngoing(taskId)
    }

    actual override fun stopOngoing(taskId: String) {
        val ids = legacyOngoingIds(taskId)
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    actual override suspend fun replacePendingReminders(requests: List<ReminderRequest>) {
        val pendingIds = suspendCoroutine<List<String>> { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { pending ->
                continuation.resume(
                    pending.orEmpty().mapNotNull { request ->
                        (request as? UNNotificationRequest)?.identifier
                    }
                        .filter(::isReminderRequestId)
                )
            }
        }
        if (pendingIds.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(pendingIds)
            center.removeDeliveredNotificationsWithIdentifiers(pendingIds)
        }
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val deliveredIds = delivered.orEmpty().mapNotNull { notification ->
                (notification as? UNNotification)?.request?.identifier
            }.filter(::isOpenTasksReminderRequestId)
            if (deliveredIds.isNotEmpty()) {
                center.removeDeliveredNotificationsWithIdentifiers(deliveredIds)
            }
        }
        requests.take(IOS_PENDING_REMINDER_LIMIT).forEach { request ->
            schedule(
                taskId = request.eventId,
                title = request.title,
                body = request.body,
                triggerAtMillis = request.triggerAtUtcMillis,
                reminderId = request.reminderId,
                occurrenceDeadlineUtcMillis = request.occurrenceUtcMillis,
                allowMarkDone = request.allowMarkDone,
                rescheduleAfterFire = false,
            )
        }
    }

    private fun requestId(taskId: String, occurrenceUtcMillis: Long, reminderId: Int): String =
        "${REMINDER_REQUEST_PREFIX}${taskId}_${occurrenceUtcMillis}_$reminderId"

    private fun removeMatchingRequests(
        taskId: String,
        predicate: (String) -> Boolean,
    ) {
        val eventPrefix = "$REMINDER_REQUEST_PREFIX${taskId}_"
        val legacyEventPrefix = "task_${taskId}_reminder_"
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            val ids = pending.orEmpty().mapNotNull { request ->
                (request as? UNNotificationRequest)?.identifier
            }
                .filter {
                    (it.startsWith(eventPrefix) || it.startsWith(legacyEventPrefix)) && predicate(it)
                }
            if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
        }
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.orEmpty().mapNotNull { notification ->
                (notification as? UNNotification)?.request?.identifier
            }
                .filter {
                    (it.startsWith(eventPrefix) || it.startsWith(legacyEventPrefix)) && predicate(it)
                }
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    private fun isReminderRequestId(identifier: String): Boolean =
        isOpenTasksReminderRequestId(identifier)

    private fun legacyOngoingIds(taskId: String): List<String> =
        (8..22 step 2).map { "task_${taskId}_ongoing_$it" }

}
