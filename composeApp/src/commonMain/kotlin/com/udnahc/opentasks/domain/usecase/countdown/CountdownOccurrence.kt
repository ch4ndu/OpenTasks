package com.udnahc.opentasks.domain.usecase.countdown

import com.udnahc.opentasks.data.extensions.daysInMonth
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.localMillisToLocalDateTime
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.toCalendarTask
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.until

data class CountdownOccurrence(
    val countdown: Countdown,
    val effectiveTargetDate: Long,
    val effectiveDate: LocalDate,
    val daysUntil: Int,
)

fun projectCountdownOccurrence(
    countdown: Countdown,
    today: LocalDate,
): CountdownOccurrence {
    val effectiveTargetDate = when (countdown.countingMode) {
        CountingMode.COUNTDOWN -> nextCountdownOccurrenceOnOrAfter(countdown, today)
        CountingMode.COUNT_UP -> latestCountdownOccurrenceOnOrBefore(countdown, today)
    }
    return CountdownOccurrence(
        countdown = countdown,
        effectiveTargetDate = effectiveTargetDate,
        effectiveDate = localMillisToLocalDate(effectiveTargetDate),
        daysUntil = daysUntil(effectiveTargetDate, today),
    )
}

fun projectCountdownCalendarTasks(
    countdowns: List<Countdown>,
    today: LocalDate,
): List<Task> = countdowns.map { countdown ->
    val occurrence = projectCountdownOccurrence(countdown, today)
    countdown.toCalendarTask(occurrence.effectiveTargetDate)
}

fun nextCountdownOccurrenceOnOrAfter(
    countdown: Countdown,
    date: LocalDate,
): Long {
    if (countdown.recurrenceType == RecurrenceType.NONE) return countdown.targetDate
    val anchorDate = localMillisToLocalDate(countdown.targetDate)
    if (anchorDate >= date) return countdown.targetDate

    val approximateIndex = approximateOccurrenceIndex(countdown, anchorDate, date)
    return adjacentCandidates(countdown, approximateIndex)
        .asSequence()
        .filter { candidate -> candidate.date >= date }
        .minByOrNull { candidate -> candidate.index }
        ?.millis
        ?: countdown.targetDate
}

fun latestCountdownOccurrenceOnOrBefore(
    countdown: Countdown,
    date: LocalDate,
): Long {
    if (countdown.recurrenceType == RecurrenceType.NONE) return countdown.targetDate
    val anchorDate = localMillisToLocalDate(countdown.targetDate)
    if (anchorDate > date) return countdown.targetDate

    val approximateIndex = approximateOccurrenceIndex(countdown, anchorDate, date)
    return adjacentCandidates(countdown, approximateIndex)
        .asSequence()
        .filter { candidate -> candidate.date <= date }
        .maxByOrNull { candidate -> candidate.index }
        ?.millis
        ?: countdown.targetDate
}

fun isCountdownVisibleInList(
    countdown: Countdown,
    today: LocalDate,
): Boolean = when (countdown.smartListVisibility) {
    SmartListVisibility.ALWAYS -> true
    SmartListVisibility.DO_NOT_SHOW -> false
    SmartListVisibility.ON_THE_DAY -> isWithinUpcomingWindow(countdown, today, leadDays = 0)
    SmartListVisibility.THREE_DAYS_EARLY -> isWithinUpcomingWindow(countdown, today, leadDays = 3)
    SmartListVisibility.SEVEN_DAYS_EARLY -> isWithinUpcomingWindow(countdown, today, leadDays = 7)
}

private fun isWithinUpcomingWindow(
    countdown: Countdown,
    today: LocalDate,
    leadDays: Int,
): Boolean {
    val occurrenceDate = localMillisToLocalDate(nextCountdownOccurrenceOnOrAfter(countdown, today))
    val daysUntil = today.until(occurrenceDate, DateTimeUnit.DAY).toInt()
    return daysUntil in 0..leadDays
}

private const val ADJACENT_INDEX_RADIUS = 2

private data class OccurrenceCandidate(
    val index: Long,
    val date: LocalDate,
    val millis: Long,
)

private fun adjacentCandidates(
    countdown: Countdown,
    approximateIndex: Long,
): List<OccurrenceCandidate> = buildList {
    for (offset in -ADJACENT_INDEX_RADIUS..ADJACENT_INDEX_RADIUS) {
        val index = approximateIndex + offset
        if (index < 0) continue
        occurrenceAt(countdown, index)?.let(::add)
    }
}

private fun approximateOccurrenceIndex(
    countdown: Countdown,
    anchorDate: LocalDate,
    date: LocalDate,
): Long {
    val interval = countdown.recurrenceInterval.toLong().coerceAtLeast(1L)
    return when (countdown.recurrenceType) {
        RecurrenceType.DAILY ->
            anchorDate.until(date, DateTimeUnit.DAY).coerceAtLeast(0L) / interval

        RecurrenceType.WEEKLY ->
            anchorDate.until(date, DateTimeUnit.DAY).coerceAtLeast(0L) / (interval * 7L)

        RecurrenceType.MONTHLY ->
            monthDistance(anchorDate, date).coerceAtLeast(0L) / interval

        RecurrenceType.YEARLY ->
            (date.year - anchorDate.year).toLong().coerceAtLeast(0L) / interval

        RecurrenceType.EVERY_WEEKDAY -> weekdayIndexOnOrBefore(anchorDate, date)
        RecurrenceType.NONE -> 0L
    }
}

private fun occurrenceAt(
    countdown: Countdown,
    index: Long,
): OccurrenceCandidate? {
    val anchor = localMillisToLocalDateTime(countdown.targetDate)
    val interval = countdown.recurrenceInterval.coerceAtLeast(1)
    val date = when (countdown.recurrenceType) {
        RecurrenceType.DAILY -> anchor.date.plusAmount(index, interval, DateTimeUnit.DAY)
        RecurrenceType.WEEKLY -> anchor.date.plusAmount(index, interval, DateTimeUnit.DAY, multiplier = 7)
        RecurrenceType.MONTHLY -> anchoredMonth(anchor.date, index, interval)
        RecurrenceType.YEARLY -> anchoredYear(anchor.date, index, interval)
        RecurrenceType.EVERY_WEEKDAY -> weekdayOccurrence(anchor.date, index)
        RecurrenceType.NONE -> anchor.date
    } ?: return null

    val millis = LocalDateTime(date, anchor.time)
        .toInstant(TimeZone.UTC)
        .toEpochMilliseconds()
    return OccurrenceCandidate(index = index, date = date, millis = millis)
}

private fun LocalDate.plusAmount(
    index: Long,
    interval: Int,
    unit: DateTimeUnit.DateBased,
    multiplier: Int = 1,
): LocalDate? {
    val amount = index * interval.toLong() * multiplier
    if (amount !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
    return runCatching { plus(amount.toInt(), unit) }.getOrNull()
}

private fun anchoredMonth(
    anchor: LocalDate,
    index: Long,
    interval: Int,
): LocalDate? {
    val month = LocalDate(anchor.year, anchor.monthNumber, 1)
        .plusAmount(index, interval, DateTimeUnit.MONTH)
        ?: return null
    return LocalDate(
        month.year,
        month.monthNumber,
        minOf(anchor.dayOfMonth, daysInMonth(month.year, month.monthNumber)),
    )
}

private fun anchoredYear(
    anchor: LocalDate,
    index: Long,
    interval: Int,
): LocalDate? {
    val year = LocalDate(anchor.year, anchor.monthNumber, 1)
        .plusAmount(index, interval, DateTimeUnit.YEAR)
        ?: return null
    return LocalDate(
        year.year,
        anchor.monthNumber,
        minOf(anchor.dayOfMonth, daysInMonth(year.year, anchor.monthNumber)),
    )
}

private fun weekdayOccurrence(
    anchor: LocalDate,
    index: Long,
): LocalDate? {
    if (index == 0L) return anchor
    val first = nextWeekday(anchor)
    val businessDaysAfterFirst = index - 1L
    val wholeWeeks = businessDaysAfterFirst / 5L
    val remainder = (businessDaysAfterFirst % 5L).toInt()
    val calendarDays = wholeWeeks * 7L + remainder +
        if (first.dayOfWeek.ordinal + remainder >= 5) 2 else 0
    if (calendarDays !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
    return runCatching {
        first.plus(calendarDays.toInt(), DateTimeUnit.DAY)
    }.getOrNull()
}

private fun nextWeekday(date: LocalDate): LocalDate {
    val daysUntilWeekday = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> 1
        DayOfWeek.FRIDAY -> 3
        DayOfWeek.SATURDAY -> 2
        DayOfWeek.SUNDAY -> 1
    }
    return date.plus(daysUntilWeekday, DateTimeUnit.DAY)
}

private fun weekdayIndexOnOrBefore(
    anchor: LocalDate,
    date: LocalDate,
): Long {
    val first = nextWeekday(anchor)
    if (date < first) return 0L
    val days = first.until(date, DateTimeUnit.DAY)
    val wholeWeeks = days / 7L
    val remainder = (days % 7L).toInt()
    val weekdayCount = wholeWeeks * 5L + (0..remainder).count { offset ->
        (first.dayOfWeek.ordinal + offset) % 7 < 5
    }
    return weekdayCount.coerceAtLeast(0L)
}

private fun monthDistance(
    anchor: LocalDate,
    date: LocalDate,
): Long = (date.year.toLong() - anchor.year.toLong()) * 12L +
    (date.monthNumber - anchor.monthNumber).toLong()

private fun daysUntil(
    localMillis: Long,
    today: LocalDate,
): Int = today.until(localMillisToLocalDate(localMillis), DateTimeUnit.DAY).toInt()
