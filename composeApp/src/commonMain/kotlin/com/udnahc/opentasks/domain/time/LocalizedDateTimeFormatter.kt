package com.udnahc.opentasks.domain.time

import com.udnahc.opentasks.data.extensions.localMillisToLocalDateTime

interface DateTimeTextFormatter {
    fun formatShortDate(localMillis: Long): String
    fun formatDateWithYear(localMillis: Long): String {
        val year = localMillisToLocalDateTime(localMillis).year
        return "${formatShortDate(localMillis)} $year"
    }
    fun formatDateLabel(localMillis: Long): String
    fun formatTime(localMillis: Long): String
    fun formatHour(hour: Int): String
    fun formatMonthYear(localMillis: Long): String
    fun formatShortWeekday(localMillis: Long): String
    fun formatWeekRange(startLocalMillis: Long, endLocalMillis: Long): String
}

expect class LocalizedDateTimeFormatter() : DateTimeTextFormatter

/** Stable English fallback for tests and direct constructors outside platform DI. */
object EnglishDateTimeFormatter : DateTimeTextFormatter {
    override fun formatShortDate(localMillis: Long): String {
        val date = localMillisToLocalDateTime(localMillis).date
        return "${SHORT_MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
    }

    override fun formatDateWithYear(localMillis: Long): String {
        val date = localMillisToLocalDateTime(localMillis).date
        return "${SHORT_MONTHS[date.monthNumber - 1]} ${date.dayOfMonth} ${date.year}"
    }

    override fun formatDateLabel(localMillis: Long): String {
        val date = localMillisToLocalDateTime(localMillis).date
        return "${SHORT_WEEKDAYS[date.dayOfWeek.ordinal]}, ${formatShortDate(localMillis)}"
    }

    override fun formatTime(localMillis: Long): String {
        val time = localMillisToLocalDateTime(localMillis).time
        val displayHour = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        val period = if (time.hour < 12) "AM" else "PM"
        return "$displayHour:${time.minute.toString().padStart(2, '0')} $period"
    }

    override fun formatHour(hour: Int): String {
        val normalized = ((hour % 24) + 24) % 24
        val displayHour = when {
            normalized == 0 -> 12
            normalized > 12 -> normalized - 12
            else -> normalized
        }
        return "$displayHour ${if (normalized < 12) "AM" else "PM"}"
    }

    override fun formatMonthYear(localMillis: Long): String {
        val date = localMillisToLocalDateTime(localMillis).date
        return "${FULL_MONTHS[date.monthNumber - 1]} ${date.year}"
    }

    override fun formatShortWeekday(localMillis: Long): String {
        val date = localMillisToLocalDateTime(localMillis).date
        return SHORT_WEEKDAYS[date.dayOfWeek.ordinal]
    }

    override fun formatWeekRange(startLocalMillis: Long, endLocalMillis: Long): String {
        val start = localMillisToLocalDateTime(startLocalMillis).date
        val end = localMillisToLocalDateTime(endLocalMillis).date
        return if (start.year == end.year && start.monthNumber == end.monthNumber) {
            "${SHORT_MONTHS[start.monthNumber - 1]} ${start.dayOfMonth} – ${end.dayOfMonth}"
        } else {
            "${formatShortDate(startLocalMillis)} – ${formatShortDate(endLocalMillis)}"
        }
    }
}

private val SHORT_MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val FULL_MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val SHORT_WEEKDAYS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
