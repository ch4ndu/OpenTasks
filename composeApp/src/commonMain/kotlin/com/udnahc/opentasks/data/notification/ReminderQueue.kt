package com.udnahc.opentasks.data.notification

const val IOS_PENDING_REMINDER_LIMIT = 60
const val REMINDER_REQUEST_PREFIX = "opentasks_reminder_"

internal fun isOpenTasksReminderRequestId(identifier: String): Boolean =
    identifier.startsWith(REMINDER_REQUEST_PREFIX) ||
        (identifier.startsWith("task_") && identifier.contains("_reminder_"))

data class ReminderRequest(
    val eventId: String,
    val title: String,
    val body: String,
    val triggerAtUtcMillis: Long,
    val reminderId: Int,
    val occurrenceUtcMillis: Long,
    val allowMarkDone: Boolean = false,
    val rescheduleAfterFire: Boolean = false,
) {
    val requestId: String
        get() = "${eventId}:${occurrenceUtcMillis}:$reminderId"
}

/**
 * Reserves one complete nearest occurrence bundle per event before filling spare
 * capacity globally by trigger time. A bundle is never partially selected in
 * the fairness pass.
 */
fun selectFairReminderQueue(
    candidates: List<ReminderRequest>,
    limit: Int = IOS_PENDING_REMINDER_LIMIT,
): List<ReminderRequest> {
    if (limit <= 0) return emptyList()
    val ordered = candidates
        .distinctBy(ReminderRequest::requestId)
        .sortedWith(compareBy(ReminderRequest::triggerAtUtcMillis, ReminderRequest::requestId))
    val nearestBundles = ordered
        .groupBy(ReminderRequest::eventId)
        .mapNotNull { (_, eventRequests) ->
            val nearestOccurrence = eventRequests.minOfOrNull(ReminderRequest::occurrenceUtcMillis)
                ?: return@mapNotNull null
            eventRequests.filter { it.occurrenceUtcMillis == nearestOccurrence }
        }
        .sortedBy { bundle -> bundle.minOf(ReminderRequest::triggerAtUtcMillis) }

    val selected = linkedMapOf<String, ReminderRequest>()
    nearestBundles.forEach { bundle ->
        if (selected.size + bundle.size <= limit) {
            bundle.forEach { selected[it.requestId] = it }
        }
    }
    ordered.forEach { request ->
        if (selected.size < limit && request.requestId !in selected) {
            selected[request.requestId] = request
        }
    }
    return selected.values
        .sortedWith(compareBy(ReminderRequest::triggerAtUtcMillis, ReminderRequest::requestId))
}
