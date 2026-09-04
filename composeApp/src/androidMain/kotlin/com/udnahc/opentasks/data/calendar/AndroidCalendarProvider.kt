package com.udnahc.opentasks.data.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.CalendarEventSourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

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
        if (checkPermission() != CalendarPermissionStatus.GRANTED) {
            throw CalendarProviderException(CalendarProviderFailure.ACCESS_DENIED)
        }

        return withContext(Dispatchers.IO) {
            try {
                suspendCancellableCoroutine { continuation ->
                    val cancellationSignal = CancellationSignal()
                    continuation.invokeOnCancellation { cancellationSignal.cancel() }
                    try {
                        val events = queryEvents(
                            contentResolver = context.contentResolver,
                            startUtcMillis = startUtcMillis,
                            endUtcMillis = endUtcMillis,
                            cancellationSignal = cancellationSignal,
                            queryContext = continuation.context,
                        )
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(events))
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(error))
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CalendarProviderException) {
                throw error
            } catch (_: SecurityException) {
                throw CalendarProviderException(CalendarProviderFailure.ACCESS_DENIED)
            } catch (_: OperationCanceledException) {
                currentCoroutineContext().ensureActive()
                throw CalendarProviderException(CalendarProviderFailure.TRANSPORT)
            } catch (_: Exception) {
                throw CalendarProviderException(CalendarProviderFailure.TRANSPORT)
            }
        }
    }

    private fun queryEvents(
        contentResolver: ContentResolver,
        startUtcMillis: Long,
        endUtcMillis: Long,
        cancellationSignal: CancellationSignal,
        queryContext: CoroutineContext,
    ): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()

        // Instances expands recurring events into individual occurrences.
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

        val cursor = contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC, ${CalendarContract.Instances.EVENT_ID} ASC",
            cancellationSignal,
        ) ?: throw CalendarProviderException(CalendarProviderFailure.INVALID_RESPONSE)

        cursor.use {
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
                queryContext.ensureActive()
                val title = cursor.getString(titleIdx) ?: ""
                if (title.isBlank() || cursor.isNull(beginIdx)) continue
                if (events.size == MAX_CALENDAR_PROVIDER_EVENTS) {
                    throw CalendarProviderException(CalendarProviderFailure.TOO_MANY_EVENTS)
                }

                val eventId = cursor.getLong(eventIdIdx)
                val rawBegin = cursor.getLong(beginIdx)
                val rawEnd = if (cursor.isNull(endIdx)) null else cursor.getLong(endIdx)
                val isAllDay = cursor.getInt(allDayIdx) == 1
                val start = if (isAllDay) utcCivilDateAsLocalStart(rawBegin) else rawBegin
                val end = if (isAllDay) {
                    inclusiveAllDayEndOrNull(rawBegin, rawEnd, start)
                } else {
                    rawEnd
                }
                val statusInt = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                val status = when (statusInt) {
                    CalendarContract.Events.STATUS_TENTATIVE -> "Tentative"
                    CalendarContract.Events.STATUS_CONFIRMED -> "Confirmed"
                    CalendarContract.Events.STATUS_CANCELED -> "Cancelled"
                    else -> ""
                }
                val occurrenceToken = if (isAllDay) {
                    Instant.fromEpochMilliseconds(rawBegin)
                        .toLocalDateTime(TimeZone.UTC)
                        .date
                        .toEpochDays()
                        .toLong()
                } else {
                    rawBegin
                }

                events.add(
                    CalendarEvent(
                        // Preserve the raw BEGIN-based legacy alias for one-batch compatibility.
                        externalId = "android_${eventId}_$rawBegin",
                        title = title,
                        description = cursor.getString(descIdx) ?: "",
                        startTimeUtcMillis = start,
                        endTimeUtcMillis = end,
                        calendarName = cursor.getString(calNameIdx) ?: "",
                        isAllDay = isAllDay,
                        location = if (locationIdx >= 0) cursor.getString(locationIdx) ?: "" else "",
                        organizer = if (organizerIdx >= 0) cursor.getString(organizerIdx) ?: "" else "",
                        status = status,
                        sourceKind = CalendarEventSourceKind.ANDROID,
                        rawUid = eventId.toString(),
                        occurrenceToken = occurrenceToken,
                    )
                )
            }
        }

        queryContext.ensureActive()
        val sorted = events.sortedWith(CALENDAR_PROVIDER_EVENT_ORDER)
        queryContext.ensureActive()
        return sorted
    }

    private fun utcCivilDateAsLocalStart(rawUtcMillis: Long): Long {
        val civilDate = Instant.fromEpochMilliseconds(rawUtcMillis)
            .toLocalDateTime(TimeZone.UTC)
            .date
        return civilDate
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }

    private fun inclusiveAllDayEndOrNull(
        rawBegin: Long,
        rawEnd: Long?,
        localStart: Long,
    ): Long? {
        if (rawEnd == null || rawEnd <= rawBegin) return null
        val inclusiveCivilDate = Instant.fromEpochMilliseconds(rawEnd)
            .toLocalDateTime(TimeZone.UTC)
            .date
            .minus(1, DateTimeUnit.DAY)
        return inclusiveCivilDate
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
            .takeIf { it >= localStart }
    }
}
