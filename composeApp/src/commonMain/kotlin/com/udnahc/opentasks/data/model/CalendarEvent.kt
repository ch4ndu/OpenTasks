package com.udnahc.opentasks.data.model

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
)
