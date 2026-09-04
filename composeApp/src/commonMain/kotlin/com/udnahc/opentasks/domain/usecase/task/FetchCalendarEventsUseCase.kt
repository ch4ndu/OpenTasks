package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.model.CalendarEvent

class FetchCalendarEventsUseCase(
    private val calendarProvider: CalendarProvider,
) {
    fun isAvailable(): Boolean = calendarProvider.isAvailable()

    fun supportsExplicitImportWithoutPermissionRequest(): Boolean =
        calendarProvider.supportsExplicitImportWithoutPermissionRequest()

    suspend operator fun invoke(
        startUtcMillis: Long,
        endUtcMillis: Long
    ): List<CalendarEvent> =
        calendarProvider.fetchEvents(startUtcMillis, endUtcMillis)
}
