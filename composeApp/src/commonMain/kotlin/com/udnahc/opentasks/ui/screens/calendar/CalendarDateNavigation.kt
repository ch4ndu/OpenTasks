package com.udnahc.opentasks.ui.screens.calendar

import kotlinx.datetime.LocalDate

internal sealed interface CalendarNavigationDateResult {
    data class Valid(val date: LocalDate) : CalendarNavigationDateResult
    data object Invalid : CalendarNavigationDateResult
    data object OutOfRange : CalendarNavigationDateResult
}

/** A navigation event parser; it never normalizes or clamps invalid input. */
internal fun parseCalendarNavigationDate(text: String, minDate: LocalDate, maxDate: LocalDate): CalendarNavigationDateResult {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text)) return CalendarNavigationDateResult.Invalid
    val date = try { LocalDate.parse(text) } catch (_: IllegalArgumentException) { return CalendarNavigationDateResult.Invalid }
    return if (date < minDate || date > maxDate) CalendarNavigationDateResult.OutOfRange else CalendarNavigationDateResult.Valid(date)
}
