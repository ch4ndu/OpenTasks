package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.calendar.CalendarProvider

class CheckCalendarPermissionUseCase(
    private val calendarProvider: CalendarProvider,
) {
    suspend operator fun invoke(): CalendarPermissionStatus =
        calendarProvider.checkPermission()
}
