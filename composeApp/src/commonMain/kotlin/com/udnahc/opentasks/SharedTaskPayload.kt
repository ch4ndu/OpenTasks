package com.udnahc.opentasks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SharedTaskPayload(
    val id: Long,
    val description: String = "",
    val url: String = "",
    val icsContent: String = "",
    val icsFileName: String = "shared.ics",
) {
    val hasTaskContent: Boolean
        get() = description.isNotBlank() || url.isNotBlank()

    val hasIcsContent: Boolean
        get() = icsContent.isNotBlank()
}

sealed interface SharedTaskPayloadEvent {
    val id: Long

    data class Accepted(
        val payload: SharedTaskPayload,
    ) : SharedTaskPayloadEvent {
        override val id: Long
            get() = payload.id
    }

    data class Rejected(
        val rejection: SharedTaskPayloadRejection,
    ) : SharedTaskPayloadEvent {
        override val id: Long
            get() = rejection.id
    }
}

data class SharedTaskPayloadRejection(
    val id: Long,
    val reason: ExternalInputFailure,
)

private val _sharedTaskPayloadEvent = MutableStateFlow<SharedTaskPayloadEvent?>(null)
val sharedTaskPayload: StateFlow<SharedTaskPayloadEvent?> = _sharedTaskPayloadEvent.asStateFlow()

fun publishSharedTaskPayload(
    id: Long,
    description: String = "",
    url: String = "",
    icsContent: String = "",
    icsFileName: String = "shared.ics",
) {
    val payload = SharedTaskPayload(
        id = id,
        description = description,
        url = url,
        icsContent = icsContent,
        icsFileName = icsFileName,
    )
    if (!payload.hasTaskContent && !payload.hasIcsContent) return
    val failure = ExternalInputPolicy.validateSharePayload(
        description = payload.description,
        url = payload.url,
        icsContent = payload.icsContent,
        icsFileName = payload.icsFileName,
    )
    if (failure != null) {
        publishSharedTaskPayloadRejection(id, failure)
        return
    }
    _sharedTaskPayloadEvent.value = SharedTaskPayloadEvent.Accepted(payload)
}

fun publishSharedTaskPayloadRejection(
    id: Long,
    reason: ExternalInputFailure,
) {
    _sharedTaskPayloadEvent.value = SharedTaskPayloadEvent.Rejected(
        SharedTaskPayloadRejection(id = id, reason = reason),
    )
}

fun publishSharedTaskPayloadRejectionCode(
    id: Long,
    reason: String,
) {
    val failure = ExternalInputFailure.fromWireValue(reason) ?: return
    publishSharedTaskPayloadRejection(id, failure)
}

/**
 * Atomically claims an accepted ICS payload for the currently mounted account
 * epoch. A claimed payload is retired from this process-global handoff and is
 * never replayed into a later account epoch.
 */
fun claimSharedIcsPayload(id: Long): SharedTaskPayload? {
    val event = _sharedTaskPayloadEvent.value as? SharedTaskPayloadEvent.Accepted
        ?: return null
    if (event.id != id || !event.payload.hasIcsContent) return null
    return if (_sharedTaskPayloadEvent.compareAndSet(event, null)) {
        event.payload
    } else {
        null
    }
}

fun clearSharedTaskPayload(id: Long) {
    while (true) {
        val event = _sharedTaskPayloadEvent.value ?: return
        if (event.id != id || !_sharedTaskPayloadEvent.compareAndSet(event, null)) {
            return
        }
        return
    }
}
