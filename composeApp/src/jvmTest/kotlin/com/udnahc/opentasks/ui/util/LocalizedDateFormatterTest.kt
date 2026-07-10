package com.udnahc.opentasks.ui.util

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizedDateFormatterTest {
    @Test
    fun formatsDateAndWeekdayForExplicitLocales() {
        val date = LocalDate(2026, 5, 4)

        assertEquals("Monday, May 4, 2026", formatLocalizedDateWithWeekday(date, "en-US"))
        assertEquals("Montag, 4. Mai 2026", formatLocalizedDateWithWeekday(date, "de-DE"))
    }
}
