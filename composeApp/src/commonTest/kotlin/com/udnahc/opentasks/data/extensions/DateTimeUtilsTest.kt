package com.udnahc.opentasks.data.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeUtilsTest {
    @Test
    fun preEpochLocalMillisUsesThePreviousCivilDay() {
        val midnight = startOfDayLocalMillis(1969, 12, 31)

        assertEquals(-1, dayKey(midnight))
        assertEquals(-1, dayKey(midnight + 1))
        assertEquals(Triple(1969, 12, 31), Triple(extractYear(midnight), extractMonth(midnight), extractDay(midnight)))
    }

    @Test
    fun monthlyAndYearlyAnchorsClampThenRestoreAcrossIntervals() {
        val januaryThirtyFirst = computeLocalMillis(2026, 1, 31, 9, 30)
        val february = computeNextDeadlineLocal(januaryThirtyFirst, "MONTHLY", anchorDay = 31)
        val march = computeNextDeadlineLocal(february, "MONTHLY", anchorDay = 31)
        assertEquals(computeLocalMillis(2026, 2, 28, 9, 30), february)
        assertEquals(computeLocalMillis(2026, 3, 31, 9, 30), march)

        val leapDay = computeLocalMillis(2024, 2, 29, 9, 30)
        val nonLeap = computeNextDeadlineLocal(leapDay, "YEARLY", anchorDay = 29)
        val restoredLeap = generateSequence(leapDay) {
            computeNextDeadlineLocal(it, "YEARLY", anchorDay = 29)
        }.drop(4).first()
        assertEquals(computeLocalMillis(2025, 2, 28, 9, 30), nonLeap)
        assertEquals(computeLocalMillis(2028, 2, 29, 9, 30), restoredLeap)

        assertEquals(
            computeLocalMillis(2026, 3, 31, 9, 30),
            computeNextDeadlineLocal(januaryThirtyFirst, "MONTHLY", interval = 2, anchorDay = 31),
        )
        assertEquals(
            computeLocalMillis(2028, 2, 29, 9, 30),
            computeNextDeadlineLocal(leapDay, "YEARLY", interval = 4, anchorDay = 29),
        )
    }

    @Test
    fun utcAndLocalRecurrencePreserveCivilTimeAcrossTimezoneAndDstBoundaries() {
        val beforeSpringTransition = computeLocalMillis(2026, 3, 8, 1, 30)
        val utc = localToUtc(beforeSpringTransition)

        assertEquals(beforeSpringTransition, utcToLocal(utc))
        assertEquals(
            computeLocalMillis(2026, 3, 9, 1, 30),
            utcToLocal(computeNextDeadlineUtc(utc, "DAILY")),
        )

        val preEpoch = computeLocalMillis(1969, 12, 31, 23, 30)
        assertEquals(preEpoch, utcToLocal(localToUtc(preEpoch)))
        assertEquals(
            computeLocalMillis(1970, 1, 1, 23, 30),
            computeNextDeadlineLocal(preEpoch, "DAILY"),
        )
    }
}
