package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_EVENT_ID_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_SEMANTIC_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_ACCOUNT_ID_KEY
import com.udnahc.opentasks.NOTIFICATION_DEEP_LINK_BOUNDARY_EPOCH_KEY
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val IOS_NATIVE_PENDING_REQUEST_LIMIT = 64
private const val ADD_REQUEST_FAILURE_MESSAGE = "Unable to add reminder request"
private const val REPLACEMENT_FAILURE_MESSAGE = "Unable to prepare reminder queue replacement"
private const val ROLLBACK_FAILURE_MESSAGE = "Unable to restore reminder queue"

private sealed interface DesiredRequestState {
    val generation: Long
}

private data class PendingRequestState(
    val request: UNNotificationRequest,
    override val generation: Long,
) : DesiredRequestState

private data class RemovedRequestState(
    override val generation: Long,
) : DesiredRequestState

actual class NotificationScheduler(
    private val boundaryGuard: AccountBoundaryGuard,
) : ReminderScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val mutationMutex = Mutex()
    // Platform DI owns one process-lifetime scheduler. Every identifier shares
    // this sequence, while desired state is retained only for the active transaction.
    private val desiredRequests = mutableMapOf<String, DesiredRequestState>()
    private var generationCounter = 0L

    actual override suspend fun schedule(request: ReminderRequest) = withMutationLock {
        val boundary = boundaryGuard.activeBoundary()
            ?: throw IllegalStateException("Cannot schedule a reminder without an active account boundary")
        scheduleLocked(request, boundary)
    }

    actual override suspend fun cancel(semanticKey: String) = withMutationLock {
        cancelIdentifierLocked("$REMINDER_REQUEST_PREFIX$semanticKey", removeDelivered = true)
    }

    actual override suspend fun cancelPendingReminders(eventId: String) = withMutationLock {
        cancelEventLocked(eventId, removeDelivered = false)
    }

    actual override suspend fun cancelReminders(eventId: String) = withMutationLock {
        cancelEventLocked(eventId, removeDelivered = true)
    }

    actual override suspend fun cancelAll(eventId: String) = withMutationLock {
        cancelEventLocked(eventId, removeDelivered = true)
        stopOngoingLocked()
    }

    actual override suspend fun startOngoing(identity: ReminderIdentity, title: String) = withMutationLock {
        // iOS has no persistent ongoing-notification equivalent. Remove a stale
        // request with this semantic identity instead of inventing one.
        cancelIdentifierLocked("$REMINDER_REQUEST_PREFIX${identity.semanticKey}", removeDelivered = true)
    }

    actual override suspend fun stopOngoing(eventId: String) = withMutationLock {
        stopOngoingLocked()
    }

    actual override suspend fun cancelAllAccountReminders() = withMutationLock {
        val pendingIds = pendingRequestsSnapshot()
            .map { it.identifier }
            .filter(::isOpenTasksReminderRequestId)
            .toSet()
        val deliveredIds = pendingIds + deliveredReminderIdsSnapshot()
            .filter(::isOpenTasksReminderRequestId)
            .toSet()
        val generations = stageDesiredRequests(
            pending = emptyMap(),
            removed = pendingIds + deliveredIds,
        )
        removeRequestsLocked(
            pendingIds = pendingIds,
            deliveredIds = deliveredIds,
            generations = generations,
        )
    }

    actual override suspend fun replacePendingReminders(requests: List<ReminderRequest>) = withMutationLock {
        val boundary = boundaryGuard.activeBoundary()
            ?: throw IllegalStateException("Cannot schedule reminders without an active account boundary")
        replacePendingRemindersLocked(requests, boundary)
    }

    private suspend fun scheduleLocked(
        request: ReminderRequest,
        boundary: AccountBoundary,
    ) {
        val pendingSnapshot = pendingRequestsSnapshot()
        val previousOwned = pendingSnapshot
            .filter { isOpenTasksReminderRequestId(it.identifier) }
            .associateBy { it.identifier }
        val foreignCount = pendingSnapshot.size - previousOwned.size
        val ownedCapacity = ownedCapacity(foreignCount)
        val identifier = request.requestId
        if (identifier !in previousOwned && previousOwned.size >= ownedCapacity) return

        val nativeRequest = notificationRequest(identifier, request, boundary)
        val generations = stageDesiredRequests(
            pending = mapOf(identifier to nativeRequest),
            removed = emptySet(),
        )
        val affectedIds = setOf(identifier)
        try {
            addRequestLocked(nativeRequest, generationFor(identifier, generations))
        } catch (error: CancellationException) {
            restoreAfterCancellation(
                previousOwned = previousOwned.filterKeys { it == identifier },
                affectedIds = affectedIds,
                cancellation = error,
            )
        } catch (error: Exception) {
            restoreAfterFailure(
                previousOwned = previousOwned.filterKeys { it == identifier },
                affectedIds = affectedIds,
            )
            throw error
        }
    }

    private suspend fun cancelIdentifierLocked(
        identifier: String,
        removeDelivered: Boolean,
    ) {
        val generations = stageDesiredRequests(
            pending = emptyMap(),
            removed = setOf(identifier),
        )
        removeRequestsLocked(
            pendingIds = setOf(identifier),
            deliveredIds = if (removeDelivered) setOf(identifier) else emptySet(),
            generations = generations,
        )
    }

    private suspend fun cancelEventLocked(
        eventId: String,
        removeDelivered: Boolean,
    ) {
        val pendingIds = pendingRequestsSnapshot()
            .map { it.identifier }
            .filter { isOpenTasksReminderRequestId(it) && requestMatchesEvent(it, eventId) }
            .toSet()
        val deliveredIds = if (removeDelivered) {
            pendingIds + deliveredReminderIdsSnapshot()
                .filter { isOpenTasksReminderRequestId(it) && requestMatchesEvent(it, eventId) }
                .toSet()
        } else {
            emptySet()
        }
        val generations = stageDesiredRequests(
            pending = emptyMap(),
            removed = pendingIds + deliveredIds,
        )
        removeRequestsLocked(
            pendingIds = pendingIds,
            deliveredIds = deliveredIds,
            generations = generations,
        )
    }

    private fun stopOngoingLocked() = Unit

    private suspend fun replacePendingRemindersLocked(
        requests: List<ReminderRequest>,
        boundary: AccountBoundary,
    ) {
        val pendingSnapshot = pendingRequestsSnapshot()
        val previousOwned = pendingSnapshot
            .filter { isOpenTasksReminderRequestId(it.identifier) }
            .associateBy { it.identifier }
        val foreignCount = pendingSnapshot.size - previousOwned.size
        val ownedLimit = ownedCapacity(foreignCount)
        val selected = requests.asSequence()
            .distinctBy(ReminderRequest::requestId)
            .take(ownedLimit)
            .toList()
        val desired = selected.associate { request ->
            request.requestId to notificationRequest(request.requestId, request, boundary)
        }
        val obsoleteIds = previousOwned.keys - desired.keys
        val generations = stageDesiredRequests(
            pending = desired,
            removed = obsoleteIds,
        )
        val affectedIds = previousOwned.keys + desired.keys

        try {
            applyReplacementLocked(
                pendingSnapshotSize = pendingSnapshot.size,
                ownedLimit = ownedLimit,
                previousOwned = previousOwned,
                desired = desired,
                obsoleteIds = obsoleteIds,
                generations = generations,
            )
        } catch (error: CancellationException) {
            restoreAfterCancellation(previousOwned, affectedIds, error)
        } catch (error: Exception) {
            restoreAfterFailure(previousOwned, affectedIds)
            throw error
        }
    }

    private suspend fun applyReplacementLocked(
        pendingSnapshotSize: Int,
        ownedLimit: Int,
        previousOwned: Map<String, UNNotificationRequest>,
        desired: Map<String, UNNotificationRequest>,
        obsoleteIds: Set<String>,
        generations: Map<String, Long>,
    ) {
        desired.values
            .filter { it.identifier in previousOwned }
            .forEach { request ->
                addRequestLocked(request, generationFor(request.identifier, generations))
        }

        val newRequests = desired.values.filter { it.identifier !in previousOwned }
        val availableSlots = minOf(
            (IOS_NATIVE_PENDING_REQUEST_LIMIT - pendingSnapshotSize).coerceAtLeast(0),
            (ownedLimit - previousOwned.size).coerceAtLeast(0),
        )
        val requestsWithoutEviction = newRequests.take(availableSlots)
        requestsWithoutEviction.forEach { request ->
            addRequestLocked(request, generationFor(request.identifier, generations))
        }

        val requestsNeedingEviction = newRequests.drop(requestsWithoutEviction.size)
        val evictedIds = obsoleteIds.take(requestsNeedingEviction.size).toSet()
        if (evictedIds.size != requestsNeedingEviction.size) {
            throw IllegalStateException(REPLACEMENT_FAILURE_MESSAGE)
        }
        removeRequestsLocked(
            pendingIds = evictedIds,
            deliveredIds = emptySet(),
            generations = generations,
        )
        requestsNeedingEviction.forEach { request ->
            addRequestLocked(request, generationFor(request.identifier, generations))
        }

        removeRequestsLocked(
            pendingIds = obsoleteIds - evictedIds,
            deliveredIds = emptySet(),
            generations = generations,
        )
    }

    private suspend fun restoreAfterCancellation(
        previousOwned: Map<String, UNNotificationRequest>,
        affectedIds: Set<String>,
        cancellation: CancellationException,
    ): Nothing {
        val rollbackFailed = withContext(NonCancellable) {
            try {
                restoreOwnedSnapshotLocked(previousOwned, affectedIds)
                false
            } catch (_: Exception) {
                true
            }
        }
        if (rollbackFailed) {
            cancellation.addSuppressed(IllegalStateException(ROLLBACK_FAILURE_MESSAGE))
        }
        throw cancellation
    }

    private suspend fun restoreAfterFailure(
        previousOwned: Map<String, UNNotificationRequest>,
        affectedIds: Set<String>,
    ) {
        try {
            withContext(NonCancellable) {
                restoreOwnedSnapshotLocked(previousOwned, affectedIds)
            }
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException(ROLLBACK_FAILURE_MESSAGE)
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun restoreOwnedSnapshotLocked(
        previousOwned: Map<String, UNNotificationRequest>,
        affectedIds: Set<String>,
    ) {
        val introducedIds = affectedIds - previousOwned.keys
        val generations = stageDesiredRequests(
            pending = previousOwned,
            removed = introducedIds,
        )
        removeRequestsLocked(
            pendingIds = introducedIds,
            deliveredIds = emptySet(),
            generations = generations,
        )
        var restoreFailed = false
        previousOwned.forEach { (identifier, request) ->
            try {
                addRequestLocked(request, generationFor(identifier, generations))
            } catch (_: Exception) {
                restoreFailed = true
            }
        }
        if (restoreFailed) {
            throw IllegalStateException(ROLLBACK_FAILURE_MESSAGE)
        }
    }

    private suspend fun addRequestLocked(
        request: UNNotificationRequest,
        generation: Long,
    ) {
        currentCoroutineContext().ensureActive()
        if (!isCurrentPending(request, generation)) return
        // Resolve the native callback before observing coroutine cancellation so
        // compensation completes while the scheduler mutex is still held.
        val succeeded = suspendCoroutine<Boolean> { continuation ->
            center.addNotificationRequest(request) { error ->
                continuation.resume(error == null)
            }
        }
        val remainsCurrent = isCurrentPending(request, generation)
        currentCoroutineContext().ensureActive()
        if (!remainsCurrent) return
        if (!succeeded) throw IllegalStateException(ADD_REQUEST_FAILURE_MESSAGE)
    }

    private suspend fun removeRequestsLocked(
        pendingIds: Set<String>,
        deliveredIds: Set<String>,
        generations: Map<String, Long>,
    ) {
        currentCoroutineContext().ensureActive()
        val currentPendingIds = pendingIds.filter { identifier ->
            generations[identifier]?.let { generation ->
                isCurrentRemoved(identifier, generation)
            } == true
        }
        val currentDeliveredIds = deliveredIds.filter { identifier ->
            generations[identifier]?.let { generation ->
                isCurrentRemoved(identifier, generation)
            } == true
        }
        if (currentPendingIds.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(currentPendingIds)
        }
        if (currentDeliveredIds.isNotEmpty()) {
            center.removeDeliveredNotificationsWithIdentifiers(currentDeliveredIds)
        }
        // Fresh native snapshots are ordering barriers for the fire-and-forget removals.
        if (currentPendingIds.isNotEmpty()) {
            pendingRequestsBarrier()
        }
        if (currentDeliveredIds.isNotEmpty()) {
            deliveredReminderIdsBarrier()
        }
        currentCoroutineContext().ensureActive()
    }

    private fun stageDesiredRequests(
        pending: Map<String, UNNotificationRequest>,
        removed: Set<String>,
    ): Map<String, Long> {
        val identifiers = linkedSetOf<String>().apply {
            addAll(pending.keys)
            addAll(removed)
        }
        return identifiers.associateWith { identifier ->
            val generation = nextGeneration()
            val request = pending[identifier]
            desiredRequests[identifier] = if (request == null) {
                RemovedRequestState(generation)
            } else {
                PendingRequestState(request, generation)
            }
            generation
        }
    }

    private fun nextGeneration(): Long {
        generationCounter += 1L
        return generationCounter
    }

    private fun generationFor(identifier: String, generations: Map<String, Long>): Long =
        generations[identifier]
            ?: throw IllegalStateException("Reminder mutation state unavailable")

    private fun isCurrentPending(request: UNNotificationRequest, generation: Long): Boolean {
        val desired = desiredRequests[request.identifier] as? PendingRequestState
        return desired?.generation == generation && desired.request === request
    }

    private fun isCurrentRemoved(identifier: String, generation: Long): Boolean =
        (desiredRequests[identifier] as? RemovedRequestState)?.generation == generation

    private fun ownedCapacity(foreignCount: Int): Int =
        minOf(
            IOS_PENDING_REMINDER_LIMIT,
            (IOS_NATIVE_PENDING_REQUEST_LIMIT - foreignCount).coerceAtLeast(0),
        )

    private suspend fun pendingRequestsSnapshot(): List<UNNotificationRequest> {
        val requests = pendingRequestsBarrier()
        currentCoroutineContext().ensureActive()
        return requests
    }

    private suspend fun pendingRequestsBarrier(): List<UNNotificationRequest> =
        suspendCoroutine { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { pending ->
                continuation.resume(
                    pending.orEmpty().mapNotNull { request ->
                        request as? UNNotificationRequest
                    }
                )
            }
        }

    private suspend fun deliveredReminderIdsSnapshot(): List<String> {
        val identifiers = deliveredReminderIdsBarrier()
        currentCoroutineContext().ensureActive()
        return identifiers
    }

    private suspend fun deliveredReminderIdsBarrier(): List<String> =
        suspendCoroutine { continuation ->
            center.getDeliveredNotificationsWithCompletionHandler { delivered ->
                continuation.resume(
                    delivered.orEmpty().mapNotNull { notification ->
                        (notification as? UNNotification)?.request?.identifier
                    }
                )
            }
        }

    private suspend fun <T> withMutationLock(block: suspend () -> T): T =
        mutationMutex.withLock {
            try {
                block()
            } finally {
                desiredRequests.clear()
            }
        }

    private fun notificationRequest(
        identifier: String,
        request: ReminderRequest,
        boundary: AccountBoundary,
    ): UNNotificationRequest {
        val userInfo = mutableMapOf<Any?, String>(
            NOTIFICATION_DEEP_LINK_EVENT_ID_KEY to request.eventId,
            NOTIFICATION_DEEP_LINK_NOTIFICATION_AT_UTC_KEY to request.triggerAtUtcMillis.toString(),
            NOTIFICATION_DEEP_LINK_SEMANTIC_KEY to request.identity.semanticKey,
            NOTIFICATION_DEEP_LINK_OCCURRENCE_DEADLINE_UTC_KEY to request.occurrenceUtcMillis.toString(),
            NOTIFICATION_DEEP_LINK_ACCOUNT_ID_KEY to boundary.accountId,
            NOTIFICATION_DEEP_LINK_BOUNDARY_EPOCH_KEY to boundary.boundaryEpoch.toString(),
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

    private fun requestMatchesEvent(identifier: String, eventId: String): Boolean {
        val semanticPrefix = "$REMINDER_REQUEST_PREFIX" + semanticEventPrefix(eventId)
        val previousSemanticPrefix = "$REMINDER_REQUEST_PREFIX${eventId}_"
        val legacyPrefix = "task_${eventId}_reminder_"
        return identifier.startsWith(semanticPrefix) ||
            identifier.startsWith(previousSemanticPrefix) ||
            identifier.startsWith(legacyPrefix)
    }

    private fun semanticEventPrefix(eventId: String): String =
        "v1|${eventId.length}|$eventId|"
}
