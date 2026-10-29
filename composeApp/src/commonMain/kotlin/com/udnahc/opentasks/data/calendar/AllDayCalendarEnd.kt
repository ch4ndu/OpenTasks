package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.CalendarEventSourceKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Normalize only newly persisted imports, after their source identity has been computed.
 * ICS and Android CalendarEvents use the inclusive final civil day; iOS/macOS retain
 * their native exclusive end. Task endpoints use the final included millisecond so
 * Calendar expansion and date-only export agree. Unknown legacy sources stay untouched.
 * Never apply this conversion to existing tasks, whose endpoint convention is ambiguous.
 */
internal fun calendarTaskEndUtcMillis(event: CalendarEvent): Long? {
    val end = event.endTimeUtcMillis ?: return null
    if (!event.isAllDay || event.sourceKind == CalendarEventSourceKind.LEGACY) return end
    val exclusiveEnd = when (event.sourceKind) {
        CalendarEventSourceKind.ICS, CalendarEventSourceKind.ANDROID -> {
            val zone = TimeZone.currentSystemDefault()
            Instant.fromEpochMilliseconds(end).toLocalDateTime(zone).date
                .plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        }
        else -> end
    }
    return exclusiveEnd.takeIf { it > event.startTimeUtcMillis }?.minus(1L)
}
