package com.udnahc.opentasks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val NOTIFICATION_DEEP_LINK_EVENT_ID_KEY = "notification_event_id"

private val _notificationDeepLinkEventId = MutableStateFlow<String?>(null)
val notificationDeepLinkEventId: StateFlow<String?> = _notificationDeepLinkEventId.asStateFlow()

fun publishNotificationDeepLinkEventId(eventId: String) {
    if (eventId.isNotBlank()) {
        _notificationDeepLinkEventId.value = eventId
    }
}

fun clearNotificationDeepLinkEventId(eventId: String) {
    if (_notificationDeepLinkEventId.value == eventId) {
        _notificationDeepLinkEventId.value = null
    }
}
