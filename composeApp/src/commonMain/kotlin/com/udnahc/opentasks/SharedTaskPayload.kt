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
    val hasTaskContent: Boolean get() = description.isNotBlank() || url.isNotBlank()
    val hasIcsContent: Boolean get() = icsContent.isNotBlank()
}

sealed interface SharedTaskPayloadEvent {
    val id: Long

    data class Accepted(val payload: SharedTaskPayload) : SharedTaskPayloadEvent {
        override val id: Long get() = payload.id
    }

    data class Rejected(val rejection: SharedTaskPayloadRejection) : SharedTaskPayloadEvent {
        override val id: Long get() = rejection.id
    }
}

data class SharedTaskPayloadRejection(val id: Long, val reason: ExternalInputFailure)

data class SharedTaskIntakeTicket(
    val id: Long,
    val readinessGeneration: Long,
    val accountId: String,
    val boundaryEpoch: Long,
)

data class SharedTaskIntakeStatus(
    val revision: Long,
    val readinessGeneration: Long,
    val accountId: String?,
    val boundaryEpoch: Long,
    val isAppActive: Boolean,
    val isMounted: Boolean,
    val isUiBusy: Boolean,
    val activeReviewId: Long?,
    val hasProcessPayload: Boolean,
)

private const val MAX_SHARED_TASK_PAYLOAD_SLOTS = 64

private data class IntakeBoundary(
    val generation: Long,
    val accountId: String,
    val epoch: Long,
)

private sealed interface PayloadSlot {
    val id: Long
    val boundary: IntakeBoundary?

    data class Reserved(override val id: Long, override val boundary: IntakeBoundary? = null) : PayloadSlot

    data class Published(
        val event: SharedTaskPayloadEvent,
        override val boundary: IntakeBoundary? = null,
    ) : PayloadSlot {
        override val id: Long get() = event.id
    }
}

private data class ActiveReview(val id: Long, val accountId: String, val epoch: Long)

/** Uses identity equality so every CAS expectation names one exact queue revision. */
private class QueueSnapshot(
    val revision: Long,
    val generation: Long,
    val accountId: String?,
    val epoch: Long,
    val isAppActive: Boolean,
    val isMounted: Boolean,
    val isUiBusy: Boolean,
    val activeReview: ActiveReview?,
    slots: List<PayloadSlot>,
) {
    val slots = slots.toList()

    fun next(
        generation: Long = this.generation,
        accountId: String? = this.accountId,
        epoch: Long = this.epoch,
        isAppActive: Boolean = this.isAppActive,
        isMounted: Boolean = this.isMounted,
        isUiBusy: Boolean = this.isUiBusy,
        activeReview: ActiveReview? = this.activeReview,
        slots: List<PayloadSlot> = this.slots,
    ) = QueueSnapshot(
        revision + 1L,
        generation,
        accountId,
        epoch,
        isAppActive,
        isMounted,
        isUiBusy,
        activeReview,
        slots,
    )

    fun isReady() = isAppActive && isMounted && !isUiBusy && !accountId.isNullOrBlank() && epoch > 0L
    fun canScan() = isReady() && activeReview == null && slots.isEmpty()
    fun head() = (slots.firstOrNull() as? PayloadSlot.Published)?.event
    fun status() = SharedTaskIntakeStatus(
        revision,
        generation,
        accountId,
        epoch,
        isAppActive,
        isMounted,
        isUiBusy,
        activeReview?.id,
        slots.isNotEmpty(),
    )
}

private val queue = MutableStateFlow(
    QueueSnapshot(0L, 0L, null, 0L, true, false, true, null, emptyList()),
)

private val platformScanRequestRevision = MutableStateFlow(0L)

internal val sharedTaskIntakeScanRequestRevision: Long
    get() = platformScanRequestRevision.value

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private object PayloadHeadFlow : StateFlow<SharedTaskPayloadEvent?> {
    override val value get() = queue.value.head()
    override val replayCache get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<SharedTaskPayloadEvent?>): Nothing {
        var initialized = false
        var previous: SharedTaskPayloadEvent? = null
        return queue.collect { snapshot ->
            val head = snapshot.head()
            if (!initialized || head != previous) {
                initialized = true
                previous = head
                collector.emit(head)
            }
        }
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private object IntakeStatusFlow : StateFlow<SharedTaskIntakeStatus> {
    override val value get() = queue.value.status()
    override val replayCache get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<SharedTaskIntakeStatus>): Nothing =
        queue.collect { collector.emit(it.status()) }
}

val sharedTaskPayload: StateFlow<SharedTaskPayloadEvent?> = PayloadHeadFlow
val sharedTaskIntakeStatus: StateFlow<SharedTaskIntakeStatus> = IntakeStatusFlow

fun publishSharedTaskPayload(
    id: Long,
    description: String = "",
    url: String = "",
    icsContent: String = "",
    icsFileName: String = "shared.ics",
) {
    val event = SharedTaskPayload(id, description, url, icsContent, icsFileName).validatedEvent() ?: return
    appendPublished(event)
}

fun publishSharedTaskPayloadRejection(id: Long, reason: ExternalInputFailure) {
    appendPublished(rejectedEvent(id, reason))
}

fun publishSharedTaskPayloadRejectionCode(id: Long, reason: String) {
    ExternalInputFailure.fromWireValue(reason)?.let { publishSharedTaskPayloadRejection(id, it) }
}

fun reserveSharedTaskPayload(id: Long): Boolean {
    while (true) {
        val snapshot = queue.value
        if (snapshot.slots.size >= MAX_SHARED_TASK_PAYLOAD_SLOTS || snapshot.slots.any { it.id == id }) {
            return false
        }
        if (queue.compareAndSet(snapshot, snapshot.next(slots = snapshot.slots + PayloadSlot.Reserved(id)))) {
            return true
        }
    }
}

fun reserveSharedTaskIntake(id: Long): SharedTaskIntakeTicket? {
    while (true) {
        val snapshot = queue.value
        val accountId = snapshot.accountId
        if (!snapshot.canScan() || accountId.isNullOrBlank()) return null
        val boundary = IntakeBoundary(snapshot.generation, accountId, snapshot.epoch)
        if (queue.compareAndSet(snapshot, snapshot.next(slots = listOf(PayloadSlot.Reserved(id, boundary))))) {
            return SharedTaskIntakeTicket(id, boundary.generation, boundary.accountId, boundary.epoch)
        }
    }
}

fun releaseSharedTaskPayloadReservation(id: Long): Boolean = releaseReservation(id, signalScanner = true)

fun abandonSharedTaskIntakeReservation(id: Long): Boolean {
    while (true) {
        val snapshot = queue.value
        val index = snapshot.slots.indexOfFirst {
            it is PayloadSlot.Reserved && it.id == id && it.boundary != null
        }
        if (index < 0) return false
        val reserved = snapshot.slots[index] as PayloadSlot.Reserved
        val readinessChanged = reserved.boundary?.generation != snapshot.generation
        val successor = snapshot.next(slots = snapshot.slots.filterIndexed { i, _ -> i != index })
        if (queue.compareAndSet(snapshot, successor)) {
            if (readinessChanged) successor.signalIfReady()
            return true
        }
    }
}

private fun releaseReservation(
    id: Long,
    signalScanner: Boolean,
): Boolean {
    while (true) {
        val snapshot = queue.value
        val index = snapshot.slots.indexOfFirst {
            it is PayloadSlot.Reserved &&
                it.id == id
        }
        if (index < 0) return false
        val successor = snapshot.next(slots = snapshot.slots.filterIndexed { i, _ -> i != index })
        if (queue.compareAndSet(snapshot, successor)) {
            if (signalScanner) successor.signalIfReady()
            return true
        }
    }
}

fun publishReservedSharedTaskPayload(
    id: Long,
    description: String = "",
    url: String = "",
    icsContent: String = "",
    icsFileName: String = "shared.ics",
): Boolean {
    val event = SharedTaskPayload(id, description, url, icsContent, icsFileName).validatedEvent()
    if (event == null) {
        releaseSharedTaskPayloadReservation(id)
        return false
    }
    return publishReserved(id, event, null)
}

fun publishReservedSharedTaskPayloadRejectionCode(id: Long, reason: String): Boolean {
    val failure = ExternalInputFailure.fromWireValue(reason)
    if (failure == null) {
        releaseSharedTaskPayloadReservation(id)
        return false
    }
    return publishReserved(id, rejectedEvent(id, failure), null)
}

fun publishSharedTaskIntake(
    id: Long,
    readinessGeneration: Long,
    accountId: String,
    boundaryEpoch: Long,
    description: String = "",
    url: String = "",
    icsContent: String = "",
    icsFileName: String = "shared.ics",
): Boolean {
    val event = SharedTaskPayload(id, description, url, icsContent, icsFileName).validatedEvent()
        ?: return false
    return publishReserved(id, event, IntakeBoundary(readinessGeneration, accountId, boundaryEpoch))
}

fun publishSharedTaskIntakeRejectionCode(
    id: Long,
    readinessGeneration: Long,
    accountId: String,
    boundaryEpoch: Long,
    reason: String,
): Boolean {
    val failure = ExternalInputFailure.fromWireValue(reason) ?: return false
    return publishReserved(
        id,
        rejectedEvent(id, failure),
        IntakeBoundary(readinessGeneration, accountId, boundaryEpoch),
    )
}

fun updateSharedTaskIntakeReadiness(
    accountId: String,
    boundaryEpoch: Long,
    isMounted: Boolean,
    isUiBusy: Boolean,
) {
    val owner = accountId.takeIf { it.isNotBlank() }
    val epoch = boundaryEpoch.takeIf { it > 0L } ?: 0L
    while (true) {
        val snapshot = queue.value
        if (snapshot.accountId == owner && snapshot.epoch == epoch &&
            snapshot.isMounted == isMounted && snapshot.isUiBusy == isUiBusy
        ) {
            return
        }
        val boundaryChanged = snapshot.accountId != owner || snapshot.epoch != epoch
        val successor = snapshot.next(
            generation = snapshot.generation + 1L,
            accountId = owner,
            epoch = epoch,
            isMounted = isMounted,
            isUiBusy = isUiBusy,
            activeReview = if (boundaryChanged) null else snapshot.activeReview,
            slots = if (boundaryChanged) snapshot.slots.filter { it.boundary == null } else snapshot.slots,
        )
        if (queue.compareAndSet(snapshot, successor)) {
            if (!snapshot.canScan()) successor.signalIfReady()
            return
        }
    }
}

fun updateSharedTaskIntakeAppActive(isActive: Boolean) {
    while (true) {
        val snapshot = queue.value
        if (snapshot.isAppActive == isActive) return
        val successor = snapshot.next(
            generation = snapshot.generation + 1L,
            isAppActive = isActive,
        )
        if (queue.compareAndSet(snapshot, successor)) {
            if (!snapshot.canScan()) successor.signalIfReady()
            return
        }
    }
}

fun deactivateSharedTaskIntake(accountId: String, boundaryEpoch: Long) {
    while (true) {
        val snapshot = queue.value
        if (snapshot.accountId != accountId || snapshot.epoch != boundaryEpoch) return
        val successor = snapshot.next(
            generation = snapshot.generation + 1L,
            accountId = null,
            epoch = 0L,
            isMounted = false,
            isUiBusy = true,
            activeReview = null,
            slots = snapshot.slots.filter { it.boundary == null },
        )
        if (queue.compareAndSet(snapshot, successor)) return
    }
}

fun canScanSharedTaskIntake(): Boolean = queue.value.canScan()

fun claimSharedTaskPayloadForReview(id: Long, accountId: String, boundaryEpoch: Long) =
    claimForReview(id, accountId, boundaryEpoch) { it.hasTaskContent && !it.hasIcsContent }

fun claimSharedIcsPayloadForReview(id: Long, accountId: String, boundaryEpoch: Long) =
    claimForReview(id, accountId, boundaryEpoch) { it.hasIcsContent }

fun claimSharedTaskRejectionForReview(
    id: Long,
    accountId: String,
    boundaryEpoch: Long,
): SharedTaskPayloadRejection? {
    while (true) {
        val snapshot = queue.value
        if (!snapshot.isReady() || snapshot.accountId != accountId || snapshot.epoch != boundaryEpoch ||
            snapshot.activeReview != null
        ) {
            return null
        }
        val event = (snapshot.slots.firstOrNull() as? PayloadSlot.Published)?.event
            as? SharedTaskPayloadEvent.Rejected ?: return null
        if (event.id != id) return null
        val successor = snapshot.next(
            activeReview = ActiveReview(id, accountId, boundaryEpoch),
            slots = snapshot.slots.drop(1),
        )
        if (queue.compareAndSet(snapshot, successor)) return event.rejection
    }
}

fun completeSharedTaskReview(id: Long): Boolean {
    while (true) {
        val snapshot = queue.value
        if (snapshot.activeReview?.id != id) return false
        val successor = snapshot.next(activeReview = null)
        if (queue.compareAndSet(snapshot, successor)) {
            successor.signalIfReady()
            return true
        }
    }
}

/** Legacy unbound claim retained for callers that do not use native intake tickets. */
fun claimSharedIcsPayload(id: Long): SharedTaskPayload? {
    while (true) {
        val snapshot = queue.value
        val event = (snapshot.slots.firstOrNull() as? PayloadSlot.Published)?.event
            as? SharedTaskPayloadEvent.Accepted ?: return null
        if (event.id != id || !event.payload.hasIcsContent) return null
        if (queue.compareAndSet(snapshot, snapshot.next(slots = snapshot.slots.drop(1)))) {
            return event.payload
        }
    }
}

fun clearSharedTaskPayload(id: Long) {
    while (true) {
        val snapshot = queue.value
        val head = snapshot.slots.firstOrNull() as? PayloadSlot.Published ?: return
        if (head.id != id) return
        val successor = snapshot.next(slots = snapshot.slots.drop(1))
        if (queue.compareAndSet(snapshot, successor)) {
            successor.signalIfReady()
            return
        }
    }
}

private fun claimForReview(
    id: Long,
    accountId: String,
    boundaryEpoch: Long,
    accepts: (SharedTaskPayload) -> Boolean,
): SharedTaskPayload? {
    while (true) {
        val snapshot = queue.value
        if (!snapshot.isReady() || snapshot.accountId != accountId || snapshot.epoch != boundaryEpoch ||
            snapshot.activeReview != null
        ) {
            return null
        }
        val event = (snapshot.slots.firstOrNull() as? PayloadSlot.Published)?.event
            as? SharedTaskPayloadEvent.Accepted ?: return null
        if (event.id != id || !accepts(event.payload)) return null
        val successor = snapshot.next(
            activeReview = ActiveReview(id, accountId, boundaryEpoch),
            slots = snapshot.slots.drop(1),
        )
        if (queue.compareAndSet(snapshot, successor)) return event.payload
    }
}

private fun SharedTaskPayload.validatedEvent(): SharedTaskPayloadEvent? {
    if (!hasTaskContent && !hasIcsContent) return null
    val failure = ExternalInputPolicy.validateSharePayload(description, url, icsContent, icsFileName)
    return failure?.let { rejectedEvent(id, it) } ?: SharedTaskPayloadEvent.Accepted(this)
}

private fun rejectedEvent(id: Long, reason: ExternalInputFailure) =
    SharedTaskPayloadEvent.Rejected(SharedTaskPayloadRejection(id, reason))

private fun appendPublished(event: SharedTaskPayloadEvent) {
    while (true) {
        val snapshot = queue.value
        if (snapshot.slots.size >= MAX_SHARED_TASK_PAYLOAD_SLOTS || snapshot.slots.any { it.id == event.id }) return
        if (queue.compareAndSet(
                snapshot,
                snapshot.next(slots = snapshot.slots + PayloadSlot.Published(event)),
            )
        ) {
            return
        }
    }
}

private fun publishReserved(id: Long, event: SharedTaskPayloadEvent, boundary: IntakeBoundary?): Boolean {
    while (true) {
        val snapshot = queue.value
        val index = snapshot.slots.indexOfFirst { it is PayloadSlot.Reserved && it.id == id }
        if (index < 0) return false
        val reserved = snapshot.slots[index] as PayloadSlot.Reserved
        if (reserved.boundary != boundary) return false
        if (boundary != null &&
            (!snapshot.isReady() || snapshot.generation != boundary.generation ||
                snapshot.accountId != boundary.accountId || snapshot.epoch != boundary.epoch)
        ) {
            return false
        }
        val successor = snapshot.next(
            slots = snapshot.slots.mapIndexed { i, slot ->
                if (i == index) PayloadSlot.Published(event, boundary) else slot
            },
        )
        if (queue.compareAndSet(snapshot, successor)) return true
    }
}

private fun QueueSnapshot.signalIfReady() {
    if (!canScan()) return
    while (true) {
        val revision = platformScanRequestRevision.value
        if (platformScanRequestRevision.compareAndSet(revision, revision + 1L)) break
    }
    requestPlatformSharedTaskIntakeScan()
}
