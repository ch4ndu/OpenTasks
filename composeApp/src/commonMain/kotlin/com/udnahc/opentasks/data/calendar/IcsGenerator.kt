package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Generates RFC 5545 VCALENDAR content with VEVENT entries for each task.
 * Uses VEVENT (not VTODO) for broader app compatibility.
 */
object IcsGenerator {

    fun generate(tasks: List<Task>): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//OpenTasks//EN")
        sb.appendLine("X-WR-CALNAME:OpenTasks")

        for (task in tasks) {
            appendVEvent(sb, task)
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun appendVEvent(
        sb: StringBuilder,
        task: Task
    ) {
        // Determine start/end times. Tasks without deadlines use createdAt as fallback.
        // Tasks are provided with raw UTC timestamps from the repository.
        val startUtcMillis = task.deadline ?: task.createdAt
        val endUtcMillis = task.endDeadline ?: task.deadline ?: task.createdAt
        if (startUtcMillis == 0L) return // Skip tasks with no meaningful timestamp

        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("UID:${task.id}")
        sb.appendLine("SUMMARY:${escapeIcs(task.title)}")

        if (task.content.isNotBlank()) {
            sb.appendLine("DESCRIPTION:${escapeIcs(task.content)}")
        }

        if (task.isAllDay) {
            // All-day events use VALUE=DATE format (YYYYMMDD).
            // Per RFC 5545, DTEND is exclusive — add one day so the event is visible.
            sb.appendLine("DTSTART;VALUE=DATE:${formatDateOnly(startUtcMillis)}")
            sb.appendLine("DTEND;VALUE=DATE:${formatDateOnlyPlusOneDay(endUtcMillis)}")
        } else {
            sb.appendLine("DTSTART:${formatIcsDateTime(startUtcMillis)}")
            sb.appendLine("DTEND:${formatIcsDateTime(endUtcMillis)}")
        }

        if (task.location.isNotBlank()) {
            sb.appendLine("LOCATION:${escapeIcs(task.location)}")
        }
        if (task.url.isNotBlank()) {
            sb.appendLine("URL:${task.url}")
        }

        sb.appendLine("STATUS:${if (task.status == TaskStatus.DONE) "CONFIRMED" else "TENTATIVE"}")
        sb.appendLine("END:VEVENT")
    }

    /** Format UTC millis to ICS datetime format: 20260315T100000Z */
    private fun formatIcsDateTime(utcMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(utcMillis)
        val dt = instant.toLocalDateTime(TimeZone.UTC)
        return buildString {
            append(dt.year.toString().padStart(4, '0'))
            append(dt.monthNumber.toString().padStart(2, '0'))
            append(dt.dayOfMonth.toString().padStart(2, '0'))
            append('T')
            append(dt.hour.toString().padStart(2, '0'))
            append(dt.minute.toString().padStart(2, '0'))
            append(dt.second.toString().padStart(2, '0'))
            append('Z')
        }
    }

    /** Format UTC millis to ICS date-only format for the next day (exclusive end): 20260316 */
    private fun formatDateOnlyPlusOneDay(utcMillis: Long): String {
        val date = Instant.fromEpochMilliseconds(utcMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return formatDateOnly(date.plus(1, DateTimeUnit.DAY))
    }

    /** Format UTC millis to ICS date-only format: 20260315 */
    private fun formatDateOnly(utcMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(utcMillis)
        return formatDateOnly(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)
    }

    private fun formatDateOnly(date: LocalDate): String {
        return buildString {
            append(date.year.toString().padStart(4, '0'))
            append(date.monthNumber.toString().padStart(2, '0'))
            append(date.dayOfMonth.toString().padStart(2, '0'))
        }
    }

    /** Escape text for ICS: backslash-escape commas, semicolons, backslashes, and newlines. */
    private fun escapeIcs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
