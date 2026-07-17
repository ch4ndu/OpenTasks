package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.action.task.ImportCsvTasksAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.task.FetchCalendarEventsUseCase
import com.udnahc.opentasks.domain.usecase.task.ParseCsvUseCase
import com.udnahc.opentasks.domain.usecase.task.ParseIcsUseCase
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeTagRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest : MainDispatcherRule() {
    @Test
    fun csvImportViewModelImportsRowsAndResetsState() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val categoryRepository = FakeCategoryRepository()
        val viewModel = ImportCsvViewModel(
            parseCsv = ParseCsvUseCase(),
            importAction = ImportCsvTasksAction(
                taskRepository = taskRepository,
                categoryRepository = categoryRepository,
                scheduleTaskRemindersAction = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.importFromCsvContent("tasks.csv", csvWithSingleTask())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(1, state.importedCount)
        assertNull(state.error)
        assertEquals("tasks.csv", state.fileName)
        assertEquals("Imported task", taskRepository.inserted.single().title)
        assertEquals("Projects", categoryRepository.inserted.single().name)

        viewModel.resetState()
        assertEquals(ImportCsvUiState(), viewModel.uiState.value)
    }

    @Test
    fun csvImportViewModelReportsEmptyFile() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val viewModel = ImportCsvViewModel(
            parseCsv = ParseCsvUseCase(),
            importAction = ImportCsvTasksAction(
                taskRepository = taskRepository,
                categoryRepository = FakeCategoryRepository(),
                scheduleTaskRemindersAction = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.importFromCsvContent("empty.csv", "Title,Due Date\n")
        advanceUntilIdle()

        assertEquals(ImportErrorType.EMPTY_CSV_FILE, viewModel.uiState.value.error?.type)
        assertTrue(taskRepository.inserted.isEmpty())
    }

    @Test
    fun csvImportDoubleTapStartsOneImport() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val viewModel = ImportCsvViewModel(
            parseCsv = ParseCsvUseCase(),
            importAction = ImportCsvTasksAction(
                taskRepository = taskRepository,
                categoryRepository = FakeCategoryRepository(),
                scheduleTaskRemindersAction = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.importFromCsvContent("tasks.csv", csvWithSingleTask())
        viewModel.importFromCsvContent("tasks.csv", csvWithSingleTask())
        advanceUntilIdle()

        assertEquals(1, taskRepository.inserted.size)
        assertEquals(1, viewModel.uiState.value.importedCount)
    }

    @Test
    fun csvImportUsesDueDateAsTheDeadlineWhenStartDateIsBlank() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val viewModel = ImportCsvViewModel(
            parseCsv = ParseCsvUseCase(),
            importAction = ImportCsvTasksAction(
                taskRepository = taskRepository,
                categoryRepository = FakeCategoryRepository(),
                scheduleTaskRemindersAction = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.importFromCsvContent(
            "due-only.csv",
            """
            Title,Content,List Name,Start Date,Due Date,Is All Day,Priority,Status,Reminder,Repeat,Created Time
            "Due only","","Inbox","","2026-05-04T11:00:00+0000","false","0","0","","","2026-05-01T09:00:00+0000"
            """.trimIndent(),
        )
        advanceUntilIdle()

        assertTrue(taskRepository.inserted.single().deadline != null)
        assertNull(taskRepository.inserted.single().endDeadline)
    }

    @Test
    fun icsImportViewModelReportsEmptyFile() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val viewModel = ImportIcsViewModel(
            parseIcs = ParseIcsUseCase(),
            importAction = importCalendarEventsAction(taskRepository),
            ioDispatcher = dispatcher,
        )

        viewModel.importFromIcsContent("empty.ics", "BEGIN:VCALENDAR\nEND:VCALENDAR")
        advanceUntilIdle()

        assertEquals(ImportErrorType.EMPTY_ICS_FILE, viewModel.uiState.value.error?.type)
        assertTrue(taskRepository.inserted.isEmpty())
    }

    @Test
    fun calendarImportViewModelUsesCalendarMonthBoundsAndPermissionState() = runTest(dispatcher) {
        val timeZone = TimeZone.currentSystemDefault()
        val nowUtc = LocalDateTime(2026, 3, 31, 10, 30)
            .toInstant(timeZone)
            .toEpochMilliseconds()
        val provider = FakeCalendarProvider(
            events = listOf(
                CalendarEvent(
                    externalId = "event",
                    title = "Planning",
                    description = "",
                    startTimeUtcMillis = nowUtc,
                    endTimeUtcMillis = null,
                    calendarName = "Work",
                    isAllDay = false,
                )
            )
        )
        val taskRepository = FakeTaskRepository()
        val viewModel = ImportCalendarViewModel(
            fetchCalendarEvents = FetchCalendarEventsUseCase(provider),
            checkCalendarPermission = CheckCalendarPermissionUseCase(provider),
            importAction = importCalendarEventsAction(taskRepository),
            ioDispatcher = dispatcher,
            nowUtcMillisProvider = { nowUtc },
        )

        viewModel.checkPermission()
        advanceUntilIdle()
        assertEquals(CalendarPermissionStatus.GRANTED, viewModel.uiState.value.permissionStatus)

        viewModel.updateRangeValue(250)
        viewModel.updateRangeUnit(ImportRangeUnit.MONTHS)
        assertEquals(99, viewModel.uiState.value.rangeValue)
        viewModel.updateRangeValue(1)
        viewModel.importEvents()
        advanceUntilIdle()

        val startLocal = Instant.fromEpochMilliseconds(provider.lastStartUtcMillis ?: 0L)
            .toLocalDateTime(timeZone)
        val endLocal = Instant.fromEpochMilliseconds(provider.lastEndUtcMillis ?: 0L)
            .toLocalDateTime(timeZone)
        assertEquals(LocalDateTime(2026, 2, 28, 10, 30), startLocal)
        assertEquals(LocalDateTime(2026, 4, 30, 10, 30), endLocal)
        assertEquals(1, viewModel.uiState.value.importedCount)
        assertEquals("Planning", taskRepository.inserted.single().title)
    }

    private fun importCalendarEventsAction(taskRepository: FakeTaskRepository): ImportCalendarEventsAction {
        val tagRepository = FakeTagRepository()
        return ImportCalendarEventsAction(
            taskRepository = taskRepository,
            categoryRepository = FakeCategoryRepository(),
            tagRepository = tagRepository,
            addTagAction = AddTagAction(tagRepository),
            scheduleTaskRemindersAction = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository),
        )
    }

    private fun csvWithSingleTask(): String =
        """
        Title,Content,List Name,Start Date,Due Date,Is All Day,Priority,Status,Reminder,Repeat,Created Time
        "Imported task","Body","Projects","2026-05-04T10:00:00+0000","2026-05-04T11:00:00+0000","false","5","0","-PT30M","FREQ=WEEKLY","2026-05-01T09:00:00+0000"
        """.trimIndent()
}

private class FakeCalendarProvider(
    private val events: List<CalendarEvent> = emptyList(),
    private val permissionStatus: CalendarPermissionStatus = CalendarPermissionStatus.GRANTED,
) : CalendarProvider {
    var lastStartUtcMillis: Long? = null
    var lastEndUtcMillis: Long? = null

    override fun isAvailable(): Boolean = true
    override suspend fun checkPermission(): CalendarPermissionStatus = permissionStatus
    override suspend fun requestPermission(): CalendarPermissionStatus = permissionStatus

    override suspend fun fetchEvents(startUtcMillis: Long, endUtcMillis: Long): List<CalendarEvent> {
        lastStartUtcMillis = startUtcMillis
        lastEndUtcMillis = endUtcMillis
        return events
    }
}
