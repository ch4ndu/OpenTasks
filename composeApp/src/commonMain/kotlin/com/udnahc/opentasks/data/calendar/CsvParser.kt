package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import kotlinx.datetime.Instant
import org.lighthousegames.logging.logging

private val log = logging("CsvParser")

/**
 * Parsed row from a TickTick CSV export.
 */
data class CsvTask(
    val title: String,
    val content: String,
    val listName: String,
    val startDate: Long?,
    val dueDate: Long?,
    val isAllDay: Boolean,
    val priority: TaskPriority,
    val isCompleted: Boolean,
    val durationReminders: String,
    val recurrenceType: RecurrenceType,
    val createdAt: Long,
)

/**
 * Parses TickTick CSV exports into [CsvTask] objects.
 *
 * Expected format:
 * - Header comment lines: "Date: …", "Version: …", "Status: …" (multi-line)
 * - Column header row
 * - Data rows with quoted fields (may contain embedded newlines and commas)
 */
object CsvParser {

    fun parse(csvContent: String): List<CsvTask> {
        val lines = csvContent.replace("\r\n", "\n").replace("\r", "\n")

        // Parse all rows respecting quoted fields with embedded newlines
        val rows = parseRows(lines)

        // Find column header row and determine indices
        val headerIndex = rows.indexOfFirst { row ->
            row.any { it.equals("Title", ignoreCase = true) } &&
                    row.any { it.equals("Due Date", ignoreCase = true) }
        }
        if (headerIndex < 0) return emptyList()

        val header = rows[headerIndex]
        val colMap = header.withIndex().associate { (i, name) -> name.trim() to i }

        val tasks = mutableListOf<CsvTask>()
        for (i in (headerIndex + 1) until rows.size) {
            val row = rows[i]
            if (row.size < header.size) continue
            parseCsvTask(row, colMap)?.let { tasks.add(it) }
        }
        return tasks
    }

    private fun parseCsvTask(fields: List<String>, col: Map<String, Int>): CsvTask? {
        val title = fields.getOrNull(col["Title"] ?: -1)?.trim() ?: return null
        if (title.isBlank()) return null

        val content = fields.getOrNull(col["Content"] ?: -1)?.trim() ?: ""
        val listName = fields.getOrNull(col["List Name"] ?: -1)?.trim() ?: "Inbox"
        val startDateStr = fields.getOrNull(col["Start Date"] ?: -1)?.trim() ?: ""
        val dueDateStr = fields.getOrNull(col["Due Date"] ?: -1)?.trim() ?: ""
        val isAllDayStr = fields.getOrNull(col["Is All Day"] ?: -1)?.trim() ?: "false"
        val priorityStr = fields.getOrNull(col["Priority"] ?: -1)?.trim() ?: "0"
        val statusStr = fields.getOrNull(col["Status"] ?: -1)?.trim() ?: "0"
        val reminderStr = fields.getOrNull(col["Reminder"] ?: -1)?.trim() ?: ""
        val repeatStr = fields.getOrNull(col["Repeat"] ?: -1)?.trim() ?: ""
        val createdStr = fields.getOrNull(col["Created Time"] ?: -1)?.trim() ?: ""

        return CsvTask(
            title = title,
            content = content,
            listName = listName.ifBlank { "Inbox" },
            startDate = parseIso8601(startDateStr),
            dueDate = parseIso8601(dueDateStr),
            isAllDay = isAllDayStr.equals("true", ignoreCase = true),
            priority = parsePriority(priorityStr),
            isCompleted = statusStr == "1" || statusStr == "2",
            durationReminders = parseReminders(reminderStr),
            recurrenceType = parseRecurrence(repeatStr),
            createdAt = parseIso8601(createdStr) ?: Instant.DISTANT_PAST.toEpochMilliseconds(),
        )
    }

    // ── CSV row parsing (handles quoted fields with embedded newlines/commas) ──

    private fun parseRows(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < content.length) {
            val c = content[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < content.length && content[i + 1] == '"') {
                            currentField.append('"')
                            i++ // skip escaped quote
                        } else {
                            inQuotes = false
                        }
                    } else {
                        currentField.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                c == '\n' -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> currentField.append(c)
            }
            i++
        }
        // Last row
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    // ── Field parsers ──

    /** Parse ISO 8601 datetime like "2026-03-26T21:00:00+0000" to UTC millis. */
    private fun parseIso8601(value: String): Long? {
        if (value.isBlank()) return null
        return try {
            // Normalize offset: +0000 → +00:00
            val normalized = value.replace(Regex("([+-])(\\d{2})(\\d{2})$"), "$1$2:$3")
            Instant.parse(normalized).toEpochMilliseconds()
        } catch (e: Exception) {
            log.d { "Failed to parse date '$value': ${e.message}" }
            null
        }
    }

    /** TickTick priority: 0→NONE, 1→LOW, 3→MEDIUM, 5→HIGH */
    private fun parsePriority(value: String): TaskPriority = when (value) {
        "5" -> TaskPriority.HIGH
        "3" -> TaskPriority.MEDIUM
        "1" -> TaskPriority.LOW
        else -> TaskPriority.NONE
    }

    /**
     * Parse ISO 8601 duration reminders.
     * Input: "-PT30M" or "-PT0S" or "-PT1H", newline-separated.
     * Output: comma-separated minutes string, e.g. "30,0"
     */
    private fun parseReminders(value: String): String {
        if (value.isBlank()) return ""
        return value.split("\n")
            .mapNotNull { parseDurationToMinutes(it.trim()) }
            .joinToString(",")
    }

    private fun parseDurationToMinutes(duration: String): Int? {
        if (duration.isBlank()) return null
        // Format: -PT30M, -PT0S, -PT1H, -PT1H30M, etc.
        val cleaned = duration.removePrefix("-").removePrefix("PT").removePrefix("P")
        if (cleaned.isBlank()) return null

        var minutes = 0
        val hourMatch = Regex("(\\d+)H").find(cleaned)
        val minMatch = Regex("(\\d+)M").find(cleaned)
        val secMatch = Regex("(\\d+)S").find(cleaned)

        if (hourMatch != null) minutes += hourMatch.groupValues[1].toInt() * 60
        if (minMatch != null) minutes += minMatch.groupValues[1].toInt()
        if (secMatch != null && hourMatch == null && minMatch == null) minutes = 0 // PT0S = 0 min

        return minutes
    }

    /** Extract FREQ from RRULE and map to RecurrenceType. */
    private fun parseRecurrence(value: String): RecurrenceType {
        if (value.isBlank()) return RecurrenceType.NONE
        val freq = Regex("FREQ=(\\w+)").find(value)?.groupValues?.get(1)?.uppercase()
        return when (freq) {
            "DAILY" -> RecurrenceType.DAILY
            "WEEKLY" -> RecurrenceType.WEEKLY
            "MONTHLY" -> RecurrenceType.MONTHLY
            "YEARLY" -> RecurrenceType.YEARLY
            else -> RecurrenceType.NONE
        }
    }
}
