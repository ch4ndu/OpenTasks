package com.udnahc.opentasks.ui.screens.calendar

import com.udnahc.opentasks.WidgetCalendarDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CalendarScreenTest {
    @Test
    fun monthPagerIncludesTheOldestJanuaryAndNewestDecemberFromYearView() {
        val oldestJanuary = monthPagerPageFor(
            todayYear = 2026,
            todayMonth = 5,
            targetYear = 2016,
            targetMonth = 1,
        )
        val newestDecember = monthPagerPageFor(
            todayYear = 2026,
            todayMonth = 5,
            targetYear = 2035,
            targetMonth = 12,
        )

        assertNotNull(oldestJanuary)
        assertNotNull(newestDecember)
        assertEquals(MONTH_PAGER_CENTRE - 124, oldestJanuary)
        assertEquals(MONTH_PAGER_CENTRE + 115, newestDecember)
    }

    @Test
    fun yearEntryUsesTheDateOfTheActiveView() {
        val dates = mapOf(
            CalendarViewType.LIST to CalendarNavigationDate(2021, 1),
            CalendarViewType.YEAR to CalendarNavigationDate(2022, 2),
            CalendarViewType.MONTH to CalendarNavigationDate(2023, 3),
            CalendarViewType.WEEK to CalendarNavigationDate(2024, 4),
            CalendarViewType.THREE_DAY to CalendarNavigationDate(2025, 5),
            CalendarViewType.DAY to CalendarNavigationDate(2026, 6),
        )

        CalendarViewType.entries.forEach { view ->
            assertEquals(
                dates.getValue(view),
                calendarYearEntryDate(
                    currentView = view,
                    listDate = dates.getValue(CalendarViewType.LIST),
                    yearDate = dates.getValue(CalendarViewType.YEAR),
                    monthDate = dates.getValue(CalendarViewType.MONTH),
                    weekDate = dates.getValue(CalendarViewType.WEEK),
                    threeDayDate = dates.getValue(CalendarViewType.THREE_DAY),
                    dayDate = dates.getValue(CalendarViewType.DAY),
                ),
            )
        }
    }

    @Test
    fun widgetNavigationMapsTheExactDateToTheSelectedMonthDay() {
        assertEquals(
            CalendarDay(year = 2028, month = 2, day = 29, isCurrentMonth = true),
            widgetCalendarDay(WidgetCalendarDate(year = 2028, month = 2, day = 29)),
        )
    }
}
