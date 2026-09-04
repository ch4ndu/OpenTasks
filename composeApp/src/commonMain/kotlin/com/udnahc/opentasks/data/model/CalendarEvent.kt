package com.udnahc.opentasks.data.model

enum class CalendarEventSourceKind {
    LEGACY,
    ANDROID,
    IOS,
    MACOS,
    ICS,
}

data class CalendarEvent(
    val externalId: String,
    val title: String,
    val description: String,
    val startTimeUtcMillis: Long,
    val endTimeUtcMillis: Long?,
    val calendarName: String,
    val isAllDay: Boolean,
    val location: String = "",
    val url: String = "",
    val organizer: String = "",
    val status: String = "",
    val attendees: List<String> = emptyList(),
    val sourceKind: CalendarEventSourceKind = CalendarEventSourceKind.LEGACY,
    val rawUid: String? = null,
    val occurrenceToken: Long? = null,
)
