package com.udnahc.opentasks.data.extensions

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until

const val MILLIS_PER_DAY = 86400000L
const val MILLIS_PER_HOUR = 3600000L
const val MILLIS_PER_MINUTE = 60000L

// ═══════════════════════════════════════════════════════════════════════════
//  CURRENT TIME
// ═══════════════════════════════════════════════════════════════════════════

/** Current UTC epoch millis. */
fun nowUtcMillis(): Long =
    Clock.System.now().toEpochMilliseconds()

/** Today's local date. */
fun todayLocal(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

// ═══════════════════════════════════════════════════════════════════════════
//  UTC ↔ LOCAL MILLIS CONVERSION
//  "Local millis" = UTC millis shifted by timezone offset, so that
//  dividing by 86400000 yields the correct local day number.
// ═══════════════════════════════════════════════════════════════════════════

/** Convert UTC epoch millis → local-shifted millis. */
fun utcMillisToLocalMillis(utcMillis: Long): Long {
    val instant = Instant.fromEpochMilliseconds(utcMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    // Re-interpret the local datetime as if it were UTC → gives shifted millis
    return local.toInstant(TimeZone.UTC).toEpochMilliseconds()
}

/** Convert local-shifted millis → UTC epoch millis. */
fun localMillisToUtcMillis(localMillis: Long): Long {
    // Interpret shifted millis as UTC to recover the LocalDateTime
    val instant = Instant.fromEpochMilliseconds(localMillis)
    val localDt = instant.toLocalDateTime(TimeZone.UTC)
    // Then convert using the real timezone
    return localDt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

// ═══════════════════════════════════════════════════════════════════════════
//  LOCAL MILLIS → DATE/TIME COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

/** Convert local-shifted millis to LocalDateTime (used by callers that need the full object). */
fun localMillisToLocalDateTime(localMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(localMillis).toLocalDateTime(TimeZone.UTC)

/** Convert local-shifted millis to LocalDate. */
fun localMillisToLocalDate(localMillis: Long): LocalDate =
    localMillisToLocalDateTime(localMillis).date

// ── Pure-arithmetic component extractors (no kotlinx-datetime, safe for previews) ──

fun extractHour(localMillis: Long): Int = ((localMillis % MILLIS_PER_DAY + MILLIS_PER_DAY) % MILLIS_PER_DAY / MILLIS_PER_HOUR).toInt()
fun extractMinute(localMillis: Long): Int = ((localMillis % MILLIS_PER_HOUR + MILLIS_PER_HOUR) % MILLIS_PER_HOUR / MILLIS_PER_MINUTE).toInt()

/**
 * Extract year, month, day from local-shifted epoch millis using civil date arithmetic.
 * Algorithm adapted from Howard Hinnant's `civil_from_days`.
 */
private fun civilFromMillis(localMillis: Long): Triple<Int, Int, Int> {
    val z = (localMillis / MILLIS_PER_DAY) + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = (z - era * 146097).toInt()                 // day of era [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return Triple(year.toInt(), m, d)
}

fun extractYear(localMillis: Long): Int = civilFromMillis(localMillis).first
fun extractMonth(localMillis: Long): Int = civilFromMillis(localMillis).second
fun extractDay(localMillis: Long): Int = civilFromMillis(localMillis).third

// ═══════════════════════════════════════════════════════════════════════════
//  DATE/TIME COMPONENTS → MILLIS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Local-shifted epoch millis for midnight of a given date.
 * Equivalent to the old `startOfDayMillis(year, month, day)`.
 */
fun startOfDayLocalMillis(year: Int, month: Int, day: Int): Long {
    val dt = LocalDateTime(year, month, day, 0, 0)
    return dt.toInstant(TimeZone.UTC).toEpochMilliseconds()
}

/**
 * Build a UTC deadline millis from local date/time components.
 * This is stored in the DB as UTC.
 */
fun computeDeadlineUtcMillis(
    year: Int, month: Int, day: Int,
    hour: Int = 9, minute: Int = 0,
): Long {
    val h = if (hour >= 0) hour else 9
    val m = if (minute >= 0) minute else 0
    val dt = LocalDateTime(year, month, day, h, m)
    return dt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

// ═══════════════════════════════════════════════════════════════════════════
//  RECURRENCE — NEXT DEADLINE COMPUTATION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Compute the next deadline UTC millis for a recurring task.
 * Advances in local time to handle DST correctly, then converts back to UTC.
 * [interval] defaults to 1 (e.g., every 1 day, every 1 week).
 */
fun computeNextDeadlineUtc(
    currentDeadlineUtcMillis: Long,
    recurrenceType: String,
    interval: Int = 1,
): Long {
    val tz = TimeZone.currentSystemDefault()
    val instant = Instant.fromEpochMilliseconds(currentDeadlineUtcMillis)
    val localDt = instant.toLocalDateTime(tz)
    val effectiveInterval = if (interval > 0) interval else 1

    val nextLocalDt = when (recurrenceType) {
        "DAILY" -> localDt.date.plus(effectiveInterval, DateTimeUnit.DAY)
            .let { LocalDateTime(it, localDt.time) }

        "WEEKLY" -> localDt.date.plus(effectiveInterval * 7, DateTimeUnit.DAY)
            .let { LocalDateTime(it, localDt.time) }

        "MONTHLY" -> localDt.date.plus(effectiveInterval, DateTimeUnit.MONTH)
            .let { LocalDateTime(it, localDt.time) }

        "YEARLY" -> localDt.date.plus(effectiveInterval, DateTimeUnit.YEAR)
            .let { LocalDateTime(it, localDt.time) }

        "EVERY_WEEKDAY" -> {
            var next = localDt.date.plus(1, DateTimeUnit.DAY)
            while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                next = next.plus(1, DateTimeUnit.DAY)
            }
            LocalDateTime(next, localDt.time)
        }

        else -> return currentDeadlineUtcMillis // NONE or unknown — no advancement
    }
    return nextLocalDt.toInstant(tz).toEpochMilliseconds()
}

// ═══════════════════════════════════════════════════════════════════════════
//  CALENDAR UTILITIES
// ═══════════════════════════════════════════════════════════════════════════

/** Day of week: 0=Sun, 1=Mon, …, 6=Sat.  Pure arithmetic (Tomohiko Sakamoto). */
fun dayOfWeekIndex(year: Int, month: Int, day: Int): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    @Suppress("NAME_SHADOWING")
    val y = if (month < 3) year - 1 else year
    return (y + y / 4 - y / 100 + y / 400 + t[month - 1] + day) % 7
}

/** Number of days in a given month. */
fun daysInMonth(year: Int, month: Int): Int {
    val start = LocalDate(year, month, 1)
    val next = start.plus(1, DateTimeUnit.MONTH)
    return start.until(next, DateTimeUnit.DAY).toInt()
}

// ═══════════════════════════════════════════════════════════════════════════
//  DAY KEY — for grouping tasks by date
// ═══════════════════════════════════════════════════════════════════════════

/** Day key from local-shifted millis. Matches old `millis / 86400000L`. */
fun dayKey(localMillis: Long): Long = localMillis / MILLIS_PER_DAY

/** Day key from a year/month/day. */
fun dayKeyFromDate(year: Int, month: Int, day: Int): Long =
    startOfDayLocalMillis(year, month, day) / MILLIS_PER_DAY

/** Local-shifted millis from a day key. */
fun dayKeyToMillis(dayKey: Long): Long = dayKey * MILLIS_PER_DAY

// ═══════════════════════════════════════════════════════════════════════════
//  WEEK UTILITIES
// ═══════════════════════════════════════════════════════════════════════════

/** Start-of-week (Sunday) local-shifted millis for the week containing [localMillis]. */
fun startOfWeekLocalMillis(localMillis: Long): Long {
    val date = localMillisToLocalDate(localMillis)
    val dow = date.dayOfWeek
    val sundayOffset = if (dow == DayOfWeek.SUNDAY) 0 else dow.ordinal + 1
    val sunday = date.plus(-sundayOffset, DateTimeUnit.DAY)
    return startOfDayLocalMillis(sunday.year, sunday.monthNumber, sunday.dayOfMonth)
}

// ═══════════════════════════════════════════════════════════════════════════
//  FORMATTING
// ═══════════════════════════════════════════════════════════════════════════

private val MONTH_NAMES_SHORT = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** Format local millis as short date, e.g. "Mar 15". */
fun formatDateShort(localMillis: Long): String {
    val date = localMillisToLocalDate(localMillis)
    return "${MONTH_NAMES_SHORT[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/** Format hour/minute as 12-hour time, e.g. "9:05 AM". */
fun formatTime12Hr(hour: Int, minute: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (hour < 12) "AM" else "PM"
    return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
}

/** Format local millis to 12-hour time string. */
fun formatTimeFromLocalMillis(localMillis: Long): String {
    val dt = localMillisToLocalDateTime(localMillis)
    return formatTime12Hr(dt.hour, dt.minute)
}

/** Format local millis as "Mon, Mar 15" style label. */
fun formatDateLabel(localMillis: Long): String {
    val date = localMillisToLocalDate(localMillis)
    val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayName = dayNames[date.dayOfWeek.ordinal] // Mon=0..Sun=6
    return "$dayName, ${MONTH_NAMES_SHORT[date.monthNumber - 1]} ${date.dayOfMonth}"
}
