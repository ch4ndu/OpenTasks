package com.udnahc.opentasks.ui.screens.calendar

import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.daysInMonth

// ── Calendar day model ──────────────────────────────────────────────────────

internal data class CalendarDay(
    val year: Int,
    val month: Int,   // 1-12
    val day: Int,      // 1-31
    val isCurrentMonth: Boolean,
)

// ── Month grid builder ──────────────────────────────────────────────────────

/** Build a list of weeks for a given month. Each week is a list of 7 CalendarDays. */
internal fun buildMonthWeeks(
    year: Int,
    month: Int
): List<List<CalendarDay>> {
    val firstDow = dayOfWeekIndex(year, month, 1)
    val totalDays = daysInMonth(year, month)
    val prevMonth = if (month == 1) 12 else month - 1
    val prevYear = if (month == 1) year - 1 else year
    val prevDays = daysInMonth(prevYear, prevMonth)

    val cells = mutableListOf<CalendarDay>()
    for (i in firstDow - 1 downTo 0) {
        cells.add(CalendarDay(prevYear, prevMonth, prevDays - i, false))
    }
    for (d in 1..totalDays) {
        cells.add(CalendarDay(year, month, d, true))
    }
    val nextMonth = if (month == 12) 1 else month + 1
    val nextYear = if (month == 12) year + 1 else year
    var nextDay = 1
    while (cells.size % 7 != 0) {
        cells.add(CalendarDay(nextYear, nextMonth, nextDay++, false))
    }
    return cells.chunked(7)
}

// ── Constants ───────────────────────────────────────────────────────────────

internal const val PAGER_MONTH_RANGE = 240  // ±10 years
