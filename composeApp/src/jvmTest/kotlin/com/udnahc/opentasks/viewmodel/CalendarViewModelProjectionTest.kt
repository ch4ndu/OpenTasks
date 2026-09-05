package com.udnahc.opentasks.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.task.CalendarRenderState
import com.udnahc.opentasks.domain.usecase.task.CountingCalendarFormatter
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelProjectionTest : MainDispatcherRule() {
    @Test
    fun firstModeSwitchRenderIsCoherentAndYearDoesNoFormatting() = runTest(dispatcher) {
        val dayStart = startOfDayLocalMillis(2026, 5, 4)
        val dayKey = dayKey(dayStart)
        val formatter = CountingCalendarFormatter()
        val settings = FakeAppSettingsRepository(
            mapOf("calendar_view_preference" to CalendarViewPreference.YEAR.name),
        )
        val viewModel = calendarViewModel(
            taskRepository = FakeTaskRepository(listOf(testTask(id = "task", deadline = dayStart))),
            settingsRepository = settings,
            localDaySignal = LocalDaySignal(currentDate = { LocalDate(2026, 5, 4) }),
            formatter = formatter,
        )

        viewModel.calendarRenderState.test {
            val year = awaitMatching {
                it.viewPreference == CalendarViewPreference.YEAR && it.taskDayKeys == setOf(dayKey)
            }
            assertTrue(year.calendarDaysByDay.isEmpty())
            assertTrue(year.timelineHourLabels.isEmpty())
            assertEquals(0, formatter.nonHourFormatCalls)
            assertEquals(0, formatter.hourFormatCalls)

            settings.setValue("calendar_view_preference", CalendarViewPreference.DAY.name)
            runCurrent()

            val day = awaitItem()
            assertEquals(CalendarViewPreference.DAY, day.viewPreference)
            assertEquals(LocalDate(2026, 5, 4), day.today)
            assertEquals(setOf(dayKey), day.taskDayKeys)
            assertEquals("task", day.calendarDaysByDay.getValue(dayKey).rows.single().task.id)
            assertTrue(day.calendarDaysByDay.getValue(dayKey).isToday)
            assertEquals(24, day.timelineHourLabels.size)
            assertTrue(formatter.nonHourFormatCalls > 0)
            assertEquals(24, formatter.hourFormatCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun continuedAndRapidRolloversEmitOnlyCoherentRelevantSnapshots() = runTest(dispatcher) {
        var currentDay = LocalDate(2026, 5, 4)
        val localDaySignal = LocalDaySignal(currentDate = { currentDay })
        val countdown = testCountdown(
            id = "daily",
            targetDate = startOfDayLocalMillis(2026, 5, 4),
        ).copy(recurrenceType = RecurrenceType.DAILY)
        val viewModel = calendarViewModel(
            countdownRepository = FakeCountdownRepository(listOf(countdown)),
            settingsRepository = FakeAppSettingsRepository(
                mapOf("calendar_view_preference" to CalendarViewPreference.DAY.name),
            ),
            localDaySignal = localDaySignal,
        )

        viewModel.calendarRenderState.test {
            val initial = awaitMatching { render ->
                render.today == currentDay && render.hasOnlyCountdownOn(currentDay)
            }
            assertCoherentDailyCountdown(initial, currentDay)

            currentDay = LocalDate(2026, 5, 5)
            localDaySignal.refresh()
            runCurrent()

            val rolled = awaitItem()
            assertCoherentDailyCountdown(rolled, currentDay)

            currentDay = LocalDate(2026, 5, 6)
            localDaySignal.refresh()
            currentDay = LocalDate(2026, 5, 7)
            localDaySignal.refresh()
            runCurrent()

            val rapid = awaitItem()
            assertCoherentDailyCountdown(rapid, LocalDate(2026, 5, 7))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun subscriptionRestartRefreshesTheFirstNewDaySnapshotCoherently() = runTest(dispatcher) {
        var currentDay = LocalDate(2026, 5, 4)
        val localDaySignal = LocalDaySignal(currentDate = { currentDay })
        val countdown = testCountdown(
            id = "daily",
            targetDate = startOfDayLocalMillis(2026, 5, 4),
        ).copy(recurrenceType = RecurrenceType.DAILY)
        val viewModel = calendarViewModel(
            countdownRepository = FakeCountdownRepository(listOf(countdown)),
            settingsRepository = FakeAppSettingsRepository(
                mapOf("calendar_view_preference" to CalendarViewPreference.DAY.name),
            ),
            localDaySignal = localDaySignal,
        )

        viewModel.calendarRenderState.test {
            val initial = awaitMatching { render -> render.hasOnlyCountdownOn(currentDay) }
            assertCoherentDailyCountdown(initial, currentDay)
            cancelAndIgnoreRemainingEvents()
        }
        advanceTimeBy(5_001)
        runCurrent()

        currentDay = LocalDate(2026, 5, 5)
        localDaySignal.refresh()

        viewModel.calendarRenderState.test {
            val retained = awaitItem()
            assertCoherentDailyCountdown(retained, LocalDate(2026, 5, 4))
            runCurrent()

            val refreshed = awaitItem()
            assertCoherentDailyCountdown(refreshed, currentDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun calendarViewModel(
        taskRepository: FakeTaskRepository = FakeTaskRepository(),
        countdownRepository: FakeCountdownRepository = FakeCountdownRepository(),
        settingsRepository: FakeAppSettingsRepository,
        localDaySignal: LocalDaySignal,
        formatter: CountingCalendarFormatter = CountingCalendarFormatter(),
    ): CalendarViewModel = CalendarViewModel(
        observeTasksByDay = ObserveTasksByDayUseCase(taskRepository),
        observeAllCountdowns = ObserveAllCountdownsUseCase(countdownRepository),
        observeAllCategories = ObserveAllCategoriesUseCase(FakeCategoryRepository()),
        toggleTaskCompleteAction = ToggleTaskCompleteAction(
            taskRepository,
            ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository),
        ),
        observeCalendarViewPreference = ObserveCalendarViewPreferenceUseCase(settingsRepository),
        saveCalendarViewPreference = SaveCalendarViewPreferenceAction(settingsRepository),
        observeCalendarListDisplayModePreference =
            ObserveCalendarListDisplayModePreferenceUseCase(settingsRepository),
        saveCalendarListDisplayModePreference =
            SaveCalendarListDisplayModePreferenceAction(settingsRepository),
        localDaySignal = localDaySignal,
        ioDispatcher = dispatcher,
        projectionDispatcher = dispatcher,
        dateTimeFormatter = formatter,
    )

    private fun CalendarRenderState.hasOnlyCountdownOn(date: LocalDate): Boolean {
        val key = dayKey(startOfDayLocalMillis(date.year, date.monthNumber, date.dayOfMonth))
        return calendarDaysByDay[key]?.rows?.singleOrNull()?.task?.id == "countdown_daily" &&
            calendarDaysByDay.values.sumOf { it.rows.size } == 1
    }

    private fun assertCoherentDailyCountdown(render: CalendarRenderState, date: LocalDate) {
        val key = dayKey(startOfDayLocalMillis(date.year, date.monthNumber, date.dayOfMonth))
        assertEquals(CalendarViewPreference.DAY, render.viewPreference)
        assertEquals(date, render.today)
        assertEquals(setOf(key), render.taskDayKeys)
        assertEquals(setOf(key), render.calendarDaysByDay.keys)
        assertEquals("countdown_daily", render.calendarDaysByDay.getValue(key).rows.single().task.id)
        assertTrue(render.calendarDaysByDay.getValue(key).isToday)
        assertFalse(render.timelineHourLabels.isEmpty())
    }

    private suspend fun ReceiveTurbine<CalendarRenderState>.awaitMatching(
        predicate: (CalendarRenderState) -> Boolean,
    ): CalendarRenderState {
        repeat(10) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching calendar render state emitted")
    }
}
