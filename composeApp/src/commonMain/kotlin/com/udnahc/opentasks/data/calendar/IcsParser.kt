package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.lighthousegames.logging.logging

private val log = logging("IcsParser")

/**
 * Parses ICS (iCalendar RFC 5545) content into [CalendarEvent] objects.
 * Supports VEVENT blocks with SUMMARY, DESCRIPTION, DTSTART, DTEND, UID.
 */
object IcsParser {

    fun parse(icsContent: String): List<CalendarEvent> {
        val unfoldedLines = unfold(icsContent)
        val calendarName = extractCalendarName(unfoldedLines)
        val events = mutableListOf<CalendarEvent>()
        var i = 0

        while (i < unfoldedLines.size) {
            if (unfoldedLines[i].uppercase().trim() == "BEGIN:VEVENT") {
                i++
                val eventLines = mutableListOf<String>()
                while (i < unfoldedLines.size && unfoldedLines[i].uppercase()
                        .trim() != "END:VEVENT"
                ) {
                    eventLines.add(unfoldedLines[i])
                    i++
                }
                parseVEvent(eventLines, calendarName)?.let { events.add(it) }
            }
            i++
        }

        return events
    }

    private fun unfold(content: String): List<String> {
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val result = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (result.isNotEmpty()) {
                    result[result.lastIndex] = result.last() + line.substring(1)
                }
            } else {
                result.add(line)
            }
        }
        return result
    }

    private fun extractCalendarName(lines: List<String>): String {
        for (line in lines) {
            if (line.uppercase().startsWith("X-WR-CALNAME:")) {
                return line.substringAfter(":").trim()
            }
        }
        return ""
    }

    private fun parseVEvent(
        lines: List<String>,
        calendarName: String
    ): CalendarEvent? {
        var uid = ""
        var summary = ""
        var description = ""
        var location = ""
        var url = ""
        var organizer = ""
        var status = ""
        val attendees = mutableListOf<String>()
        var dtStart: Pair<Long, Boolean>? = null // (utcMillis, isAllDay)
        var dtEnd: Pair<Long, Boolean>? = null

        for (line in lines) {
            val colonIdx = findPropertyColon(line)
            if (colonIdx < 0) continue

            val fullKey = line.substring(0, colonIdx).uppercase()
            val value = line.substring(colonIdx + 1)
            val propertyName = fullKey.split(";").first()
            val params = parseParams(fullKey)

            when (propertyName) {
                "UID" -> uid = value.trim()
                "SUMMARY" -> summary = unescapeIcs(value)
                "DESCRIPTION" -> description = unescapeIcs(value)
                "DTSTART" -> dtStart = parseDateTime(value.trim(), params)
                "DTEND" -> dtEnd = parseDateTime(value.trim(), params)
                "LOCATION" -> location = unescapeIcs(value)
                "URL" -> url = value.trim()
                "STATUS" -> status = value.trim().lowercase().replaceFirstChar { it.uppercase() }
                "ORGANIZER" -> {
                    // Extract CN= display name if present, otherwise use the value (usually mailto:)
                    val cn = params["CN"]?.removeSurrounding("\"")
                    organizer = cn ?: value.trim().removePrefix("mailto:").removePrefix("MAILTO:")
                }

                "ATTENDEE" -> {
                    val cn = params["CN"]?.removeSurrounding("\"")
                    val name = cn ?: value.trim().removePrefix("mailto:").removePrefix("MAILTO:")
                    if (name.isNotBlank()) attendees.add(name)
                }
            }
        }

        if (summary.isBlank() || dtStart == null) return null
        if (uid.isBlank()) uid = "no_uid_${summary.hashCode()}_${dtStart.first}"

        return CalendarEvent(
            externalId = "ics_$uid",
            title = summary,
            description = description,
            startTimeUtcMillis = dtStart.first,
            endTimeUtcMillis = dtEnd?.first,
            calendarName = calendarName,
            isAllDay = dtStart.second,
            location = location,
            url = url,
            organizer = organizer,
            status = status,
            attendees = attendees,
        )
    }

    private fun findPropertyColon(line: String): Int {
        // Find the first colon that isn't inside a quoted parameter value
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ':' -> if (!inQuotes) return i
            }
        }
        return -1
    }

    private fun parseParams(fullKey: String): Map<String, String> {
        val parts = fullKey.split(";")
        val params = mutableMapOf<String, String>()
        for (i in 1 until parts.size) {
            val eqIdx = parts[i].indexOf('=')
            if (eqIdx > 0) {
                params[parts[i].substring(0, eqIdx).uppercase()] = parts[i].substring(eqIdx + 1)
            }
        }
        return params
    }

    /**
     * Parse an ICS datetime value.
     * Formats:
     * - 20260315T100000Z (UTC)
     * - 20260315T100000 (with TZID param)
     * - 20260315 (all-day, VALUE=DATE)
     *
     * Returns (utcMillis, isAllDay)
     */
    private fun parseDateTime(
        value: String,
        params: Map<String, String>
    ): Pair<Long, Boolean>? {
        return try {
            val isAllDay = params["VALUE"]?.equals("DATE", ignoreCase = true) == true
                    || (value.length == 8 && value.all { it.isDigit() })

            if (isAllDay) {
                val date = LocalDate(
                    year = value.substring(0, 4).toInt(),
                    month = Month(value.substring(4, 6).toInt()),
                    day = value.substring(6, 8).toInt(),
                )
                val millis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                millis to true
            } else if (value.endsWith("Z")) {
                // UTC format
                val dt = parseLocalDateTime(value.removeSuffix("Z"))
                val millis = dt.toInstant(TimeZone.UTC).toEpochMilliseconds()
                millis to false
            } else {
                // TZID-qualified or floating time
                val tzid = params["TZID"]
                val tz = if (tzid != null) {
                    try {
                        TimeZone.of(tzid)
                    } catch (e: Exception) {
                        log.w { "Unknown calendar timezone; using system default" }
                        TimeZone.currentSystemDefault()
                    }
                } else {
                    TimeZone.currentSystemDefault()
                }
                val dt = parseLocalDateTime(value)
                val millis = dt.toInstant(tz).toEpochMilliseconds()
                millis to false
            }
        } catch (e: Exception) {
            log.w { "Failed to parse ICS datetime" }
            null
        }
    }

    private fun parseLocalDateTime(value: String): LocalDateTime {
        // Format: 20260315T100000
        val dateStr = value.substringBefore("T")
        val timeStr = value.substringAfter("T", "000000")
        return LocalDateTime(
            year = dateStr.substring(0, 4).toInt(),
            month = Month(dateStr.substring(4, 6).toInt()),
            day = dateStr.substring(6, 8).toInt(),
            hour = timeStr.substring(0, 2).toInt(),
            minute = timeStr.substring(2, 4).toInt(),
            second = if (timeStr.length >= 6) timeStr.substring(4, 6).toInt() else 0,
        )
    }

    private fun unescapeIcs(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }
}
