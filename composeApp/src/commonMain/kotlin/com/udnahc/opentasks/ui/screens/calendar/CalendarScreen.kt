package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.domain.usecase.task.CalendarVisibleRange
import kotlinx.datetime.LocalDate
import com.udnahc.opentasks.data.extensions.daysInMonth
import com.udnahc.opentasks.ui.theme.isReimagined
import com.udnahc.opentasks.ui.screens.ReimaginedAddButton
import opentasks.composeapp.generated.resources.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.startOfWeekLocalMillis
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.WidgetCalendarDate
import com.udnahc.opentasks.WidgetNavigationEvent
import com.udnahc.opentasks.domain.usecase.task.CalendarRenderState
import com.udnahc.opentasks.ui.screens.CompleteSeriesDialog
import com.udnahc.opentasks.ui.screens.ModalBusyEffect
import com.udnahc.opentasks.ui.screens.OpenTasksBackButton
import com.udnahc.opentasks.ui.screens.OpenTasksSettingsButton
import com.udnahc.opentasks.ui.screens.OpenTasksTopBar
import com.udnahc.opentasks.ui.screens.OpenTasksTopBarContainerStyle
import com.udnahc.opentasks.ui.screens.SelectedOptionRow
import com.udnahc.opentasks.ui.screens.SyncPullToRefresh
import com.udnahc.opentasks.ui.screens.TaskMutationFailureEffect
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.CalendarViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.calendar_view_day
import opentasks.composeapp.generated.resources.calendar_view_list
import opentasks.composeapp.generated.resources.calendar_view_month
import opentasks.composeapp.generated.resources.calendar_view_three_day
import opentasks.composeapp.generated.resources.calendar_view_week
import opentasks.composeapp.generated.resources.calendar_view_year
import opentasks.composeapp.generated.resources.choose_calendar_view
import opentasks.composeapp.generated.resources.ic_chevron_left
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_schedule
import opentasks.composeapp.generated.resources.next_day
import opentasks.composeapp.generated.resources.next_month
import opentasks.composeapp.generated.resources.next_week
import opentasks.composeapp.generated.resources.next_year
import opentasks.composeapp.generated.resources.previous_day
import opentasks.composeapp.generated.resources.previous_month
import opentasks.composeapp.generated.resources.previous_week
import opentasks.composeapp.generated.resources.previous_year
import opentasks.composeapp.generated.resources.show_card_view
import opentasks.composeapp.generated.resources.show_timeline_view
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── Pager constants ─────────────────────────────────────────────────────────

private const val DAY_PAGER_RANGE = 7300
internal const val DAY_PAGER_CENTRE = DAY_PAGER_RANGE / 2
private const val WEEK_PAGER_RANGE = 1040
internal const val WEEK_PAGER_CENTRE = WEEK_PAGER_RANGE / 2
internal const val YEAR_PAGER_RANGE = 20
internal const val YEAR_PAGER_CENTRE = YEAR_PAGER_RANGE / 2
internal const val MONTH_PAGER_MIN_OFFSET = -(YEAR_PAGER_CENTRE * 12 + 11)
internal const val MONTH_PAGER_MAX_OFFSET =
    ((YEAR_PAGER_RANGE - YEAR_PAGER_CENTRE - 1) * 12) + 11
internal const val MONTH_PAGER_RANGE = MONTH_PAGER_MAX_OFFSET - MONTH_PAGER_MIN_OFFSET + 1
internal const val MONTH_PAGER_CENTRE = -MONTH_PAGER_MIN_OFFSET

// ── View types ──────────────────────────────────────────────────────────────

internal enum class CalendarViewType(val labelRes: StringResource) {
    LIST(Res.string.calendar_view_list),
    YEAR(Res.string.calendar_view_year),
    MONTH(Res.string.calendar_view_month),
    WEEK(Res.string.calendar_view_week),
    THREE_DAY(Res.string.calendar_view_three_day),
    DAY(Res.string.calendar_view_day),
}

internal enum class ListDisplayMode { TIMELINE, CARD }

internal data class CalendarPagerAnchor(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    val todayMillis: Long = startOfDayLocalMillis(year, month, day)

    fun monthAt(page: Int): CalendarNavigationDate {
        val offset = page - MONTH_PAGER_CENTRE
        var targetYear = year
        var targetMonth = month + offset
        while (targetMonth > 12) {
            targetMonth -= 12
            targetYear += 1
        }
        while (targetMonth < 1) {
            targetMonth += 12
            targetYear -= 1
        }
        return CalendarNavigationDate(targetYear, targetMonth)
    }

    fun monthPageFor(targetYear: Int, targetMonth: Int): Int? {
        val offset = (targetYear - year) * 12 + (targetMonth - month)
        return (MONTH_PAGER_CENTRE + offset).takeIf { it in 0 until MONTH_PAGER_RANGE }
    }

    fun yearAt(page: Int): Int = year + (page - YEAR_PAGER_CENTRE)

    fun yearPageFor(targetYear: Int): Int =
        (YEAR_PAGER_CENTRE + (targetYear - year)).coerceIn(0, YEAR_PAGER_RANGE - 1)

    fun weekStartAt(page: Int): Long =
        startOfWeekLocalMillis(todayMillis) +
            (page - WEEK_PAGER_CENTRE) * 7L * MILLIS_PER_DAY

    fun weekSelectionAt(page: Int): Long =
        if (page == WEEK_PAGER_CENTRE) todayMillis else weekStartAt(page)

    fun dayAt(page: Int): Long =
        todayMillis + (page - DAY_PAGER_CENTRE) * MILLIS_PER_DAY
}

internal data class CalendarNavigationDate(
    val year: Int,
    val month: Int,
)

internal fun monthPagerPageFor(
    todayYear: Int,
    todayMonth: Int,
    targetYear: Int,
    targetMonth: Int,
): Int? {
    return CalendarPagerAnchor(todayYear, todayMonth, 1).monthPageFor(targetYear, targetMonth)
}

internal fun widgetCalendarDay(date: WidgetCalendarDate): CalendarDay =
    CalendarDay(date.year, date.month, date.day, isCurrentMonth = true)

internal fun calendarYearEntryDate(
    currentView: CalendarViewType,
    listDate: CalendarNavigationDate,
    yearDate: CalendarNavigationDate,
    monthDate: CalendarNavigationDate,
    weekDate: CalendarNavigationDate,
    threeDayDate: CalendarNavigationDate,
    dayDate: CalendarNavigationDate,
): CalendarNavigationDate = when (currentView) {
    CalendarViewType.LIST -> listDate
    CalendarViewType.YEAR -> yearDate
    CalendarViewType.MONTH -> monthDate
    CalendarViewType.WEEK -> weekDate
    CalendarViewType.THREE_DAY -> threeDayDate
    CalendarViewType.DAY -> dayDate
}

// ── Public entry point ──────────────────────────────────────────────────────

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onCreateTask: () -> Unit = {},
    onTaskClick: (Task) -> Unit,
    widgetNavigationEvent: WidgetNavigationEvent? = null,
    onWidgetNavigationConsumed: (Long) -> Unit = {},
    onSelectedDateChanged: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> },
    onSettingsClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onTaskMutationFailure: () -> Unit = {},
    onModalBusyChanged: (Boolean) -> Unit = {},
) {
    val loadState by viewModel.loadState.collectAsState()
    val calendarRenderState by viewModel.calendarRenderState.collectAsState()
    val taskPendingSeriesChoice by viewModel.taskPendingSeriesChoice.collectAsState()
    var isContentModalBusy by remember { mutableStateOf(false) }
    ModalBusyEffect(taskPendingSeriesChoice != null || isContentModalBusy, onModalBusyChanged)

    val stripTaskDayKeys = if (isReimagined && calendarRenderState.viewPreference == CalendarViewPreference.DAY) viewModel.dayStripTaskDayKeys.collectAsState().value else emptySet()
    CalendarContent(
        stripTaskDayKeys = stripTaskDayKeys,
        onStripRangeChanged = viewModel::setDayStripRange,
        loadState = loadState,
        onRetryLoad = viewModel::retryLoad,
        onCreateTask = onCreateTask,
        onAgendaRangeChanged = viewModel::selectAgendaRange,
        onVisibleRangesChanged = viewModel::setVisibleRanges,
        calendarRenderState = calendarRenderState,
        categoryNamesFlow = viewModel.categoryNames,
        onCalendarViewChanged = { viewModel.saveCalendarViewPreference(it.toPreference()) },
        onListDisplayModeChanged = {
            viewModel.saveCalendarListDisplayModePreference(it.toPreference())
        },
        onListDaySelected = { viewModel.selectListDay(it) },
        onMonthDaySelected = { year, month, day -> viewModel.selectMonthDay(year, month, day) },
        onMonthDayCleared = { viewModel.clearMonthSelectedDay() },
        widgetNavigationEvent = widgetNavigationEvent,
        onWidgetNavigationConsumed = onWidgetNavigationConsumed,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onSelectedDateChanged = onSelectedDateChanged,
        onSettingsClick = onSettingsClick,
        isRefreshing = isRefreshing,
        syncEnabled = syncEnabled,
        onRefresh = onRefresh,
        onModalBusyChanged = { isContentModalBusy = it },
    )


    if (taskPendingSeriesChoice != null) {
        CompleteSeriesDialog(
            onCompleteOccurrence = { viewModel.completeOccurrence() },
            onCompleteSeries = { viewModel.completeSeries() },
            onDismiss = { viewModel.dismissSeriesChoice() },
        )
    }

    TaskMutationFailureEffect(
        eventFlow = viewModel.taskMutationFailureEvent,
        consume = viewModel::consumeTaskMutationFailureEvent,
        onFailure = onTaskMutationFailure,
    )
}

// ── Main Content ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarContent(
    stripTaskDayKeys: Set<Long>,
    onStripRangeChanged: (CalendarVisibleRange?) -> Unit,
    loadState: com.udnahc.opentasks.viewmodel.CalendarLoadState,
    onRetryLoad: () -> Unit,
    onCreateTask: () -> Unit = {},
    onAgendaRangeChanged: (Long?, Int) -> Unit,
    onVisibleRangesChanged: (Map<CalendarViewPreference, CalendarVisibleRange>) -> Unit,
    calendarRenderState: CalendarRenderState,
    categoryNamesFlow: StateFlow<Map<String, String>>,
    onCalendarViewChanged: (CalendarViewType) -> Deferred<Boolean>,
    onListDisplayModeChanged: (ListDisplayMode) -> Unit,
    onListDaySelected: (Long) -> Unit,
    onMonthDaySelected: (Int, Int, Int) -> Unit,
    onMonthDayCleared: () -> Unit,
    widgetNavigationEvent: WidgetNavigationEvent?,
    onWidgetNavigationConsumed: (Long) -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onSelectedDateChanged: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> },
    onSettingsClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onModalBusyChanged: (Boolean) -> Unit = {},
) {
    val reimagined = isReimagined
    val categoryNames = if (calendarRenderState.viewPreference == CalendarViewPreference.LIST || calendarRenderState.viewPreference == CalendarViewPreference.MONTH || reimagined && calendarRenderState.viewPreference != CalendarViewPreference.YEAR) categoryNamesFlow.collectAsState().value else emptyMap()
    val currentView = calendarRenderState.viewPreference.toCalendarViewType()
    val listDisplayMode = calendarRenderState.listDisplayModePreference.toListDisplayMode()
    val pagerAnchor = remember(calendarRenderState.today) {
        val today = calendarRenderState.today
        CalendarPagerAnchor(today.year, today.monthNumber, today.dayOfMonth)
    }
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val dimens = OpenTasksTheme.dimens
    val topBarHeight = if (reimagined) 0.dp else dimens.topBarHeight + statusBarHeight

    val todayYear = pagerAnchor.year
    val todayMonth = pagerAnchor.month
    val todayDay = pagerAnchor.day

    var showViewPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateNavigationError by remember { mutableStateOf<StringResource?>(null) }
    val contentModalOwners = remember { mutableStateMapOf<Any, Boolean>() }
    ModalBusyEffect(showViewPicker || showDatePicker || contentModalOwners.isNotEmpty(), onModalBusyChanged)
    val scope = rememberCoroutineScope()
    var calendarNavigationJob by remember { mutableStateOf<Job?>(null) }
    val latestView by rememberUpdatedState(currentView)

    // ── Month-pager state (shared by Month view) ───
    val centreIndex = MONTH_PAGER_CENTRE
    val pagerState = rememberPagerState(initialPage = centreIndex) { MONTH_PAGER_RANGE }

    val displayedYearMonth by remember(pagerAnchor, pagerState) {
        derivedStateOf { pagerAnchor.monthAt(pagerState.currentPage) }
    }
    val displayedYear = displayedYearMonth.year
    val displayedMonth = displayedYearMonth.month

    // Selected day for month-view collapse
    var selectedDay by remember { mutableStateOf<CalendarDay?>(null) }
    LaunchedEffect(displayedYear, displayedMonth) {
        val current = selectedDay
        if (current == null || current.year != displayedYear || current.month != displayedMonth) {
            selectedDay = null
            onMonthDayCleared()
        }
    }

    LaunchedEffect(reimagined, currentView, displayedYear, displayedMonth, selectedDay) {
        if (reimagined && currentView == CalendarViewType.MONTH && selectedDay == null) {
            val day = if (displayedYear == todayYear && displayedMonth == todayMonth) todayDay else 1
            selectedDay = CalendarDay(displayedYear, displayedMonth, day, true)
            onMonthDaySelected(displayedYear, displayedMonth, day)
        }
    }

    val collapseProgress = remember { Animatable(0f) }
    LaunchedEffect(selectedDay) {
        collapseProgress.animateTo(
            if (selectedDay != null) 1f else 0f,
            animationSpec = tween(350),
        )
    }

    // ── Year-pager state ───
    val yearPagerState = rememberPagerState(initialPage = YEAR_PAGER_CENTRE) { YEAR_PAGER_RANGE }
    val yearViewYear by remember(pagerAnchor, yearPagerState) {
        derivedStateOf { pagerAnchor.yearAt(yearPagerState.currentPage) }
    }
    var yearViewMonth by remember(todayMonth) { mutableStateOf(todayMonth) }

    LaunchedEffect(widgetNavigationEvent?.id, pagerAnchor) {
        val event = widgetNavigationEvent ?: return@LaunchedEffect
        val date = event.calendarDate ?: run {
            onWidgetNavigationConsumed(event.id)
            return@LaunchedEffect
        }
        val targetPage = pagerAnchor.monthPageFor(date.year, date.month)
            ?: run {
                onWidgetNavigationConsumed(event.id)
                return@LaunchedEffect
            }
        pagerState.scrollToPage(targetPage)
        onMonthDaySelected(date.year, date.month, date.day)
        selectedDay = widgetCalendarDay(date)
        onCalendarViewChanged(CalendarViewType.MONTH)
        onWidgetNavigationConsumed(event.id)
    }

    // ── List-view week-pager state ───
    val weekPagerState = rememberPagerState(initialPage = WEEK_PAGER_CENTRE) { WEEK_PAGER_RANGE }

    // Selected day for list view (defaults to today)
    val todayMillis = pagerAnchor.todayMillis
    var pendingListSelection by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var listSelectedDayMillis by remember(pagerAnchor) {
        mutableStateOf(pagerAnchor.weekSelectionAt(weekPagerState.currentPage))
    }
    LaunchedEffect(listSelectedDayMillis) {
        onListDaySelected(listSelectedDayMillis)
    }

    LaunchedEffect(weekPagerState.currentPage, if (reimagined) weekPagerState.isScrollInProgress else false, todayMillis, calendarNavigationJob, reimagined) {
        if (reimagined && pendingListSelection != null && (calendarNavigationJob?.isActive == true || weekPagerState.isScrollInProgress)) return@LaunchedEffect
        val targetSelection = pendingListSelection?.takeIf { it.first == weekPagerState.currentPage }?.second ?: pagerAnchor.weekSelectionAt(weekPagerState.currentPage)
        if (listSelectedDayMillis != targetSelection) listSelectedDayMillis = targetSelection
    }

    // Derive month name from the list-view selected day
    val listSelectedMonth = remember(listSelectedDayMillis) {
        extractMonth(listSelectedDayMillis)
    }

    // ── Week-view pager state ───
    val weekViewPagerState =
        rememberPagerState(initialPage = WEEK_PAGER_CENTRE) { WEEK_PAGER_RANGE }

    val weekViewSundayMillis by remember(pagerAnchor, weekViewPagerState) {
        derivedStateOf { pagerAnchor.weekStartAt(weekViewPagerState.currentPage) }
    }

    val weekViewCalendarMonth = extractMonth(weekViewSundayMillis)

    var pendingWeekSelection by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var weekViewSelectedDayMillis by remember(pagerAnchor) {
        mutableStateOf(pagerAnchor.weekSelectionAt(weekViewPagerState.currentPage))
    }
    LaunchedEffect(weekViewPagerState.currentPage, if (reimagined) weekViewPagerState.isScrollInProgress else false, todayMillis, calendarNavigationJob, reimagined) {
        if (reimagined && pendingWeekSelection != null && (calendarNavigationJob?.isActive == true || weekViewPagerState.isScrollInProgress)) return@LaunchedEffect
        val targetSelection = pendingWeekSelection?.takeIf { it.first == weekViewPagerState.currentPage }?.second ?: pagerAnchor.weekSelectionAt(weekViewPagerState.currentPage)
        if (weekViewSelectedDayMillis != targetSelection) weekViewSelectedDayMillis = targetSelection
    }

    // ── Three-day pager state (per-day pages) ───
    val threeDayPagerState = rememberPagerState(initialPage = DAY_PAGER_CENTRE) { DAY_PAGER_RANGE }

    val threeDayStartMillis by remember(pagerAnchor, threeDayPagerState) {
        derivedStateOf { pagerAnchor.dayAt(threeDayPagerState.currentPage) }
    }

    val threeDayMonth = extractMonth(threeDayStartMillis)

    // ── Day-view pager state (per-day pages) ───
    val dayViewPagerState = rememberPagerState(initialPage = DAY_PAGER_CENTRE) { DAY_PAGER_RANGE }

    val dayViewSelectedMillis by remember(pagerAnchor, dayViewPagerState) {
        derivedStateOf { pagerAnchor.dayAt(dayViewPagerState.currentPage) }
    }

    val dayViewMonth = extractMonth(dayViewSelectedMillis)
    DisposableEffect(pagerAnchor, reimagined, widgetNavigationEvent?.id) {
        onDispose {
            calendarNavigationJob?.cancel()
            calendarNavigationJob = null
            pendingListSelection = null
            pendingWeekSelection = null
        }
    }
    LaunchedEffect(reimagined, currentView, weekPagerState.currentPage, threeDayStartMillis) {
        when {
            !reimagined -> onAgendaRangeChanged(null, 0)
            currentView == CalendarViewType.LIST -> onAgendaRangeChanged(pagerAnchor.weekStartAt(weekPagerState.currentPage), 7)
            currentView == CalendarViewType.THREE_DAY -> onAgendaRangeChanged(threeDayStartMillis, 3)
            else -> onAgendaRangeChanged(null, 0)
        }
    }

    // Civil viewport geometry, including at most one pager neighbor per side. No task span controls this window.
    LaunchedEffect(reimagined, pagerAnchor, pagerState.currentPage, yearPagerState.currentPage,
        weekPagerState.currentPage, weekViewPagerState.currentPage, threeDayPagerState.currentPage, dayViewPagerState.currentPage) {
        if (!reimagined) {
            onVisibleRangesChanged(emptyMap())
            return@LaunchedEffect
        }
        fun dateAt(millis: Long) = LocalDate(extractYear(millis), extractMonth(millis), extractDay(millis))
        fun dayWindow(page: Int, visibleDays: Int): CalendarVisibleRange {
            val first = (page - 1).coerceAtLeast(0)
            val last = (page + visibleDays).coerceAtMost(DAY_PAGER_RANGE - 1)
            return CalendarVisibleRange(dateAt(pagerAnchor.dayAt(first)), dateAt(pagerAnchor.dayAt(last)).plus(1, DateTimeUnit.DAY))
        }
        fun weekWindow(page: Int): CalendarVisibleRange {
            val first = (page - 1).coerceAtLeast(0)
            val last = (page + 1).coerceAtMost(WEEK_PAGER_RANGE - 1)
            return CalendarVisibleRange(dateAt(pagerAnchor.weekStartAt(first)), dateAt(pagerAnchor.weekStartAt(last)).plus(7, DateTimeUnit.DAY))
        }
        val firstMonth = pagerAnchor.monthAt((pagerState.currentPage - 1).coerceAtLeast(0))
        val lastMonth = pagerAnchor.monthAt((pagerState.currentPage + 1).coerceAtMost(MONTH_PAGER_RANGE - 1))
        val firstMonthDay = LocalDate(firstMonth.year, firstMonth.month, 1)
        val lastMonthDay = LocalDate(lastMonth.year, lastMonth.month, daysInMonth(lastMonth.year, lastMonth.month))
        val monthWindow = CalendarVisibleRange(
            firstMonthDay.minus(dayOfWeekIndex(firstMonth.year, firstMonth.month, 1), DateTimeUnit.DAY),
            lastMonthDay.plus(7 - dayOfWeekIndex(lastMonth.year, lastMonth.month, lastMonthDay.dayOfMonth), DateTimeUnit.DAY),
        )
        val yearWindow = CalendarVisibleRange(
            LocalDate(pagerAnchor.yearAt((yearPagerState.currentPage - 1).coerceAtLeast(0)), 1, 1),
            LocalDate(pagerAnchor.yearAt((yearPagerState.currentPage + 1).coerceAtMost(YEAR_PAGER_RANGE - 1)) + 1, 1, 1),
        )
        val visibleWeek = weekWindow(weekViewPagerState.currentPage)
        val miniMonthDate = dateAt(pagerAnchor.weekStartAt(weekViewPagerState.currentPage))
        val miniMonthStart = LocalDate(miniMonthDate.year, miniMonthDate.monthNumber, 1)
        val weekGridWindow = CalendarVisibleRange(
            minOf(visibleWeek.first, miniMonthStart),
            maxOf(visibleWeek.endExclusive, miniMonthStart.plus(1, DateTimeUnit.MONTH)),
        )
        onVisibleRangesChanged(mapOf(
            CalendarViewPreference.MONTH to monthWindow,
            CalendarViewPreference.YEAR to yearWindow,
            CalendarViewPreference.LIST to weekWindow(weekPagerState.currentPage),
            CalendarViewPreference.WEEK to weekGridWindow,
            CalendarViewPreference.THREE_DAY to dayWindow(threeDayPagerState.currentPage, 3),
            CalendarViewPreference.DAY to dayWindow(dayViewPagerState.currentPage, 1),
        ))
    }

    // Propagate selected date to parent (per-view effects)
    LaunchedEffect(currentView, listSelectedDayMillis) {
        if (currentView == CalendarViewType.LIST) {
            onSelectedDateChanged(
                extractYear(listSelectedDayMillis),
                extractMonth(listSelectedDayMillis),
                extractDay(listSelectedDayMillis)
            )
        }
    }
    LaunchedEffect(currentView, selectedDay, displayedYear, displayedMonth) {
        if (currentView == CalendarViewType.MONTH) {
            val sd = selectedDay
            if (sd != null) onSelectedDateChanged(sd.year, sd.month, sd.day)
            else onSelectedDateChanged(displayedYear, displayedMonth, 0)
        }
    }
    LaunchedEffect(currentView, weekViewSelectedDayMillis, weekViewSundayMillis) {
        if (currentView == CalendarViewType.WEEK) {
            val selection = if (reimagined) weekViewSelectedDayMillis else weekViewSundayMillis
            onSelectedDateChanged(extractYear(selection), extractMonth(selection), extractDay(selection))
        }
    }
    LaunchedEffect(currentView, threeDayStartMillis) {
        if (currentView == CalendarViewType.THREE_DAY) {
            onSelectedDateChanged(
                extractYear(threeDayStartMillis),
                extractMonth(threeDayStartMillis),
                extractDay(threeDayStartMillis)
            )
        }
    }
    LaunchedEffect(currentView, dayViewSelectedMillis) {
        if (currentView == CalendarViewType.DAY) {
            onSelectedDateChanged(
                extractYear(dayViewSelectedMillis),
                extractMonth(dayViewSelectedMillis),
                extractDay(dayViewSelectedMillis)
            )
        }
    }
    LaunchedEffect(currentView, yearViewYear, yearViewMonth) {
        if (currentView == CalendarViewType.YEAR) {
            onSelectedDateChanged(yearViewYear, yearViewMonth, 0)
        }
    }

    // ── Top bar title ───
    val topBarTitle = when (currentView) {
        CalendarViewType.LIST -> calendarMonthName(listSelectedMonth)
        CalendarViewType.YEAR -> yearViewYear.toString()
        CalendarViewType.MONTH -> calendarMonthName(displayedMonth)
        CalendarViewType.WEEK -> calendarMonthName(weekViewCalendarMonth)
        CalendarViewType.THREE_DAY -> calendarMonthName(threeDayMonth)
        CalendarViewType.DAY -> calendarMonthName(dayViewMonth)
    }

    val activePagerState = when (currentView) {
        CalendarViewType.LIST -> weekPagerState
        CalendarViewType.YEAR -> yearPagerState
        CalendarViewType.MONTH -> pagerState
        CalendarViewType.WEEK -> weekViewPagerState
        CalendarViewType.THREE_DAY -> threeDayPagerState
        CalendarViewType.DAY -> dayViewPagerState
    }
    val isMonthPagerCollapsed by remember(currentView, selectedDay) {
        derivedStateOf { currentView == CalendarViewType.MONTH && selectedDay != null }
    }
    val canNavigateCalendar = !activePagerState.isScrollInProgress && !isMonthPagerCollapsed
    val canNavigatePrevious = canNavigateCalendar && activePagerState.canScrollBackward
    val canNavigateNext = canNavigateCalendar && activePagerState.canScrollForward

    fun navigateCalendarBy(pageDelta: Int) {
        if (activePagerState.isScrollInProgress || isMonthPagerCollapsed) return
        val targetPage = (activePagerState.currentPage + pageDelta)
            .coerceIn(0, activePagerState.pageCount - 1)
        if (targetPage != activePagerState.currentPage) {
            scope.launch { activePagerState.animateScrollToPage(targetPage) }
        }
    }

    val pickerSelected = when (currentView) {
        CalendarViewType.LIST -> LocalDate(extractYear(listSelectedDayMillis), extractMonth(listSelectedDayMillis), extractDay(listSelectedDayMillis))
        CalendarViewType.YEAR -> LocalDate(yearViewYear, yearViewMonth, 1)
        CalendarViewType.MONTH -> selectedDay?.let { LocalDate(it.year, it.month, it.day) } ?: LocalDate(displayedYear, displayedMonth, 1)
        CalendarViewType.WEEK -> LocalDate(extractYear(weekViewSelectedDayMillis), extractMonth(weekViewSelectedDayMillis), extractDay(weekViewSelectedDayMillis))
        CalendarViewType.THREE_DAY -> LocalDate(extractYear(threeDayStartMillis), extractMonth(threeDayStartMillis), extractDay(threeDayStartMillis))
        CalendarViewType.DAY -> LocalDate(extractYear(dayViewSelectedMillis), extractMonth(dayViewSelectedMillis), extractDay(dayViewSelectedMillis))
    }
    fun localDate(millis: Long) = LocalDate(extractYear(millis), extractMonth(millis), extractDay(millis))
    val pickerMin = when (currentView) {
        CalendarViewType.MONTH -> pagerAnchor.monthAt(0).let { LocalDate(it.year, it.month, 1) }
        CalendarViewType.YEAR -> LocalDate(pagerAnchor.yearAt(0), 1, 1)
        CalendarViewType.LIST, CalendarViewType.WEEK -> localDate(pagerAnchor.weekStartAt(0))
        else -> localDate(pagerAnchor.dayAt(0))
    }
    val pickerMax = when (currentView) {
        CalendarViewType.MONTH -> pagerAnchor.monthAt(MONTH_PAGER_RANGE - 1).let { LocalDate(it.year, it.month, daysInMonth(it.year, it.month)) }
        CalendarViewType.YEAR -> LocalDate(pagerAnchor.yearAt(YEAR_PAGER_RANGE - 1), 12, 31)
        CalendarViewType.LIST, CalendarViewType.WEEK -> localDate(pagerAnchor.weekStartAt(WEEK_PAGER_RANGE - 1) + 6 * MILLIS_PER_DAY)
        else -> localDate(pagerAnchor.dayAt(DAY_PAGER_RANGE - 1))
    }
    val reimaginedPeriod = if (!reimagined) "" else when (currentView) {
        CalendarViewType.YEAR -> topBarTitle
        CalendarViewType.MONTH -> stringResource(Res.string.calendar_period_month, topBarTitle, pickerSelected.year)
        CalendarViewType.DAY -> calendarPeriodDay(dayViewSelectedMillis)
        CalendarViewType.THREE_DAY -> stringResource(Res.string.calendar_period_range, calendarPeriodDay(threeDayStartMillis), calendarPeriodDay(threeDayStartMillis + 2 * MILLIS_PER_DAY))
        CalendarViewType.WEEK -> stringResource(Res.string.calendar_period_range, calendarPeriodDay(weekViewSundayMillis), calendarPeriodDay(weekViewSundayMillis + 6 * MILLIS_PER_DAY))
        CalendarViewType.LIST -> {
            val start = pagerAnchor.weekStartAt(weekPagerState.currentPage)
            stringResource(Res.string.calendar_period_range, calendarPeriodDay(start), calendarPeriodDay(start + 6 * MILLIS_PER_DAY))
        }
    }
    fun switchReimaginedView(view: CalendarViewType, date: LocalDate = pickerSelected): Boolean {
        val millis = startOfDayLocalMillis(date.year, date.monthNumber, date.dayOfMonth)
        val targetPager = when (view) {
            CalendarViewType.LIST -> weekPagerState
            CalendarViewType.YEAR -> yearPagerState
            CalendarViewType.MONTH -> pagerState
            CalendarViewType.WEEK -> weekViewPagerState
            CalendarViewType.THREE_DAY -> threeDayPagerState
            CalendarViewType.DAY -> dayViewPagerState
        }
        val page = when (view) {
            CalendarViewType.MONTH -> pagerAnchor.monthPageFor(date.year, date.monthNumber)
            CalendarViewType.YEAR -> (YEAR_PAGER_CENTRE + date.year - todayYear).takeIf { it in 0 until YEAR_PAGER_RANGE }
            CalendarViewType.WEEK, CalendarViewType.LIST -> (WEEK_PAGER_CENTRE + ((startOfWeekLocalMillis(millis) - startOfWeekLocalMillis(todayMillis)) / (7 * MILLIS_PER_DAY)).toInt()).takeIf { it in 0 until WEEK_PAGER_RANGE }
            else -> (DAY_PAGER_CENTRE + ((millis - todayMillis) / MILLIS_PER_DAY).toInt()).takeIf { it in 0 until DAY_PAGER_RANGE }
        } ?: run { dateNavigationError = Res.string.calendar_date_out_of_range; return false }
        dateNavigationError = null
        fun applySelection() {
            when (view) {
                CalendarViewType.MONTH -> { selectedDay = CalendarDay(date.year, date.monthNumber, date.dayOfMonth, true); onMonthDaySelected(date.year, date.monthNumber, date.dayOfMonth) }
                CalendarViewType.YEAR -> yearViewMonth = date.monthNumber
                CalendarViewType.LIST -> listSelectedDayMillis = millis
                CalendarViewType.WEEK -> weekViewSelectedDayMillis = millis
                else -> Unit
            }
        }
        if (view == currentView && page == targetPager.currentPage && !targetPager.isScrollInProgress && calendarNavigationJob?.isActive != true) {
            pendingListSelection = if (view == CalendarViewType.LIST) page to millis else null
            pendingWeekSelection = if (view == CalendarViewType.WEEK) page to millis else null
            applySelection()
            return true
        }
        calendarNavigationJob?.cancel()
        val crossView = view != currentView
        // The ViewModel owns this write even if this navigation is superseded or cancelled.
        val preferenceSave = onCalendarViewChanged(view)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (crossView && !preferenceSave.await()) {
                    currentCoroutineContext().ensureActive()
                    dateNavigationError = Res.string.calendar_view_save_failed
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                pendingListSelection = if (view == CalendarViewType.LIST) page to millis else null
                pendingWeekSelection = if (view == CalendarViewType.WEEK) page to millis else null
                // Activate before scrolling: an inactive pager needs its first layout.
                snapshotFlow { latestView }.first { it == view }
                targetPager.scrollToPage(page)
                snapshotFlow { targetPager.currentPage == page && !targetPager.isScrollInProgress }.first { it }
                currentCoroutineContext().ensureActive()
                if (latestView != view) return@launch
                applySelection()
            } finally {
                if (calendarNavigationJob === currentCoroutineContext()[Job]) calendarNavigationJob = null
            }
        }
        calendarNavigationJob = job
        job.start()
        return true
    }

    fun navigateDate(date: LocalDate): Boolean {
        if (date < pickerMin || date > pickerMax) { dateNavigationError = Res.string.calendar_date_out_of_range; return false }
        return switchReimaginedView(currentView, date)
    }

    // ── Navigate from Year → Month ───
    fun navigateToMonth(year: Int, month: Int) {
        if (reimagined) {
            switchReimaginedView(CalendarViewType.MONTH, LocalDate(year, month, 1))
        } else {
            val targetPage = pagerAnchor.monthPageFor(year, month) ?: return
            scope.launch { pagerState.scrollToPage(targetPage) }
            onCalendarViewChanged(CalendarViewType.MONTH)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalCalendarModalBusy provides { owner, busy -> if (busy) contentModalOwners[owner] = true else contentModalOwners.remove(owner); Unit }, LocalCalendarCategoryNames provides categoryNames) {
    val body: @Composable () -> Unit = {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Body ─────────────────────────────────────────────────────
        SyncPullToRefresh(
            isRefreshing = isRefreshing,
            enabled = syncEnabled,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (currentView) {
                CalendarViewType.LIST -> {
                    ListViewContent(
                        onDisplayModeChanged = onListDisplayModeChanged,
                        dayProjection = calendarRenderState.selectedDayProjection,
                        todayMillis = todayMillis,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        selectedDayMillis = listSelectedDayMillis,
                        onDaySelected = { millis ->
                            if (reimagined) navigateDate(localDate(millis)) else {
                                pendingListSelection = null
                                listSelectedDayMillis = millis
                            }
                        },
                        weekPagerState = weekPagerState,
                        weekPagerCentre = WEEK_PAGER_CENTRE,
                        calendarDaysByDay = calendarRenderState.calendarDaysByDay,
                        categoryNames = categoryNames,
                        topBarHeight = topBarHeight,
                        navBarHeight = navBarHeight,
                        displayMode = listDisplayMode,
                        onTaskClick = onTaskClick,
                        onToggleComplete = onToggleComplete,
                    )
                }

                CalendarViewType.YEAR -> {
                    YearViewContent(
                        pagerState = yearPagerState,
                        centreIndex = YEAR_PAGER_CENTRE,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        taskDayKeys = calendarRenderState.taskDayKeys,
                        topBarHeight = topBarHeight,
                        navBarHeight = navBarHeight,
                        onMonthClick = { year, month -> navigateToMonth(year, month) },
                    )
                }

                CalendarViewType.MONTH -> {
                    MonthViewContent(
                        selectedDayProjection = calendarRenderState.selectedDayProjection,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        selectedDay = selectedDay,
                        collapseProgress = collapseProgress,
                        pagerState = pagerState,
                        centreIndex = centreIndex,
                        calendarDaysByDay = calendarRenderState.calendarDaysByDay,
                        categoryNames = categoryNames,
                        topBarHeight = topBarHeight,
                        navBarHeight = navBarHeight,
                        onDayClick = { day ->
                            if (!day.isCurrentMonth) return@MonthViewContent
                            if (reimagined) calendarNavigationJob?.cancel()
                            scope.launch {
                                selectedDay = if (!reimagined && selectedDay == day) {
                                    onMonthDayCleared()
                                    null
                                } else {
                                    onMonthDaySelected(day.year, day.month, day.day)
                                    day
                                }
                            }
                        },
                        onTaskClick = onTaskClick,
                        onToggleComplete = onToggleComplete,
                    )
                }

                CalendarViewType.WEEK -> {
                    WeekViewContent(
                        todayMillis = todayMillis,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        weekPagerState = weekViewPagerState,
                        weekPagerCentre = WEEK_PAGER_CENTRE,
                        weekSundayMillis = weekViewSundayMillis,
                        calendarYear = extractYear(weekViewSundayMillis),
                        calendarMonth = weekViewCalendarMonth,
                        calendarDaysByDay = calendarRenderState.calendarDaysByDay,
                        taskDayKeys = calendarRenderState.taskDayKeys,
                        topBarHeight = topBarHeight,
                        navBarHeight = navBarHeight,
                        onTaskClick = onTaskClick,
                        onToggleComplete = onToggleComplete,
                        onWeekSelected = selectWeek@ { sundayMillis ->
                            val thisWeekSunMillis = startOfWeekLocalMillis(todayMillis)
                            val offset =
                                ((sundayMillis - thisWeekSunMillis) / (7 * MILLIS_PER_DAY)).toInt()
                            val targetPage = WEEK_PAGER_CENTRE + offset
                            if (reimagined) {
                                if (targetPage !in 0 until weekViewPagerState.pageCount) {
                                    dateNavigationError = Res.string.calendar_date_out_of_range
                                    return@selectWeek
                                }
                                dateNavigationError = null
                                calendarNavigationJob?.cancel()
                                pendingWeekSelection = null
                            }
                            scope.launch {
                                weekViewPagerState.animateScrollToPage(targetPage)
                            }
                        },
                        selectedDayMillis = weekViewSelectedDayMillis,
                        onDaySelected = { millis ->
                            if (reimagined) {
                                calendarNavigationJob?.cancel()
                                pendingWeekSelection = weekViewPagerState.currentPage to millis
                            } else pendingWeekSelection = null
                            weekViewSelectedDayMillis = millis
                        },
                    )
                }

                CalendarViewType.THREE_DAY -> {
                    ThreeDayViewContent(
                        onDaySelected = { millis ->
                            if (reimagined) switchReimaginedView(CalendarViewType.DAY, localDate(millis))
                        },
                        todayMillis = todayMillis,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        pagerState = threeDayPagerState,
                        pagerCentre = DAY_PAGER_CENTRE,
                        calendarDaysByDay = calendarRenderState.calendarDaysByDay,
                        topBarHeight = topBarHeight,
                        navBarHeight = navBarHeight,
                        onTaskClick = onTaskClick,
                        onToggleComplete = onToggleComplete,
                        timelineHourLabels = calendarRenderState.timelineHourLabels,
                    )
                }

                CalendarViewType.DAY -> {
                    DayViewContent(
                        stripTaskDayKeys = stripTaskDayKeys,
                        onStripRangeChanged = onStripRangeChanged,
                        onCreateTask = onCreateTask,
                        todayMillis = todayMillis,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        pagerState = dayViewPagerState,
                        pagerCentre = DAY_PAGER_CENTRE,
                        calendarDaysByDay = calendarRenderState.calendarDaysByDay,
                        topBarHeight = topBarHeight,
                        navBarHeight = navBarHeight,
                        onTaskClick = onTaskClick,
                        onToggleComplete = onToggleComplete,
                        timelineHourLabels = calendarRenderState.timelineHourLabels,
                    )
                }
            }
        }

        if (reimagined && loadState != com.udnahc.opentasks.viewmodel.CalendarLoadState.READY) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }, color = MaterialTheme.colorScheme.background) {
                com.udnahc.opentasks.ui.screens.ReimaginedReadStatus(loadState == com.udnahc.opentasks.viewmodel.CalendarLoadState.LOADING, onRetryLoad)
            }
        }
    }
    }
    if (reimagined) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    OpenTasksTopBar(
                        titleContent = {
                            TextButton(onClick = { if (dateNavigationError == Res.string.calendar_date_out_of_range) dateNavigationError = null; showDatePicker = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minimumInteractiveTargetSize)) {
                                val titleStyle = OpenTasksTheme.typography.appBarTitle
                                Text(reimaginedPeriod, style = titleStyle.copy(fontSize = titleStyle.fontSize * (14f / 22f), lineHeight = titleStyle.lineHeight * (14f / 22f)), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(painterResource(Res.drawable.ic_dropdown), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        actions = {
                            IconButton(onClick = { navigateDate(calendarRenderState.today) }) {
                                Icon(painterResource(Res.drawable.ic_calendar), contentDescription = stringResource(Res.string.today), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ReimaginedAddButton(onCreateTask)
                            OpenTasksSettingsButton(onClick = onSettingsClick)
                        },
                    )
                    CalendarDatePicker(showDatePicker, pickerSelected, calendarRenderState.today, pickerMin, pickerMax, { showDatePicker = false; if (dateNavigationError == Res.string.calendar_date_out_of_range) dateNavigationError = null }, ::navigateDate)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = dimens.paddingLarge).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)) {
                    CalendarViewType.entries.forEach { view ->
                        FilterChip(selected = currentView == view, onClick = { switchReimaginedView(view) }, label = { Text(stringResource(view.labelRes)) }, modifier = Modifier.heightIn(min = dimens.minimumInteractiveTargetSize))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                body()
                dateNavigationError?.let { error ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(dimens.paddingLarge), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = { dateNavigationError = null }) { Text(stringResource(Res.string.close)) }
                    }
                }
            }
        }
    } else Box(Modifier.fillMaxSize()) {
        body()
        CalendarTopBar(
            title = topBarTitle,
            currentView = currentView,
            showBackButton = (currentView == CalendarViewType.MONTH && selectedDay != null) ||
                    currentView == CalendarViewType.YEAR,
            onBack = {
                if (currentView == CalendarViewType.MONTH && selectedDay != null) {
                    scope.launch {
                        selectedDay = null
                        onMonthDayCleared()
                    }
                } else if (currentView == CalendarViewType.YEAR) {
                    navigateToMonth(yearViewYear, yearViewMonth)
                }
            },
            canNavigatePrevious = canNavigatePrevious,
            onNavigatePrevious = { navigateCalendarBy(-1) },
            canNavigateNext = canNavigateNext,
            onNavigateNext = { navigateCalendarBy(1) },
            listDisplayMode = listDisplayMode,
            onToggleDisplayMode = {
                onListDisplayModeChanged(
                    when (listDisplayMode) {
                        ListDisplayMode.TIMELINE -> ListDisplayMode.CARD
                        ListDisplayMode.CARD -> ListDisplayMode.TIMELINE
                    }
                )
            },
            showViewPicker = showViewPicker,
            onViewPickerToggle = { showViewPicker = true },
            onViewSelected = { view ->
                showViewPicker = false
                if (view == CalendarViewType.YEAR) {
                    val activeViewDate = calendarYearEntryDate(
                        currentView = currentView,
                        listDate = CalendarNavigationDate(
                            extractYear(listSelectedDayMillis),
                            extractMonth(listSelectedDayMillis),
                        ),
                        yearDate = CalendarNavigationDate(yearViewYear, yearViewMonth),
                        monthDate = selectedDay?.let {
                            CalendarNavigationDate(it.year, it.month)
                        } ?: CalendarNavigationDate(displayedYear, displayedMonth),
                        weekDate = CalendarNavigationDate(
                            extractYear(weekViewSundayMillis),
                            extractMonth(weekViewSundayMillis),
                        ),
                        threeDayDate = CalendarNavigationDate(
                            extractYear(threeDayStartMillis),
                            extractMonth(threeDayStartMillis),
                        ),
                        dayDate = CalendarNavigationDate(
                            extractYear(dayViewSelectedMillis),
                            extractMonth(dayViewSelectedMillis),
                        ),
                    )
                    yearViewMonth = activeViewDate.month
                    val targetPage = pagerAnchor.yearPageFor(activeViewDate.year)
                        .coerceIn(0, yearPagerState.pageCount - 1)
                    scope.launch { yearPagerState.scrollToPage(targetPage) }
                }
                onCalendarViewChanged(view)
            },
            onViewPickerDismiss = { showViewPicker = false },
            onSettingsClick = onSettingsClick,
        )
    }
}

}

// ── Calendar Top Bar ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarTopBar(
    title: String,
    currentView: CalendarViewType,
    showBackButton: Boolean,
    onBack: () -> Unit,
    canNavigatePrevious: Boolean,
    onNavigatePrevious: () -> Unit,
    canNavigateNext: Boolean,
    onNavigateNext: () -> Unit,
    listDisplayMode: ListDisplayMode,
    onToggleDisplayMode: () -> Unit,
    showViewPicker: Boolean,
    onViewPickerToggle: () -> Unit,
    onViewSelected: (CalendarViewType) -> Unit,
    onViewPickerDismiss: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    OpenTasksTopBar(
        titleContent = {
            CalendarPeriodNavigation(
                title = title,
                currentView = currentView,
                canNavigatePrevious = canNavigatePrevious,
                onNavigatePrevious = onNavigatePrevious,
                canNavigateNext = canNavigateNext,
                onNavigateNext = onNavigateNext,
            )
        },
        containerStyle = OpenTasksTopBarContainerStyle.Translucent,
        centerTitle = true,
        navigationIcon = {
            if (showBackButton) {
                OpenTasksBackButton(onClick = onBack)
            }
        },
        actions = {
            if (currentView == CalendarViewType.LIST) {
                DisplayModeToggle(
                    displayMode = listDisplayMode,
                    onToggle = onToggleDisplayMode,
                )
            }

            Box {
                IconButton(onClick = onViewPickerToggle) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_list),
                        contentDescription = stringResource(Res.string.choose_calendar_view),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimens.iconLarge),
                    )
                }
                ViewPickerDropdown(
                    expanded = showViewPicker,
                    currentView = currentView,
                    onViewSelected = onViewSelected,
                    onDismiss = onViewPickerDismiss,
                )
            }

            OpenTasksSettingsButton(onClick = onSettingsClick)

        },
    )
}

@Composable
private fun CalendarPeriodNavigation(
    title: String,
    currentView: CalendarViewType,
    canNavigatePrevious: Boolean,
    onNavigatePrevious: () -> Unit,
    canNavigateNext: Boolean,
    onNavigateNext: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onNavigatePrevious,
            enabled = canNavigatePrevious,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_left),
                contentDescription = stringResource(currentView.previousNavigationLabel()),
                modifier = Modifier.size(dimens.iconLarge),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(
            onClick = onNavigateNext,
            enabled = canNavigateNext,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = stringResource(currentView.nextNavigationLabel()),
                modifier = Modifier.size(dimens.iconLarge),
            )
        }
    }
}

@Composable
private fun DisplayModeToggle(
    displayMode: ListDisplayMode,
    onToggle: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    IconButton(onClick = onToggle) {
        Icon(
            painter = painterResource(
                when (displayMode) {
                    ListDisplayMode.TIMELINE -> Res.drawable.ic_grid_view
                    ListDisplayMode.CARD -> Res.drawable.ic_schedule
                }
            ),
            contentDescription = stringResource(
                when (displayMode) {
                    ListDisplayMode.TIMELINE -> Res.string.show_card_view
                    ListDisplayMode.CARD -> Res.string.show_timeline_view
                }
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconLarge),
        )
    }
}

private fun CalendarViewType.previousNavigationLabel(): StringResource = when (this) {
    CalendarViewType.MONTH -> Res.string.previous_month
    CalendarViewType.YEAR -> Res.string.previous_year
    CalendarViewType.LIST,
    CalendarViewType.WEEK -> Res.string.previous_week
    CalendarViewType.THREE_DAY,
    CalendarViewType.DAY -> Res.string.previous_day
}

private fun CalendarViewType.nextNavigationLabel(): StringResource = when (this) {
    CalendarViewType.MONTH -> Res.string.next_month
    CalendarViewType.YEAR -> Res.string.next_year
    CalendarViewType.LIST,
    CalendarViewType.WEEK -> Res.string.next_week
    CalendarViewType.THREE_DAY,
    CalendarViewType.DAY -> Res.string.next_day
}

// ── View Picker Dropdown ────────────────────────────────────────────────────

@Composable
internal fun ViewPickerDropdown(
    expanded: Boolean,
    currentView: CalendarViewType,
    onViewSelected: (CalendarViewType) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        CalendarViewType.entries.forEach { view ->
            DropdownMenuItem(
                text = {
                    SelectedOptionRow(
                        label = stringResource(view.labelRes),
                        isSelected = view == currentView,
                        onClick = { onViewSelected(view) },
                    )
                },
                onClick = { onViewSelected(view) },
            )
        }
    }
}

private val dimens @Composable get() = OpenTasksTheme.dimens


private fun CalendarViewPreference.toCalendarViewType(): CalendarViewType = when (this) {
    CalendarViewPreference.LIST -> CalendarViewType.LIST
    CalendarViewPreference.YEAR -> CalendarViewType.YEAR
    CalendarViewPreference.MONTH -> CalendarViewType.MONTH
    CalendarViewPreference.WEEK -> CalendarViewType.WEEK
    CalendarViewPreference.THREE_DAY -> CalendarViewType.THREE_DAY
    CalendarViewPreference.DAY -> CalendarViewType.DAY
}

private fun CalendarViewType.toPreference(): CalendarViewPreference = when (this) {
    CalendarViewType.LIST -> CalendarViewPreference.LIST
    CalendarViewType.YEAR -> CalendarViewPreference.YEAR
    CalendarViewType.MONTH -> CalendarViewPreference.MONTH
    CalendarViewType.WEEK -> CalendarViewPreference.WEEK
    CalendarViewType.THREE_DAY -> CalendarViewPreference.THREE_DAY
    CalendarViewType.DAY -> CalendarViewPreference.DAY
}

private fun CalendarListDisplayModePreference.toListDisplayMode(): ListDisplayMode = when (this) {
    CalendarListDisplayModePreference.TIMELINE -> ListDisplayMode.TIMELINE
    CalendarListDisplayModePreference.CARD -> ListDisplayMode.CARD
}

private fun ListDisplayMode.toPreference(): CalendarListDisplayModePreference = when (this) {
    ListDisplayMode.TIMELINE -> CalendarListDisplayModePreference.TIMELINE
    ListDisplayMode.CARD -> CalendarListDisplayModePreference.CARD
}
