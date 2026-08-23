package com.udnahc.opentasks.ui.screens.calendar

import com.udnahc.opentasks.WidgetCalendarDate
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.startOfWeekLocalMillis
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

    @Test
    fun monthCellsCacheLeapDayMillisAndDayKeyWithoutChangingDataClassEquality() {
        val monthCells = buildMonthWeeks(2028, 2).flatten()
        val januaryBoundary = monthCells
            .single { !it.isCurrentMonth && it.year == 2028 && it.month == 1 && it.day == 31 }
        val leapDay = monthCells
            .single { it.year == 2028 && it.month == 2 && it.day == 29 }
        val copy = leapDay.copy()

        assertEquals(startOfDayLocalMillis(2028, 1, 31), januaryBoundary.localMillis)
        assertEquals(dayKey(januaryBoundary.localMillis), januaryBoundary.dayKey)
        assertEquals(startOfDayLocalMillis(2028, 2, 29), leapDay.localMillis)
        assertEquals(dayKey(leapDay.localMillis), leapDay.dayKey)
        assertEquals(leapDay, copy)
        assertEquals(leapDay.localMillis, copy.localMillis)
        assertEquals(leapDay.dayKey, copy.dayKey)
    }

    @Test
    fun weekStripProjectionCachesDaysAcrossYearBoundary() {
        val sunday = startOfWeekLocalMillis(startOfDayLocalMillis(2026, 12, 31))
        val days = buildWeekStripDays(sunday)

        assertEquals(listOf(27, 28, 29, 30, 31, 1, 2), days.map { it.dayNumber })
        assertEquals(startOfDayLocalMillis(2027, 1, 1), days[5].millis)
        assertEquals(days.map { dayKey(it.millis) }, days.map { it.dayKey })
        assertEquals(sunday + 6 * com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY, days.last().millis)
    }
}
