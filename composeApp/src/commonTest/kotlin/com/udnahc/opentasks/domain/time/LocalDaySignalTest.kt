package com.udnahc.opentasks.domain.time

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDaySignalTest {
    @Test
    fun emitsChangedDateOnPeriodicCheck() = runTest {
        var currentDate = LocalDate(2026, 5, 4)
        val signal = LocalDaySignal(
            currentDate = { currentDate },
            checkInterval = 1.seconds,
        )

        signal.dates.test {
            assertEquals(LocalDate(2026, 5, 4), awaitItem())

            currentDate = LocalDate(2026, 5, 5)
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LocalDate(2026, 5, 5), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
