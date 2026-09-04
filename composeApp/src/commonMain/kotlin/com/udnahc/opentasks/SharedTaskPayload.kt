package com.udnahc.opentasks

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

private const val MAX_SHARED_TASK_PAYLOAD_SLOTS = 64

private sealed interface SharedTaskPayloadSlot {
    val id: Long

    data class Reserved(override val id: Long) : SharedTaskPayloadSlot

    data class Published(
        val event: SharedTaskPayloadEvent,
    ) : SharedTaskPayloadSlot {
        override val id: Long
            get() = event.id
    }
}

/** Uses identity equality so every CAS expectation names one exact queue revision. */
private class SharedTaskPayloadQueueSnapshot(
    val revision: Long,
    slots: List<SharedTaskPayloadSlot>,
) {
    val slots: List<SharedTaskPayloadSlot> = slots.toList()

    fun successor(slots: List<SharedTaskPayloadSlot>): SharedTaskPayloadQueueSnapshot =
        SharedTaskPayloadQueueSnapshot(revision = revision + 1L, slots = slots)
}

private val sharedTaskPayloadQueue = MutableStateFlow(
    SharedTaskPayloadQueueSnapshot(revision = 0L, slots = emptyList()),
)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private object SharedTaskPayloadHeadStateFlow : StateFlow<SharedTaskPayloadEvent?> {
    override val value: SharedTaskPayloadEvent?
        get() = sharedTaskPayloadQueue.value.publishedHeadOrNull()

    override val replayCache: List<SharedTaskPayloadEvent?>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<SharedTaskPayloadEvent?>): Nothing {
        var initialized = false
        var previous: SharedTaskPayloadEvent? = null
        return sharedTaskPayloadQueue.collect(
            object : FlowCollector<SharedTaskPayloadQueueSnapshot> {
                override suspend fun emit(value: SharedTaskPayloadQueueSnapshot) {
                    val head = value.publishedHeadOrNull()
                    if (!initialized || head != previous) {
                        initialized = true
                        previous = head
                        collector.emit(head)
                    }
                }
            }
        )
    }
}

val sharedTaskPayload: StateFlow<SharedTaskPayloadEvent?> = SharedTaskPayloadHeadStateFlow

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
    val event = payload.validatedEventOrNull() ?: return
    appendPublished(event)
}

fun publishSharedTaskPayloadRejection(
    id: Long,
    reason: ExternalInputFailure,
) {
    appendPublished(rejectedEvent(id, reason))
}

fun publishSharedTaskPayloadRejectionCode(
    id: Long,
    reason: String,
) {
    val failure = ExternalInputFailure.fromWireValue(reason) ?: return
    publishSharedTaskPayloadRejection(id, failure)
}

fun reserveSharedTaskPayload(id: Long): Boolean {
    while (true) {
        val snapshot = sharedTaskPayloadQueue.value
        if (snapshot.slots.size >= MAX_SHARED_TASK_PAYLOAD_SLOTS ||
            snapshot.slots.any { it.id == id }
        ) {
            return false
        }
        val successor = snapshot.successor(snapshot.slots + SharedTaskPayloadSlot.Reserved(id))
        if (sharedTaskPayloadQueue.compareAndSet(snapshot, successor)) return true
    }
}

fun releaseSharedTaskPayloadReservation(id: Long): Boolean {
    while (true) {
        val snapshot = sharedTaskPayloadQueue.value
        val index = snapshot.slots.indexOfFirst { it is SharedTaskPayloadSlot.Reserved && it.id == id }
        if (index < 0) return false
        val successor = snapshot.successor(snapshot.slots.filterIndexed { slotIndex, _ -> slotIndex != index })
        if (sharedTaskPayloadQueue.compareAndSet(snapshot, successor)) return true
    }
}

fun publishReservedSharedTaskPayload(
    id: Long,
    description: String = "",
    url: String = "",
    icsContent: String = "",
    icsFileName: String = "shared.ics",
): Boolean {
    val payload = SharedTaskPayload(
        id = id,
        description = description,
        url = url,
        icsContent = icsContent,
        icsFileName = icsFileName,
    )
    val event = payload.validatedEventOrNull()
    if (event == null) {
        releaseSharedTaskPayloadReservation(id)
        return false
    }
    return publishReserved(id, event)
}

fun publishReservedSharedTaskPayloadRejectionCode(
    id: Long,
    reason: String,
): Boolean {
    val failure = ExternalInputFailure.fromWireValue(reason)
    if (failure == null) {
        releaseSharedTaskPayloadReservation(id)
        return false
    }
    return publishReserved(id, rejectedEvent(id, failure))
}

/**
 * Atomically claims an accepted ICS payload for the currently mounted account
 * epoch. A claimed payload is retired from this process-global handoff and is
 * never replayed into a later account epoch.
 */
fun claimSharedIcsPayload(id: Long): SharedTaskPayload? {
    while (true) {
        val snapshot = sharedTaskPayloadQueue.value
        val published = snapshot.slots.firstOrNull() as? SharedTaskPayloadSlot.Published
            ?: return null
        val event = published.event as? SharedTaskPayloadEvent.Accepted ?: return null
        if (event.id != id || !event.payload.hasIcsContent) return null
        val successor = snapshot.successor(snapshot.slots.drop(1))
        if (sharedTaskPayloadQueue.compareAndSet(snapshot, successor)) return event.payload
    }
}

fun clearSharedTaskPayload(id: Long) {
    while (true) {
        val snapshot = sharedTaskPayloadQueue.value
        val published = snapshot.slots.firstOrNull() as? SharedTaskPayloadSlot.Published ?: return
        if (published.id != id) return
        val successor = snapshot.successor(snapshot.slots.drop(1))
        if (sharedTaskPayloadQueue.compareAndSet(snapshot, successor)) return
    }
}

private fun SharedTaskPayload.validatedEventOrNull(): SharedTaskPayloadEvent? {
    if (!hasTaskContent && !hasIcsContent) return null
    val failure = ExternalInputPolicy.validateSharePayload(
        description = description,
        url = url,
        icsContent = icsContent,
        icsFileName = icsFileName,
    )
    return if (failure == null) {
        SharedTaskPayloadEvent.Accepted(this)
    } else {
        rejectedEvent(id, failure)
    }
}

private fun rejectedEvent(id: Long, reason: ExternalInputFailure): SharedTaskPayloadEvent =
    SharedTaskPayloadEvent.Rejected(SharedTaskPayloadRejection(id = id, reason = reason))

private fun appendPublished(event: SharedTaskPayloadEvent) {
    while (true) {
        val snapshot = sharedTaskPayloadQueue.value
        if (snapshot.slots.size >= MAX_SHARED_TASK_PAYLOAD_SLOTS ||
            snapshot.slots.any { it.id == event.id }
        ) {
            return
        }
        val successor = snapshot.successor(snapshot.slots + SharedTaskPayloadSlot.Published(event))
        if (sharedTaskPayloadQueue.compareAndSet(snapshot, successor)) return
    }
}

private fun publishReserved(id: Long, event: SharedTaskPayloadEvent): Boolean {
    while (true) {
        val snapshot = sharedTaskPayloadQueue.value
        val index = snapshot.slots.indexOfFirst { it is SharedTaskPayloadSlot.Reserved && it.id == id }
        if (index < 0) return false
        val successor = snapshot.successor(
            snapshot.slots.mapIndexed { slotIndex, slot ->
                if (slotIndex == index) SharedTaskPayloadSlot.Published(event) else slot
            }
        )
        if (sharedTaskPayloadQueue.compareAndSet(snapshot, successor)) return true
    }
}

private fun SharedTaskPayloadQueueSnapshot.publishedHeadOrNull(): SharedTaskPayloadEvent? =
    (slots.firstOrNull() as? SharedTaskPayloadSlot.Published)?.event
