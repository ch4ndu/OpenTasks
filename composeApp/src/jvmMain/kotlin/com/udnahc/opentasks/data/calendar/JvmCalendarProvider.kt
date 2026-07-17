package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import org.lighthousegames.logging.logging

private val log = logging("JvmCalendarProvider")
private val IS_MAC = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

class JvmCalendarProvider : CalendarProvider {

    override fun isAvailable(): Boolean = IS_MAC

    override suspend fun checkPermission(): CalendarPermissionStatus {
        if (!IS_MAC) return CalendarPermissionStatus.NOT_AVAILABLE
        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder(
                    "osascript", "-e",
                    "tell application \"Calendar\" to get name of calendars"
                ).redirectErrorStream(true).start()
                val exited = process.waitFor(10, TimeUnit.SECONDS)
                if (exited && process.exitValue() == 0) CalendarPermissionStatus.GRANTED
                else CalendarPermissionStatus.DENIED
            } catch (e: Exception) {
                log.e { "Calendar permission check failed: ${e.message}" }
                CalendarPermissionStatus.DENIED
            }
        }
    }

    override suspend fun requestPermission(): CalendarPermissionStatus = checkPermission()

    override suspend fun fetchEvents(
        startUtcMillis: Long,
        endUtcMillis: Long,
    ): List<CalendarEvent> {
        if (!IS_MAC) return emptyList()
        if (checkPermission() != CalendarPermissionStatus.GRANTED) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val startDate = formatForAppleScript(startUtcMillis)
                val endDate = formatForAppleScript(endUtcMillis)

                // AppleScript that outputs tab-separated fields per event, one per line
                // Fields: uid \t summary \t description \t startEpoch \t endEpoch \t calendarName \t isAllDay \t location \t url
                val script = """
                    set output to ""
                    tell application "Calendar"
                        repeat with cal in calendars
                            set calName to name of cal
                            set evtList to (every event of cal whose start date >= date "$startDate" and start date <= date "$endDate")
                            repeat with evt in evtList
                                set evtUid to uid of evt
                                set evtSummary to summary of evt
                                set evtDesc to ""
                                try
                                    set evtDesc to description of evt
                                end try
                                set evtLoc to ""
                                try
                                    set evtLoc to location of evt
                                end try
                                set evtUrl to ""
                                try
                                    set evtUrl to url of evt
                                end try
                                set evtStart to ((start date of evt) - (date "Thursday, January 1, 1970 at 12:00:00 AM")) div 1
                                set evtEnd to ((end date of evt) - (date "Thursday, January 1, 1970 at 12:00:00 AM")) div 1
                                set evtAllDay to allday event of evt
                                set output to output & evtUid & tab & evtSummary & tab & evtDesc & tab & evtStart & tab & evtEnd & tab & calName & tab & evtAllDay & tab & evtLoc & tab & evtUrl & linefeed
                            end repeat
                        end repeat
                    end tell
                    return output
                """.trimIndent()

                val process = ProcessBuilder("osascript", "-e", script)
                    .redirectErrorStream(true)
                    .start()

                val outputText = process.inputStream.bufferedReader().use(BufferedReader::readText)
                process.waitFor(30, TimeUnit.SECONDS)

                if (process.exitValue() != 0) return@withContext emptyList()

                parseAppleScriptOutput(outputText)
            } catch (e: Exception) {
                log.e { "Calendar fetch failed: ${e.message}" }
                emptyList()
            }
        }
    }

    private fun parseAppleScriptOutput(output: String): List<CalendarEvent> {
        return output.lines()
            .filter { it.contains("\t") }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 7) return@mapNotNull null
                val title = parts[1].trim()
                if (title.isBlank()) return@mapNotNull null

                val startEpochSeconds = parts[3].trim().toLongOrNull() ?: return@mapNotNull null
                val endEpochSeconds = parts[4].trim().toLongOrNull()

                CalendarEvent(
                    externalId = "mac_${parts[0].trim()}",
                    title = title,
                    description = parts[2].trim(),
                    startTimeUtcMillis = localEpochSecondsToUtcMillis(startEpochSeconds),
                    endTimeUtcMillis = endEpochSeconds?.let(::localEpochSecondsToUtcMillis),
                    calendarName = parts[5].trim(),
                    isAllDay = parts[6].trim().equals("true", ignoreCase = true),
                    location = parts.getOrNull(7)?.trim() ?: "",
                    url = parts.getOrNull(8)?.trim() ?: "",
                )
            }
    }

    private fun formatForAppleScript(utcMillis: Long): String {
        // Format as AppleScript-compatible date string in the local timezone
        val instant = Instant.ofEpochMilli(utcMillis)
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm:ss a")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    /** AppleScript's date subtraction uses local 1970 midnight, not UTC epoch. */
    private fun localEpochSecondsToUtcMillis(seconds: Long): Long =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
