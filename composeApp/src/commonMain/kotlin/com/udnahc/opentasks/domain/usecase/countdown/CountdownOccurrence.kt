package com.udnahc.opentasks.domain.usecase.countdown

import com.udnahc.opentasks.data.extensions.computeNextDeadlineLocal
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.toCalendarTask
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
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

    var occurrence = countdown.targetDate
    while (localMillisToLocalDate(occurrence) < date) {
        val next = nextOccurrence(countdown, occurrence)
        if (next <= occurrence) return occurrence
        occurrence = next
    }
    return occurrence
}

fun latestCountdownOccurrenceOnOrBefore(
    countdown: Countdown,
    date: LocalDate,
): Long {
    if (countdown.recurrenceType == RecurrenceType.NONE) return countdown.targetDate
    if (localMillisToLocalDate(countdown.targetDate) > date) return countdown.targetDate

    var occurrence = countdown.targetDate
    while (true) {
        val next = nextOccurrence(countdown, occurrence)
        if (next <= occurrence || localMillisToLocalDate(next) > date) return occurrence
        occurrence = next
    }
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

private fun nextOccurrence(
    countdown: Countdown,
    currentOccurrence: Long,
): Long = computeNextDeadlineLocal(
    currentDeadlineLocalMillis = currentOccurrence,
    recurrenceType = countdown.recurrenceType.name,
    interval = countdown.recurrenceInterval,
)

private fun daysUntil(
    localMillis: Long,
    today: LocalDate,
): Int = today.until(localMillisToLocalDate(localMillis), DateTimeUnit.DAY).toInt()
