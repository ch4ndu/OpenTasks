package com.udnahc.opentasks

import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val NOTIFICATION_DEEP_LINK_EVENT_ID_KEY = "notification_event_id"
const val NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY = "notification_occurrence_deadline_utc"
const val NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY = "notification_at_utc"
const val NOTIFICATION_DEEP_LINK_SEMANTIC_KEY = "notification_semantic_key"
const val NOTIFICATION_DEEP_LINK_ACCOUNT_ID_KEY = "notification_account_id"
const val NOTIFICATION_DEEP_LINK_BOUNDARY_EPOCH_KEY = "notification_boundary_epoch"

data class NotificationDeepLinkEvent(
    val eventId: String,
    val occurrenceDeadlineUtcMillis: Long? = null,
    val notificationAtUtcMillis: Long? = null,
    val semanticKey: String? = null,
    val accountId: String? = null,
    val boundaryEpoch: Long = 0L,
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
    accountId: String? = null,
    boundaryEpoch: Long = 0L,
) {
    if (eventId.isNotBlank()) {
        _notificationDeepLinkEvent.value = NotificationDeepLinkEvent(
            eventId = eventId,
            occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis.takeIf { it > 0L },
            notificationAtUtcMillis = notificationAtUtcMillis.takeIf { it > 0L },
            semanticKey = semanticKey,
            accountId = accountId,
            boundaryEpoch = boundaryEpoch,
        )
    }
}

fun clearNotificationDeepLinkEvent(event: NotificationDeepLinkEvent) {
    _notificationDeepLinkEvent.value =
        consumeNotificationDeepLinkEvent(_notificationDeepLinkEvent.value, event)
}

fun NotificationDeepLinkEvent.matches(binding: CacheBinding): Boolean =
    accountId == binding.accountId && boundaryEpoch == binding.boundaryEpoch

/**
 * Matches the complete ownership tuple used by native delivered-notification
 * cleanup. Local event IDs are reusable across account boundaries.
 */
fun notificationOwnershipMatches(
    eventId: String?,
    accountId: String?,
    boundaryEpoch: Long,
    expectedEventId: String,
    expectedAccountId: String,
    expectedBoundaryEpoch: Long,
): Boolean =
    eventId?.isNotBlank() == true &&
        accountId?.isNotBlank() == true &&
        expectedEventId.isNotBlank() &&
        expectedAccountId.isNotBlank() &&
        boundaryEpoch > 0L &&
        expectedBoundaryEpoch > 0L &&
        eventId == expectedEventId &&
        accountId == expectedAccountId &&
        boundaryEpoch == expectedBoundaryEpoch

/** Returns a countdown ID only after the event matches the active cache binding. */
fun NotificationDeepLinkEvent.countdownIdIfMatches(binding: CacheBinding): String? =
    if (matches(binding) && eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
        eventId.removePrefix(COUNTDOWN_ID_PREFIX).takeIf { it.isNotBlank() }
    } else {
        null
    }

/** Clears only the exact event that was handled, preserving a newer replacement event. */
fun consumeNotificationDeepLinkEvent(
    current: NotificationDeepLinkEvent?,
    consumed: NotificationDeepLinkEvent,
): NotificationDeepLinkEvent? = current?.takeUnless { it == consumed }
