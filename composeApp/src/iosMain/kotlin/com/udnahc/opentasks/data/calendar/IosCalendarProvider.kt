package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.CalendarEventSourceKind
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.EventKit.EKAuthorizationStatusWriteOnly
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.time.Instant

@OptIn(ExperimentalForeignApi::class)
class IosCalendarProvider : CalendarProvider {

    private val eventStore = EKEventStore()

    override fun isAvailable(): Boolean = true

    override suspend fun checkPermission(): CalendarPermissionStatus {
        return mapAuthorizationStatus(
            EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        )
    }

    override suspend fun requestPermission(): CalendarPermissionStatus {
        return suspendCancellableCoroutine { continuation ->
            eventStore.requestFullAccessToEventsWithCompletion { granted, _ ->
                val status = if (granted) CalendarPermissionStatus.GRANTED
                else CalendarPermissionStatus.DENIED
                if (continuation.isActive) {
                    continuation.resume(status)
                }
            }
        }
    }

    override suspend fun fetchEvents(
        startUtcMillis: Long,
        endUtcMillis: Long,
    ): List<CalendarEvent> {
        if (checkPermission() != CalendarPermissionStatus.GRANTED) {
            throw CalendarProviderException(CalendarProviderFailure.ACCESS_DENIED)
        }

        val startDate = NSDate.dateWithTimeIntervalSince1970(startUtcMillis / 1000.0)
        val endDate = NSDate.dateWithTimeIntervalSince1970(endUtcMillis / 1000.0)

        val predicate = eventStore.predicateForEventsWithStartDate(
            startDate = startDate,
            endDate = endDate,
            calendars = null,
        )

        val coroutineContext = currentCoroutineContext()
        val events = mutableListOf<CalendarEvent>()
        var overflow = false
        eventStore.enumerateEventsMatchingPredicate(predicate) { event, stop ->
            if (!coroutineContext.isActive) {
                stop?.pointed?.value = true
                return@enumerateEventsMatchingPredicate
            }

            val currentEvent = event ?: return@enumerateEventsMatchingPredicate
            val mapped = currentEvent.toCalendarEvent() ?: return@enumerateEventsMatchingPredicate
            if (events.size == MAX_CALENDAR_PROVIDER_EVENTS) {
                overflow = true
                stop?.pointed?.value = true
                return@enumerateEventsMatchingPredicate
            }
            events.add(mapped)
        }
        coroutineContext.ensureActive()
        if (overflow) {
            throw CalendarProviderException(CalendarProviderFailure.TOO_MANY_EVENTS)
        }
        val sorted = events.sortedWith(CALENDAR_PROVIDER_EVENT_ORDER)
        coroutineContext.ensureActive()
        return sorted
    }

    private fun EKEvent.toCalendarEvent(): CalendarEvent? {
        val eventTitle = title ?: return null
        if (eventTitle.isBlank()) return null
        val eventStartDate = startDate ?: return null
        val startMillis = (eventStartDate.timeIntervalSince1970 * 1000).toLong()
        val statusText = when (status.toInt()) {
            1 -> "Confirmed"
            2 -> "Tentative"
            3 -> "Cancelled"
            else -> ""
        }
        val attendeeNames = attendees?.mapNotNull { attendee ->
            (attendee as? platform.EventKit.EKParticipant)?.name
        } ?: emptyList()
        val occurrence = if (allDay) {
            Instant.fromEpochMilliseconds(startMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toEpochDays()
                .toLong()
        } else {
            startMillis
        }

        return CalendarEvent(
            externalId = "ios_${eventIdentifier}_${eventStartDate.timeIntervalSince1970.toLong()}",
            title = eventTitle,
            description = notes ?: "",
            startTimeUtcMillis = startMillis,
            endTimeUtcMillis = endDate?.let { (it.timeIntervalSince1970 * 1000).toLong() },
            calendarName = calendar?.title ?: "",
            isAllDay = allDay,
            location = location ?: "",
            url = URL?.absoluteString ?: "",
            organizer = organizer?.name ?: "",
            status = statusText,
            attendees = attendeeNames.sorted(),
            sourceKind = CalendarEventSourceKind.IOS,
            rawUid = eventIdentifier,
            occurrenceToken = occurrence,
        )
    }

    private fun mapAuthorizationStatus(status: Long): CalendarPermissionStatus {
        return when (status) {
            EKAuthorizationStatusFullAccess -> CalendarPermissionStatus.GRANTED
            EKAuthorizationStatusNotDetermined -> CalendarPermissionStatus.NOT_DETERMINED
            EKAuthorizationStatusDenied,
            EKAuthorizationStatusRestricted,
            EKAuthorizationStatusWriteOnly -> CalendarPermissionStatus.DENIED
            else -> CalendarPermissionStatus.DENIED
        }
    }
}
