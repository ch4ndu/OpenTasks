package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent

enum class CalendarPermissionStatus {
    GRANTED,
    DENIED,
    NOT_DETERMINED,
    NOT_AVAILABLE,
}

enum class CalendarProviderFailure {
    ACCESS_DENIED,
    TRANSPORT,
    INVALID_RESPONSE,
    TOO_MANY_EVENTS,
}

class CalendarProviderException(
    val failure: CalendarProviderFailure,
) : Exception(
    when (failure) {
        CalendarProviderFailure.ACCESS_DENIED -> "Calendar access denied"
        CalendarProviderFailure.TRANSPORT -> "Calendar transport failed"
        CalendarProviderFailure.INVALID_RESPONSE -> "Calendar response invalid"
        CalendarProviderFailure.TOO_MANY_EVENTS -> "Calendar event limit exceeded"
    }
)

const val MAX_CALENDAR_PROVIDER_EVENTS = 10_000

internal val CALENDAR_PROVIDER_EVENT_ORDER = compareBy<CalendarEvent>(
    { it.startTimeUtcMillis },
    { it.rawUid.orEmpty() },
    { it.occurrenceToken ?: Long.MIN_VALUE },
    { it.endTimeUtcMillis ?: Long.MIN_VALUE },
    { it.title },
    { it.description },
    { it.calendarName },
    { it.isAllDay },
    { it.location },
    { it.url },
    { it.organizer },
    { it.status },
    { it.attendees.joinToString("\u0000") },
    { it.externalId },
)

interface CalendarProvider {
    fun isAvailable(): Boolean
    fun supportsExplicitImportWithoutPermissionRequest(): Boolean = false
    suspend fun checkPermission(): CalendarPermissionStatus
    suspend fun requestPermission(): CalendarPermissionStatus
    suspend fun fetchEvents(
        startUtcMillis: Long,
        endUtcMillis: Long
    ): List<CalendarEvent>
}
