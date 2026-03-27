package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.calendar.IcsParser
import com.udnahc.opentasks.data.model.CalendarEvent

class ParseIcsUseCase {
    operator fun invoke(icsContent: String): List<CalendarEvent> =
        IcsParser.parse(icsContent)
}
