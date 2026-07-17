package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume

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
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, _ ->
                val status = if (granted) CalendarPermissionStatus.GRANTED
                else CalendarPermissionStatus.DENIED
                continuation.resume(status)
            }
        }
    }

    override suspend fun fetchEvents(
        startUtcMillis: Long,
        endUtcMillis: Long,
    ): List<CalendarEvent> {
        if (checkPermission() != CalendarPermissionStatus.GRANTED) return emptyList()

        val startDate = NSDate.dateWithTimeIntervalSince1970(startUtcMillis / 1000.0)
        val endDate = NSDate.dateWithTimeIntervalSince1970(endUtcMillis / 1000.0)

        val predicate = eventStore.predicateForEventsWithStartDate(
            startDate = startDate,
            endDate = endDate,
            calendars = null,
        )

        return eventStore.eventsMatchingPredicate(predicate).mapNotNull { event ->
            val ekEvent = event as? EKEvent ?: return@mapNotNull null
            val title = ekEvent.title ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            val startDate = ekEvent.startDate ?: return@mapNotNull null

            val statusStr = when (ekEvent.status.toInt()) {
                1 -> "Confirmed"
                2 -> "Tentative"
                3 -> "Cancelled"
                else -> ""
            }

            val attendeeNames = ekEvent.attendees?.mapNotNull { attendee ->
                (attendee as? platform.EventKit.EKParticipant)?.name
            } ?: emptyList()

            CalendarEvent(
                externalId = "ios_${ekEvent.eventIdentifier}_${startDate.timeIntervalSince1970.toLong()}",
                title = title,
                description = ekEvent.notes ?: "",
                startTimeUtcMillis = (startDate.timeIntervalSince1970 * 1000).toLong(),
                endTimeUtcMillis = ekEvent.endDate?.let { (it.timeIntervalSince1970 * 1000).toLong() },
                calendarName = ekEvent.calendar?.title ?: "",
                isAllDay = ekEvent.allDay,
                location = ekEvent.location ?: "",
                url = ekEvent.URL?.absoluteString ?: "",
                organizer = ekEvent.organizer?.name ?: "",
                status = statusStr,
                attendees = attendeeNames,
            )
        }
    }

    private fun mapAuthorizationStatus(status: Long): CalendarPermissionStatus {
        return when (status) {
            EKAuthorizationStatusAuthorized -> CalendarPermissionStatus.GRANTED
            EKAuthorizationStatusDenied -> CalendarPermissionStatus.DENIED
            EKAuthorizationStatusNotDetermined -> CalendarPermissionStatus.NOT_DETERMINED
            else -> CalendarPermissionStatus.DENIED
        }
    }
}
