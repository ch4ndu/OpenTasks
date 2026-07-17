package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_EVENT_ID_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_SEMANTIC_KEY
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class NotificationScheduler : ReminderScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    actual override suspend fun schedule(request: ReminderRequest) {
        val identifier = request.requestId
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
        val reminderIds = pendingReminderIds()
        if (identifier in reminderIds || reminderIds.size < IOS_PENDING_REMINDER_LIMIT) {
            addNotificationRequestAwait(notificationRequest(identifier, request))
        }
    }

    actual override suspend fun cancel(semanticKey: String) {
        val identifier = "$REMINDER_REQUEST_PREFIX$semanticKey"
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
    }

    actual override suspend fun cancelPendingReminders(eventId: String) {
        removeMatchingRequests(eventId)
    }

    actual override suspend fun cancelReminders(eventId: String) {
        removeMatchingRequests(eventId)
    }

    actual override suspend fun cancelAll(eventId: String) {
        cancelReminders(eventId)
        stopOngoing(eventId)
    }

    actual override suspend fun startOngoing(identity: ReminderIdentity, title: String) {
        // iOS has no persistent ongoing-notification equivalent. Remove a stale
        // request with this semantic identity instead of inventing one.
        cancel(identity.semanticKey)
    }

    actual override suspend fun stopOngoing(eventId: String) = Unit

    actual override suspend fun replacePendingReminders(requests: List<ReminderRequest>) {
        val pendingIds = pendingReminderIds()
        if (pendingIds.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(pendingIds)
        }
        val deliveredIds = deliveredReminderIds()
        if (deliveredIds.isNotEmpty()) {
            center.removeDeliveredNotificationsWithIdentifiers(deliveredIds)
        }

        // The callback is a barrier for the preceding removal request. Requests
        // have already been selected fairly by the common 60-request queue.
        pendingReminderIds()
        val selected = requests.take(IOS_PENDING_REMINDER_LIMIT)
        val selectedIds = selected.map(ReminderRequest::requestId)
        try {
            selected.forEach { request ->
                addNotificationRequestAwait(notificationRequest(request.requestId, request))
            }
        } catch (e: Exception) {
            center.removePendingNotificationRequestsWithIdentifiers(selectedIds)
            throw e
        }
    }

    private suspend fun pendingReminderIds(): List<String> =
        suspendCancellableCoroutine { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { pending ->
                if (continuation.isActive) {
                    continuation.resume(
                        pending.orEmpty().mapNotNull { request ->
                            (request as? UNNotificationRequest)?.identifier
                        }.filter(::isOpenTasksReminderRequestId)
                    )
                }
            }
        }

    private suspend fun deliveredReminderIds(): List<String> =
        suspendCancellableCoroutine { continuation ->
            center.getDeliveredNotificationsWithCompletionHandler { delivered ->
                if (continuation.isActive) {
                    continuation.resume(
                        delivered.orEmpty().mapNotNull { notification ->
                            (notification as? UNNotification)?.request?.identifier
                        }.filter(::isOpenTasksReminderRequestId)
                    )
                }
            }
        }

    private suspend fun addNotificationRequestAwait(request: UNNotificationRequest) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val identifier = request.identifier
            continuation.invokeOnCancellation {
                center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
            }
            center.addNotificationRequest(request) { error ->
                if (!continuation.isActive) return@addNotificationRequest
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                }
            }
        }
    }

    private fun notificationRequest(
        identifier: String,
        request: ReminderRequest,
    ): UNNotificationRequest {
        val userInfo = mutableMapOf<Any?, String>(
            NOTIFICATION_DEEP_LINK_EVENT_ID_KEY to request.eventId,
            NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY to request.triggerAtUtcMillis.toString(),
            NOTIFICATION_DEEP_LINK_SEMANTIC_KEY to request.identity.semanticKey,
            NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY to request.occurrenceUtcMillis.toString(),
        )
        val content = UNMutableNotificationContent().apply {
            setTitle(request.title)
            setBody(request.body)
            setSound(UNNotificationSound.defaultSound)
            setThreadIdentifier("opentasks_reminder_${request.eventId}")
            setUserInfo(userInfo)
        }
        // A time interval calculated from the UTC target preserves the delivery
        // instant when the device timezone changes after this request is queued.
        val nowUtcMillis = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val intervalSeconds = ((request.triggerAtUtcMillis - nowUtcMillis) / 1000.0).coerceAtLeast(1.0)
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = intervalSeconds,
            repeats = false,
        )
        return UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)
    }

    private suspend fun removeMatchingRequests(eventId: String) {
        val semanticPrefix = "$REMINDER_REQUEST_PREFIX" + semanticEventPrefix(eventId)
        val previousSemanticPrefix = "$REMINDER_REQUEST_PREFIX${eventId}_"
        val legacyPrefix = "task_${eventId}_reminder_"
        val pendingIds = pendingReminderIds().filter {
            it.startsWith(semanticPrefix) ||
                it.startsWith(previousSemanticPrefix) ||
                it.startsWith(legacyPrefix)
        }
        if (pendingIds.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(pendingIds)
        }
        val deliveredIds = deliveredReminderIds().filter {
            it.startsWith(semanticPrefix) ||
                it.startsWith(previousSemanticPrefix) ||
                it.startsWith(legacyPrefix)
        }
        if (deliveredIds.isNotEmpty()) {
            center.removeDeliveredNotificationsWithIdentifiers(deliveredIds)
        }
    }

    private fun semanticEventPrefix(eventId: String): String =
        "v1|${eventId.length}|$eventId|"
}
