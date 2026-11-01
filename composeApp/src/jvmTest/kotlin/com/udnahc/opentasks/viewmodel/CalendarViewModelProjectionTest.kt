package com.udnahc.opentasks.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelProjectionTest : MainDispatcherRule() {
    @Test
    fun classicTimelineRetryRecoversWithoutStartingUnusedCategoryReads() = runTest(dispatcher) {
        for (mode in listOf(CalendarViewPreference.DAY, CalendarViewPreference.THREE_DAY, CalendarViewPreference.WEEK)) {
            val failure = CompletableDeferred<Unit>()
            val taskSubscriptions = AtomicInteger()
            val categorySubscriptions = AtomicInteger()
            val repository = object : TaskRepository by FakeTaskRepository() {
                override fun getAllTasks() = flow {
                    val subscription = taskSubscriptions.incrementAndGet()
                    emit(listOf(testTask(id = "task", deadline = startOfDayLocalMillis(2026, 5, 4))))
                    if (subscription == 1) {
                        failure.await()
                        error("Injected read failure")
                    }
                }
            }
            val categories = object : CategoryRepository by FakeCategoryRepository() {
                override fun getAllCategories() = flow<List<Category>> {
                    categorySubscriptions.incrementAndGet()
                    error("This mode must not observe categories")
                }
            }
            val viewModel = calendarViewModel(
                taskRepository = repository,
                categoryRepository = categories,
                settingsRepository = FakeAppSettingsRepository(mapOf("calendar_view_preference" to mode.name)),
                localDaySignal = LocalDaySignal(currentDate = { LocalDate(2026, 5, 4) }),
            )
            val renders = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.calendarRenderState.collect() }
            viewModel.loadState.test {
                while (awaitItem() != CalendarLoadState.READY) Unit
                advanceTimeBy(6_000)
                runCurrent()
                failure.complete(Unit)
                while (awaitItem() != CalendarLoadState.ERROR) Unit
                val readsBeforeRetry = taskSubscriptions.get()
                viewModel.retryLoad()
                while (awaitItem() != CalendarLoadState.READY) Unit
                assertEquals(CalendarLoadState.READY, calendarLoadStateForView(
                    viewModel.loadState.value, viewModel.categoryLoadState.value, mode, reimagined = false,
                ))
                assertEquals(CalendarLoadState.LOADING, viewModel.categoryLoadState.value)
                assertEquals(0, categorySubscriptions.get())
                // Retry must restart the read. WhileSubscribed may also restart it while
                // real Default-dispatcher cancellation overlaps virtual sharing timeouts.
                assertTrue(taskSubscriptions.get() > readsBeforeRetry, "Retry did not restart $mode task observation")
                cancelAndIgnoreRemainingEvents()
            }
            renders.cancel()
        }
    }

    @Test
    fun categoryLoadingAndErrorsGateOnlyViewsThatRenderCategoryNames() {
        for (reimagined in listOf(false, true)) {
            for (mode in CalendarViewPreference.entries) {
                val requiresCategories = if (reimagined) mode != CalendarViewPreference.YEAR else
                    mode in listOf(CalendarViewPreference.LIST, CalendarViewPreference.MONTH)
                for (categories in listOf(CalendarLoadState.LOADING, CalendarLoadState.ERROR)) {
                    assertEquals(if (requiresCategories) categories else CalendarLoadState.READY,
                        calendarLoadStateForView(CalendarLoadState.READY, categories, mode, reimagined))
                }
                assertEquals(CalendarLoadState.ERROR,
                    calendarLoadStateForView(CalendarLoadState.ERROR, CalendarLoadState.READY, mode, reimagined))
            }
        }
    }

    @Test
    fun categoryFailureStillBlocksMonthAndRetryRestartsItsRequiredRead() = runTest(dispatcher) {
        var subscriptions = 0
        val categories = object : CategoryRepository by FakeCategoryRepository() {
            override fun getAllCategories() = flow<List<Category>> {
                subscriptions += 1
                if (subscriptions == 1) error("Injected category read failure")
                emit(emptyList())
            }
        }
        val viewModel = calendarViewModel(
            categoryRepository = categories,
            settingsRepository = FakeAppSettingsRepository(),
            localDaySignal = LocalDaySignal(currentDate = { LocalDate(2026, 5, 4) }),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.categoryNames.collect() }
        combine(viewModel.loadState, viewModel.categoryLoadState, viewModel.calendarRenderState) { content, categoryState, render ->
            calendarLoadStateForView(content, categoryState, render.viewPreference, reimagined = false)
        }.test {
            while (awaitItem() != CalendarLoadState.ERROR) Unit
            viewModel.retryLoad()
            while (awaitItem() != CalendarLoadState.READY) Unit
            assertEquals(2, subscriptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sameDayRefreshReformatsRowsAndHourLabelsWithoutTaskChanges() = runTest(dispatcher) {
        var context = "first"
        val day = LocalDate(2026, 5, 4)
        val dayStart = startOfDayLocalMillis(2026, 5, 4)
        val signal = LocalDaySignal(currentDate = { day })
        val formatter = object : DateTimeTextFormatter by EnglishDateTimeFormatter {
            override val formattingContextKey: String get() = context
            override fun formatHour(hour: Int) = "$context-hour-$hour"
            override fun formatTime(localMillis: Long) = "$context-time"
        }
        val viewModel = calendarViewModel(
            taskRepository = FakeTaskRepository(listOf(testTask(id = "task", deadline = dayStart + 9 * MILLIS_PER_HOUR))),
            settingsRepository = FakeAppSettingsRepository(mapOf("calendar_view_preference" to CalendarViewPreference.DAY.name)),
            localDaySignal = signal,
            formatter = formatter,
        )
        viewModel.calendarRenderState.test {
            awaitMatching { it.timelineHourLabels.firstOrNull() == "first-hour-0" }
            context = "changed"
            signal.refresh()
            val refreshed = awaitMatching { it.timelineHourLabels.firstOrNull() == "changed-hour-0" }
            assertEquals(day, refreshed.today)
            assertEquals("changed-time", refreshed.calendarDaysByDay.getValue(dayKey(dayStart)).rows.single().cardTimeText)
            signal.refresh()
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedCalendarReadRetainsRowsAndExplicitRetryRestartsObservation() = runTest(dispatcher) {
        val dayStart = startOfDayLocalMillis(2026, 5, 4)
        val key = dayKey(dayStart)
        val task = testTask(id = "retained", deadline = dayStart)
        val failure = CompletableDeferred<Unit>()
        var subscriptions = 0
        val repository = object : TaskRepository by FakeTaskRepository() {
            override fun getAllTasks() = flow {
                subscriptions += 1
                emit(listOf(task))
                if (subscriptions == 1) {
                    failure.await()
                    error("Injected read failure")
                }
            }
        }
        val viewModel = calendarViewModel(
            taskRepository = repository,
            settingsRepository = FakeAppSettingsRepository(),
            localDaySignal = LocalDaySignal(currentDate = { LocalDate(2026, 5, 4) }),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.calendarRenderState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.categoryNames.collect() }

        viewModel.loadState.test {
            while (awaitItem() != CalendarLoadState.READY) Unit
            viewModel.calendarRenderState.first { it.calendarDaysByDay[key]?.rows?.isNotEmpty() == true }
            assertEquals("retained", viewModel.calendarRenderState.value.calendarDaysByDay.getValue(key).rows.single().task.id)
            failure.complete(Unit)
            while (awaitItem() != CalendarLoadState.ERROR) Unit
            assertEquals("retained", viewModel.calendarRenderState.value.calendarDaysByDay.getValue(key).rows.single().task.id)
            viewModel.retryLoad()
            while (awaitItem() != CalendarLoadState.READY) Unit
            assertEquals(2, subscriptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

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
        taskRepository: TaskRepository = FakeTaskRepository(),
        categoryRepository: CategoryRepository = FakeCategoryRepository(),
        countdownRepository: FakeCountdownRepository = FakeCountdownRepository(),
        settingsRepository: FakeAppSettingsRepository,
        localDaySignal: LocalDaySignal,
        formatter: DateTimeTextFormatter = CountingCalendarFormatter(),
    ): CalendarViewModel = CalendarViewModel(
        observeTasksByDay = ObserveTasksByDayUseCase(taskRepository),
        observeAllCountdowns = ObserveAllCountdownsUseCase(countdownRepository),
        observeAllCategories = ObserveAllCategoriesUseCase(categoryRepository),
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
