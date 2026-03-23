package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent

enum class CalendarPermissionStatus {
    GRANTED,
    DENIED,
    NOT_DETERMINED,
    NOT_AVAILABLE,
}

interface CalendarProvider {
    fun isAvailable(): Boolean
    suspend fun checkPermission(): CalendarPermissionStatus
    suspend fun requestPermission(): CalendarPermissionStatus
    suspend fun fetchEvents(startUtcMillis: Long, endUtcMillis: Long): List<CalendarEvent>
}
