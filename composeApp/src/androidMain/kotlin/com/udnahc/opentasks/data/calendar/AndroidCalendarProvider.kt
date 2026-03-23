package com.udnahc.opentasks.data.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.udnahc.opentasks.data.model.CalendarEvent

class AndroidCalendarProvider(private val context: Context) : CalendarProvider {

    override fun isAvailable(): Boolean = true

    override suspend fun checkPermission(): CalendarPermissionStatus {
        return when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED -> CalendarPermissionStatus.GRANTED
            else -> CalendarPermissionStatus.DENIED
        }
    }

    override suspend fun requestPermission(): CalendarPermissionStatus {
        // Permission request is handled by the UI layer (Compose rememberLauncherForActivityResult)
        // This just rechecks the current status
        return checkPermission()
    }

    override suspend fun fetchEvents(
        startUtcMillis: Long,
        endUtcMillis: Long,
    ): List<CalendarEvent> {
        if (checkPermission() != CalendarPermissionStatus.GRANTED) return emptyList()

        val events = mutableListOf<CalendarEvent>()
        val contentResolver: ContentResolver = context.contentResolver

        // Use Instances table which expands recurring events into individual occurrences
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { ContentUris.appendId(it, startUtcMillis) }
            .let { ContentUris.appendId(it, endUtcMillis) }
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ORGANIZER,
            CalendarContract.Instances.STATUS,
        )

        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        // No selection needed — the URI already filters by time range
        contentResolver.query(
            uri,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val eventIdIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
            val calNameIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
            val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val locationIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val organizerIdx = cursor.getColumnIndex(CalendarContract.Instances.ORGANIZER)
            val statusIdx = cursor.getColumnIndex(CalendarContract.Instances.STATUS)

            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(eventIdIdx)
                val title = cursor.getString(titleIdx) ?: ""
                if (title.isBlank()) continue

                val begin = cursor.getLong(beginIdx)
                val statusInt = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                val statusStr = when (statusInt) {
                    CalendarContract.Events.STATUS_TENTATIVE -> "Tentative"
                    CalendarContract.Events.STATUS_CONFIRMED -> "Confirmed"
                    CalendarContract.Events.STATUS_CANCELED -> "Cancelled"
                    else -> ""
                }

                events.add(
                    CalendarEvent(
                        // Include begin time in externalId to disambiguate recurring instances
                        externalId = "android_${eventId}_$begin",
                        title = title,
                        description = cursor.getString(descIdx) ?: "",
                        startTimeUtcMillis = begin,
                        endTimeUtcMillis = if (!cursor.isNull(endIdx)) cursor.getLong(endIdx) else null,
                        calendarName = cursor.getString(calNameIdx) ?: "",
                        isAllDay = cursor.getInt(allDayIdx) == 1,
                        location = if (locationIdx >= 0) cursor.getString(locationIdx) ?: "" else "",
                        organizer = if (organizerIdx >= 0) cursor.getString(organizerIdx) ?: "" else "",
                        status = statusStr,
                    )
                )
            }
        }

        return events
    }
}
