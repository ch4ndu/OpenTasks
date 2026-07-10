package com.udnahc.opentasks.domain.usecase.countdown

import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountdownOccurrenceTest {
    @Test
    fun countdownModeProjectsNextOccurrenceWithoutChangingOriginalTarget() {
        val originalTarget = dateMillis(2026, 5, 1)
        val countdown = countdown(
            targetDate = originalTarget,
            recurrenceType = RecurrenceType.DAILY,
            countingMode = CountingMode.COUNTDOWN,
        )

        val projected = projectCountdownOccurrence(countdown, LocalDate(2026, 5, 4))

        assertEquals(dateMillis(2026, 5, 4), projected.effectiveTargetDate)
        assertEquals(0, projected.daysUntil)
        assertEquals(originalTarget, countdown.targetDate)
        assertEquals(originalTarget, projected.countdown.targetDate)
    }

    @Test
    fun countUpModeProjectsLatestReachedOccurrence() {
        val countdown = countdown(
            targetDate = dateMillis(2026, 1, 15),
            recurrenceType = RecurrenceType.MONTHLY,
            countingMode = CountingMode.COUNT_UP,
        )

        val projected = projectCountdownOccurrence(countdown, LocalDate(2026, 3, 14))

        assertEquals(dateMillis(2026, 2, 15), projected.effectiveTargetDate)
        assertEquals(-27, projected.daysUntil)
    }

    @Test
    fun countUpBeforeFirstOccurrenceKeepsOriginalTarget() {
        val target = dateMillis(2026, 6, 1)
        val countdown = countdown(
            targetDate = target,
            recurrenceType = RecurrenceType.YEARLY,
            countingMode = CountingMode.COUNT_UP,
        )

        assertEquals(
            target,
            latestCountdownOccurrenceOnOrBefore(countdown, LocalDate(2026, 5, 1)),
        )
    }

    @Test
    fun recurrenceTypesAndIntervalsUseCalendarBoundaries() {
        assertEquals(
            dateMillis(2026, 5, 7),
            nextCountdownOccurrenceOnOrAfter(
                countdown(dateMillis(2026, 5, 1), RecurrenceType.DAILY, interval = 3),
                LocalDate(2026, 5, 6),
            ),
        )
        assertEquals(
            dateMillis(2026, 5, 18),
            nextCountdownOccurrenceOnOrAfter(
                countdown(dateMillis(2026, 4, 20), RecurrenceType.WEEKLY, interval = 2),
                LocalDate(2026, 5, 5),
            ),
        )
        assertEquals(
            dateMillis(2026, 3, 15),
            nextCountdownOccurrenceOnOrAfter(
                countdown(dateMillis(2026, 1, 15), RecurrenceType.MONTHLY),
                LocalDate(2026, 3, 14),
            ),
        )
        assertEquals(
            dateMillis(2026, 6, 1),
            nextCountdownOccurrenceOnOrAfter(
                countdown(dateMillis(2024, 6, 1), RecurrenceType.YEARLY),
                LocalDate(2026, 5, 1),
            ),
        )
        assertEquals(
            dateMillis(2026, 5, 4),
            nextCountdownOccurrenceOnOrAfter(
                countdown(dateMillis(2026, 5, 1), RecurrenceType.EVERY_WEEKDAY),
                LocalDate(2026, 5, 2),
            ),
        )
        assertEquals(
            dateMillis(2026, 5, 1),
            nextCountdownOccurrenceOnOrAfter(
                countdown(dateMillis(2026, 5, 1), RecurrenceType.NONE),
                LocalDate(2026, 5, 20),
            ),
        )
    }

    @Test
    fun smartListVisibilityHonorsEachLeadWindow() {
        val target = dateMillis(2026, 5, 10)

        assertTrue(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.ON_THE_DAY), LocalDate(2026, 5, 10)))
        assertFalse(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.ON_THE_DAY), LocalDate(2026, 5, 9)))
        assertTrue(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.THREE_DAYS_EARLY), LocalDate(2026, 5, 7)))
        assertFalse(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.THREE_DAYS_EARLY), LocalDate(2026, 5, 6)))
        assertTrue(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.SEVEN_DAYS_EARLY), LocalDate(2026, 5, 3)))
        assertFalse(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.SEVEN_DAYS_EARLY), LocalDate(2026, 5, 2)))
        assertTrue(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.ALWAYS), LocalDate(2027, 1, 1)))
        assertFalse(isCountdownVisibleInList(countdown(target, visibility = SmartListVisibility.DO_NOT_SHOW), LocalDate(2026, 5, 10)))
    }

    @Test
    fun recurringVisibilityUsesTheNextOccurrence() {
        val countdown = countdown(
            targetDate = dateMillis(2025, 5, 10),
            recurrenceType = RecurrenceType.YEARLY,
            visibility = SmartListVisibility.THREE_DAYS_EARLY,
        )

        assertTrue(isCountdownVisibleInList(countdown, LocalDate(2026, 5, 7)))
    }

    @Test
    fun calendarProjectionProducesExactlyOneEffectiveOccurrencePerCountdown() {
        val countdown = countdown(
            targetDate = dateMillis(2025, 5, 10),
            recurrenceType = RecurrenceType.YEARLY,
        )

        val tasks = projectCountdownCalendarTasks(listOf(countdown), LocalDate(2026, 5, 1))

        assertEquals(1, tasks.size)
        assertEquals("countdown_event", tasks.single().id)
        assertEquals(dateMillis(2026, 5, 10), tasks.single().deadline)
        assertEquals(dateMillis(2025, 5, 10), countdown.targetDate)
    }

    private fun countdown(
        targetDate: Long,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        interval: Int = 1,
        countingMode: CountingMode = CountingMode.COUNTDOWN,
        visibility: SmartListVisibility = SmartListVisibility.ALWAYS,
    ) = Countdown(
        id = "event",
        title = "Event",
        targetDate = targetDate,
        recurrenceType = recurrenceType,
        recurrenceInterval = interval,
        countingMode = countingMode,
        smartListVisibility = visibility,
    )

    private fun dateMillis(year: Int, month: Int, day: Int): Long =
        startOfDayLocalMillis(year, month, day)
}
