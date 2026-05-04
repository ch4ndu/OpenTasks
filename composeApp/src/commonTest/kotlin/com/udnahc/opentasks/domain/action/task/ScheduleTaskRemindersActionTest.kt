package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class ScheduleTaskRemindersActionTest {
    @Test
    fun legacyMonthReminderUsesCalendarMonthAndPreservesLocalTime() {
        val timeZone = TimeZone.currentSystemDefault()
        val deadlineUtc = LocalDateTime(2026, 3, 31, 10, 30)
            .toInstant(timeZone)
            .toEpochMilliseconds()

        val triggerUtc = legacyReminderTriggerUtcMillis(
            deadlineUtcMillis = deadlineUtc,
            value = 1,
            unit = NotifyBeforeUnit.MONTHS,
        )
        val triggerLocal = Instant.fromEpochMilliseconds(triggerUtc).toLocalDateTime(timeZone)

        assertEquals(2026, triggerLocal.year)
        assertEquals(Month.FEBRUARY, triggerLocal.month)
        assertEquals(28, triggerLocal.day)
        assertEquals(10, triggerLocal.hour)
        assertEquals(30, triggerLocal.minute)
    }

    @Test
    fun legacyWeekReminderUsesSevenCalendarDaysAndPreservesLocalTime() {
        val timeZone = TimeZone.currentSystemDefault()
        val deadlineUtc = LocalDateTime(2026, 5, 11, 9, 15)
            .toInstant(timeZone)
            .toEpochMilliseconds()

        val triggerUtc = legacyReminderTriggerUtcMillis(
            deadlineUtcMillis = deadlineUtc,
            value = 1,
            unit = NotifyBeforeUnit.WEEKS,
        )
        val triggerLocal = Instant.fromEpochMilliseconds(triggerUtc).toLocalDateTime(timeZone)

        assertEquals(2026, triggerLocal.year)
        assertEquals(Month.MAY, triggerLocal.month)
        assertEquals(4, triggerLocal.day)
        assertEquals(9, triggerLocal.hour)
        assertEquals(15, triggerLocal.minute)
    }
}
