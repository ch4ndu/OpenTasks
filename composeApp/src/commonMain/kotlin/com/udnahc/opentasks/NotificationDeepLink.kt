package com.udnahc.opentasks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val NOTIFICATION_DEEP_LINK_EVENT_ID_KEY = "notification_event_id"
const val NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY = "notification_occurrence_deadline_utc"
const val NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY = "notification_at_utc"
const val NOTIFICATION_DEEP_LINK_SEMANTIC_KEY = "notification_semantic_key"

data class NotificationDeepLinkEvent(
    val eventId: String,
    val occurrenceDeadlineUtcMillis: Long? = null,
    val notificationAtUtcMillis: Long? = null,
    val semanticKey: String? = null,
    val deliverySequence: Long = nextNotificationDeepLinkDeliverySequence(),
)

private var notificationDeepLinkDeliverySequence = 0L

private fun nextNotificationDeepLinkDeliverySequence(): Long {
    notificationDeepLinkDeliverySequence += 1
    return notificationDeepLinkDeliverySequence
}

private val _notificationDeepLinkEvent = MutableStateFlow<NotificationDeepLinkEvent?>(null)
val notificationDeepLinkEvent: StateFlow<NotificationDeepLinkEvent?> =
    _notificationDeepLinkEvent.asStateFlow()

fun publishNotificationDeepLinkEventId(eventId: String) {
    publishNotificationDeepLinkEvent(eventId, 0L, 0L)
}

fun publishNotificationDeepLinkEvent(
    eventId: String,
    occurrenceDeadlineUtcMillis: Long,
    notificationAtUtcMillis: Long,
) {
    publishNotificationDeepLinkEvent(
        eventId = eventId,
        occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
        notificationAtUtcMillis = notificationAtUtcMillis,
        semanticKey = null,
    )
}

fun publishNotificationDeepLinkEvent(
    eventId: String,
    occurrenceDeadlineUtcMillis: Long,
    notificationAtUtcMillis: Long,
    semanticKey: String?,
) {
    if (eventId.isNotBlank()) {
        _notificationDeepLinkEvent.value = NotificationDeepLinkEvent(
            eventId = eventId,
            occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis.takeIf { it > 0L },
            notificationAtUtcMillis = notificationAtUtcMillis.takeIf { it > 0L },
            semanticKey = semanticKey,
        )
    }
}

fun clearNotificationDeepLinkEvent(event: NotificationDeepLinkEvent) {
    if (_notificationDeepLinkEvent.value == event) {
        _notificationDeepLinkEvent.value = null
    }
}
