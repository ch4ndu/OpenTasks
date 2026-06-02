package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Generates TickTick-compatible CSV content for round-trip with [CsvParser].
 *
 * Column headers match what CsvParser expects:
 * Folder Name, List Name, Title, Content, Is All Day, Start Date, Due Date,
 * Reminder, Repeat, Priority, Status, Created Time, Completed Time, Order, Timezone, Is Floating
 */
object CsvGenerator {

    private val COLUMNS = listOf(
        "Folder Name",
        "List Name",
        "Title",
        "Content",
        "Is All Day",
        "Start Date",
        "Due Date",
        "Reminder",
        "Repeat",
        "Priority",
        "Status",
        "Created Time",
        "Completed Time",
        "Order",
        "Timezone",
        "Is Floating",
    )

    fun generate(
        tasks: List<Task>,
        categories: List<Category>
    ): String {
        val categoryMap = categories.associateBy { it.id }
        val sb = StringBuilder()

        // Header row
        sb.appendLine(COLUMNS.joinToString(",") { escapeCsv(it) })

        for (task in tasks) {
            val listName = categoryMap[task.categoryId]?.name ?: "Inbox"
            val row = listOf(
                "",                                             // Folder Name
                listName,                                       // List Name
                task.title,                                     // Title
                task.content,                                   // Content
                if (task.isAllDay) "true" else "false",         // Is All Day
                formatDeadline(task.deadline),                  // Start Date
                formatDeadline(task.endDeadline ?: task.deadline), // Due Date
                formatReminders(task.durationReminders),        // Reminder
                formatRecurrence(task.recurrenceType),          // Repeat
                formatPriority(task.priority),                  // Priority
                if (task.status == TaskStatus.DONE) "2" else "0", // Status
                formatCreatedAt(task.createdAt),                // Created Time
                "",                                             // Completed Time
                "0",                                            // Order
                "",                                             // Timezone
                "",                                             // Is Floating
            )
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }

        return sb.toString()
    }

    /** Escape a CSV field: wrap in double quotes, double any embedded quotes. */
    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * Format UTC millis deadline to ISO 8601 UTC string for CSV.
     * Tasks are provided with raw UTC timestamps from the repository.
     * Format: 2026-03-26T21:00:00+0000
     */
    private fun formatDeadline(utcMillis: Long?): String {
        if (utcMillis == null || utcMillis == 0L) return ""
        return formatUtcMillisToIso8601(utcMillis)
    }

    /**
     * Format createdAt (UTC millis) to ISO 8601 UTC string.
     */
    private fun formatCreatedAt(utcMillis: Long): String {
        if (utcMillis == 0L) return ""
        return formatUtcMillisToIso8601(utcMillis)
    }

    /** Format UTC millis to ISO 8601 string like "2026-03-26T21:00:00+0000". */
    private fun formatUtcMillisToIso8601(utcMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(utcMillis)
        val dt = instant.toLocalDateTime(TimeZone.UTC)
        return buildString {
            append(dt.year.toString().padStart(4, '0'))
            append('-')
            append(dt.monthNumber.toString().padStart(2, '0'))
            append('-')
            append(dt.dayOfMonth.toString().padStart(2, '0'))
            append('T')
            append(dt.hour.toString().padStart(2, '0'))
            append(':')
            append(dt.minute.toString().padStart(2, '0'))
            append(':')
            append(dt.second.toString().padStart(2, '0'))
            append("+0000")
        }
    }

    /** Reverse of CsvParser.parsePriority: HIGH->5, MEDIUM->3, LOW->1, NONE->0. */
    private fun formatPriority(priority: TaskPriority): String = when (priority) {
        TaskPriority.HIGH -> "5"
        TaskPriority.MEDIUM -> "3"
        TaskPriority.LOW -> "1"
        TaskPriority.NONE -> "0"
    }

    /**
     * Format duration reminders back to ISO 8601 duration strings.
     * Input: comma-separated minutes string, e.g. "30,0"
     * Output: newline-separated durations, e.g. "-PT30M\n-PT0S"
     */
    private fun formatReminders(reminders: String): String {
        if (reminders.isBlank()) return ""
        return reminders.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .joinToString("\n") { minutes ->
                if (minutes == 0) "-PT0S"
                else if (minutes >= 60 && minutes % 60 == 0) "-PT${minutes / 60}H"
                else if (minutes >= 60) "-PT${minutes / 60}H${minutes % 60}M"
                else "-PT${minutes}M"
            }
    }

    /** Format RecurrenceType to RRULE string. */
    private fun formatRecurrence(type: RecurrenceType): String = when (type) {
        RecurrenceType.NONE -> ""
        RecurrenceType.DAILY -> "RRULE:FREQ=DAILY"
        RecurrenceType.WEEKLY -> "RRULE:FREQ=WEEKLY"
        RecurrenceType.MONTHLY -> "RRULE:FREQ=MONTHLY"
        RecurrenceType.YEARLY -> "RRULE:FREQ=YEARLY"
        RecurrenceType.EVERY_WEEKDAY -> "RRULE:FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR"
    }
}
