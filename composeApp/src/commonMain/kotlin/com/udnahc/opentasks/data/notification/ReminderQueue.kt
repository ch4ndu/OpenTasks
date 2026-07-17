package com.udnahc.opentasks.data.notification

const val IOS_PENDING_REMINDER_LIMIT = 60
const val REMINDER_REQUEST_PREFIX = "opentasks_reminder_"

internal fun isOpenTasksReminderRequestId(identifier: String): Boolean =
    identifier.startsWith(REMINDER_REQUEST_PREFIX) ||
        (identifier.startsWith("task_") && identifier.contains("_reminder_"))

enum class ReminderKind {
    DATE,
    DURATION,
    OVERDUE,
    COUNTDOWN,
    ONGOING,
}

/**
 * Stable identity for every reminder delivery. The event length keeps the key
 * unambiguous even if an event identifier itself contains the separator.
 */
data class ReminderIdentity(
    val eventId: String,
    val occurrenceUtcMillis: Long,
    val kind: ReminderKind,
    val ordinal: Int,
) {
    val semanticKey: String = buildString {
        append("v1|")
        append(eventId.length)
        append('|')
        append(eventId)
        append('|')
        append(occurrenceUtcMillis)
        append('|')
        append(kind.name)
        append('|')
        append(ordinal)
    }

    companion object {
        fun fromSemanticKey(key: String): ReminderIdentity? {
            if (!key.startsWith("v1|")) return null
            val lengthEnd = key.indexOf('|', startIndex = 3)
            val eventLength = key.substring(3, lengthEnd.coerceAtLeast(3)).toIntOrNull() ?: return null
            val eventStart = lengthEnd + 1
            val eventEnd = eventStart + eventLength
            if (lengthEnd < 3 || eventEnd >= key.length || key.getOrNull(eventEnd) != '|') return null
            val eventId = key.substring(eventStart, eventEnd)
            val fields = key.substring(eventEnd + 1).split('|')
            if (fields.size != 3) return null
            val occurrence = fields[0].toLongOrNull() ?: return null
            val kind = ReminderKind.entries.firstOrNull { it.name == fields[1] } ?: return null
            val ordinal = fields[2].toIntOrNull() ?: return null
            return ReminderIdentity(eventId, occurrence, kind, ordinal)
        }
    }
}

fun reminderPendingIntentIdentity(semanticKey: String, role: String): String =
    "$role:$semanticKey"

data class ReminderRequest(
    val identity: ReminderIdentity,
    val title: String,
    val body: String,
    val triggerAtUtcMillis: Long,
    val allowMarkDone: Boolean = false,
    val rescheduleAfterFire: Boolean = false,
) {
    val eventId: String get() = identity.eventId
    val occurrenceUtcMillis: Long get() = identity.occurrenceUtcMillis
    val requestId: String
        get() = "${REMINDER_REQUEST_PREFIX}${identity.semanticKey}"
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
