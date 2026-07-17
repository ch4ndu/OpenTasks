package com.udnahc.opentasks.widget

import com.udnahc.opentasks.data.extensions.localMillisToUtcMillis
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.RecurrenceType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetDataProviderTest {

    @Test
    fun recurringCountdownUsesEffectiveOccurrenceBeforeWidgetBucketing() {
        val storedUtcTarget = localMillisToUtcMillis(startOfDayLocalMillis(2025, 1, 15))
        val countdown = Countdown(
            id = "annual",
            title = "Annual",
            targetDate = storedUtcTarget,
            recurrenceType = RecurrenceType.YEARLY,
        )

        val effective = effectiveCountdownTargetLocalMillis(countdown, LocalDate(2026, 2, 1))

        assertEquals(startOfDayLocalMillis(2027, 1, 15), effective)
    }
}
