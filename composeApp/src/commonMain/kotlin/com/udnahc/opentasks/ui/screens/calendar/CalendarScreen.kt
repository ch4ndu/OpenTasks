package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.extensions.currentMonth
import com.udnahc.opentasks.data.extensions.currentYear
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.startOfWeekLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.calendar_view_day
import opentasks.composeapp.generated.resources.calendar_view_list
import opentasks.composeapp.generated.resources.calendar_view_month
import opentasks.composeapp.generated.resources.calendar_view_three_day
import opentasks.composeapp.generated.resources.calendar_view_week
import opentasks.composeapp.generated.resources.calendar_view_year
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.ic_schedule
import opentasks.composeapp.generated.resources.ic_settings
import opentasks.composeapp.generated.resources.import_from_calendar
import opentasks.composeapp.generated.resources.import_from_ics
import opentasks.composeapp.generated.resources.more
import opentasks.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── Pager constants ─────────────────────────────────────────────────────────

private const val DAY_PAGER_RANGE = 7300
private const val DAY_PAGER_CENTRE = DAY_PAGER_RANGE / 2
private const val WEEK_PAGER_RANGE = 1040
private const val WEEK_PAGER_CENTRE = WEEK_PAGER_RANGE / 2
private const val MONTH_PAGER_RANGE = 240
private const val MONTH_PAGER_CENTRE = MONTH_PAGER_RANGE / 2
private const val YEAR_PAGER_RANGE = 20
private const val YEAR_PAGER_CENTRE = YEAR_PAGER_RANGE / 2

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

// ── Public entry point ──────────────────────────────────────────────────────

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onTaskClick: (Task) -> Unit,
    onSelectedDateChanged: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> },
    onSettingsClick: () -> Unit = {},
) {
    val tasks by viewModel.tasks.collectAsState()
    val tasksByDay by viewModel.tasksByDay.collectAsState()
    CalendarContent(
        tasks = tasks,
        tasksByDay = tasksByDay,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onSelectedDateChanged = onSelectedDateChanged,
        onSettingsClick = onSettingsClick,
    )
}

// ── Main Content ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarContent(
    tasks: List<Task>,
    tasksByDay: Map<Long, List<Task>>,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onSelectedDateChanged: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> },
    onSettingsClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val dimens = OpenTasksTheme.dimens
    val topBarHeight = dimens.topBarHeight + statusBarHeight

    val todayYear = remember { currentYear() }
    val todayMonth = remember { currentMonth() }
    val todayDay = remember { currentDay() }

    var currentView by remember { mutableStateOf(CalendarViewType.MONTH) }
    var showViewPicker by remember { mutableStateOf(false) }
    var listDisplayMode by remember { mutableStateOf(ListDisplayMode.TIMELINE) }
    val scope = rememberCoroutineScope()

    // ── Month-pager state (shared by Month view) ───
    val centreIndex = MONTH_PAGER_CENTRE
    val pagerState = rememberPagerState(initialPage = centreIndex) { MONTH_PAGER_RANGE }

    val displayedYearMonth by remember {
        derivedStateOf {
            val offset = pagerState.currentPage - centreIndex
            var y = todayYear
            var m = todayMonth + offset
            while (m > 12) {
                m -= 12; y++
            }
            while (m < 1) {
                m += 12; y--
            }
            y to m
        }
    }
    val displayedYear = displayedYearMonth.first
    val displayedMonth = displayedYearMonth.second

    // Selected day for month-view collapse
    var selectedDay by remember { mutableStateOf<CalendarDay?>(null) }
    LaunchedEffect(pagerState.currentPage) { selectedDay = null }

    val collapseProgress = remember { Animatable(0f) }
    LaunchedEffect(selectedDay) {
        collapseProgress.animateTo(
            if (selectedDay != null) 1f else 0f,
            animationSpec = tween(350),
        )
    }

    // ── Year-pager state ───
    val yearPagerState = rememberPagerState(initialPage = YEAR_PAGER_CENTRE) { YEAR_PAGER_RANGE }
    val yearViewYear by remember {
        derivedStateOf {
            todayYear + (yearPagerState.currentPage - YEAR_PAGER_CENTRE)
        }
    }

    // ── List-view week-pager state ───
    val weekPagerState = rememberPagerState(initialPage = WEEK_PAGER_CENTRE) { WEEK_PAGER_RANGE }

    // Selected day for list view (defaults to today)
    val todayMillis = remember { startOfDayLocalMillis(todayYear, todayMonth, todayDay) }
    var listSelectedDayMillis by remember { mutableStateOf(todayMillis) }

    // When the week pager page changes, select the first day of the new week
    // (unless the current selection is already within that week)
    val weekMillis = 7 * MILLIS_PER_DAY
    LaunchedEffect(weekPagerState.currentPage) {
        val weekOffset = weekPagerState.currentPage - WEEK_PAGER_CENTRE
        val thisWeekSunMillis = startOfWeekLocalMillis(todayMillis)
        val targetWeekSunMillis = thisWeekSunMillis + weekOffset * weekMillis
        val targetWeekSatMillis = targetWeekSunMillis + 6 * MILLIS_PER_DAY
        // If current selection is outside this week, snap to Sunday of the new week
        if (listSelectedDayMillis !in targetWeekSunMillis..targetWeekSatMillis) {
            listSelectedDayMillis = targetWeekSunMillis
        }
    }

    // Derive month name from the list-view selected day
    val listSelectedMonth = remember(listSelectedDayMillis) {
        extractMonth(listSelectedDayMillis)
    }

    // ── Week-view pager state ───
    val weekViewPagerState =
        rememberPagerState(initialPage = WEEK_PAGER_CENTRE) { WEEK_PAGER_RANGE }

    val weekViewSundayMillis by remember {
        derivedStateOf {
            val weekOffset = weekViewPagerState.currentPage - WEEK_PAGER_CENTRE
            val thisWeekSunMillis = startOfWeekLocalMillis(todayMillis)
            thisWeekSunMillis + weekOffset * 7 * MILLIS_PER_DAY
        }
    }

    val weekViewCalendarMonth by remember {
        derivedStateOf { extractMonth(weekViewSundayMillis) }
    }

    var weekViewSelectedDayMillis by remember { mutableStateOf(todayMillis) }

    // ── Three-day pager state (per-day pages) ───
    val threeDayPagerState = rememberPagerState(initialPage = DAY_PAGER_CENTRE) { DAY_PAGER_RANGE }

    val threeDayStartMillis by remember {
        derivedStateOf {
            val offset = threeDayPagerState.currentPage - DAY_PAGER_CENTRE
            todayMillis + offset * MILLIS_PER_DAY
        }
    }

    val threeDayMonth by remember {
        derivedStateOf { extractMonth(threeDayStartMillis) }
    }

    // ── Day-view pager state (per-day pages) ───
    val dayViewPagerState = rememberPagerState(initialPage = DAY_PAGER_CENTRE) { DAY_PAGER_RANGE }

    val dayViewSelectedMillis by remember {
        derivedStateOf {
            val offset = dayViewPagerState.currentPage - DAY_PAGER_CENTRE
            todayMillis + offset * MILLIS_PER_DAY
        }
    }

    val dayViewMonth by remember {
        derivedStateOf { extractMonth(dayViewSelectedMillis) }
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
    LaunchedEffect(currentView, weekViewSundayMillis) {
        if (currentView == CalendarViewType.WEEK) {
            onSelectedDateChanged(
                extractYear(weekViewSundayMillis),
                extractMonth(weekViewSundayMillis),
                extractDay(weekViewSundayMillis)
            )
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
    LaunchedEffect(currentView, displayedYear, displayedMonth) {
        if (currentView == CalendarViewType.YEAR) {
            onSelectedDateChanged(displayedYear, displayedMonth, 0)
        }
    }

    // ── Navigate from Year → Month ───
    fun navigateToMonth(
        year: Int,
        month: Int
    ) {
        val offset = (year - todayYear) * 12 + (month - todayMonth)
        scope.launch { pagerState.scrollToPage(centreIndex + offset) }
        currentView = CalendarViewType.MONTH
    }

    // ── Top bar title ───
    val topBarTitle = when (currentView) {
        CalendarViewType.LIST -> monthName(listSelectedMonth)
        CalendarViewType.YEAR -> yearViewYear.toString()
        CalendarViewType.MONTH -> monthName(displayedMonth)
        CalendarViewType.WEEK -> monthName(weekViewCalendarMonth)
        CalendarViewType.THREE_DAY -> monthName(threeDayMonth)
        CalendarViewType.DAY -> monthName(dayViewMonth)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Body ─────────────────────────────────────────────────────
        when (currentView) {
            CalendarViewType.LIST -> {
                ListViewContent(
                    tasks = tasks,
                    todayMillis = todayMillis,
                    todayYear = todayYear,
                    todayMonth = todayMonth,
                    todayDay = todayDay,
                    selectedDayMillis = listSelectedDayMillis,
                    onDaySelected = { listSelectedDayMillis = it },
                    weekPagerState = weekPagerState,
                    weekPagerCentre = WEEK_PAGER_CENTRE,
                    tasksByDay = tasksByDay,
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
                    tasksByDay = tasksByDay,
                    topBarHeight = topBarHeight,
                    navBarHeight = navBarHeight,
                    onMonthClick = { year, month -> navigateToMonth(year, month) },
                )
            }

            CalendarViewType.MONTH -> {
                MonthViewContent(
                    tasks = tasks,
                    todayYear = todayYear,
                    todayMonth = todayMonth,
                    todayDay = todayDay,
                    selectedDay = selectedDay,
                    collapseProgress = collapseProgress,
                    pagerState = pagerState,
                    centreIndex = centreIndex,
                    tasksByDay = tasksByDay,
                    topBarHeight = topBarHeight,
                    navBarHeight = navBarHeight,
                    onDayClick = { day ->
                        if (!day.isCurrentMonth) return@MonthViewContent
                        scope.launch {
                            selectedDay = if (selectedDay == day) null else day
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
                    tasksByDay = tasksByDay,
                    topBarHeight = topBarHeight,
                    navBarHeight = navBarHeight,
                    onTaskClick = onTaskClick,
                    onWeekSelected = { sundayMillis ->
                        val thisWeekSunMillis = startOfWeekLocalMillis(todayMillis)
                        val offset =
                            ((sundayMillis - thisWeekSunMillis) / (7 * MILLIS_PER_DAY)).toInt()
                        scope.launch {
                            weekViewPagerState.animateScrollToPage(WEEK_PAGER_CENTRE + offset)
                        }
                    },
                    selectedDayMillis = weekViewSelectedDayMillis,
                    onDaySelected = { weekViewSelectedDayMillis = it },
                )
            }

            CalendarViewType.THREE_DAY -> {
                ThreeDayViewContent(
                    todayMillis = todayMillis,
                    todayYear = todayYear,
                    todayMonth = todayMonth,
                    todayDay = todayDay,
                    pagerState = threeDayPagerState,
                    pagerCentre = DAY_PAGER_CENTRE,
                    tasksByDay = tasksByDay,
                    topBarHeight = topBarHeight,
                    navBarHeight = navBarHeight,
                    onTaskClick = onTaskClick,
                    onToggleComplete = onToggleComplete,
                )
            }

            CalendarViewType.DAY -> {
                DayViewContent(
                    todayMillis = todayMillis,
                    todayYear = todayYear,
                    todayMonth = todayMonth,
                    todayDay = todayDay,
                    pagerState = dayViewPagerState,
                    pagerCentre = DAY_PAGER_CENTRE,
                    tasksByDay = tasksByDay,
                    topBarHeight = topBarHeight,
                    navBarHeight = navBarHeight,
                    onTaskClick = onTaskClick,
                    onToggleComplete = onToggleComplete,
                )
            }
        }

        // ── Top bar overlay ──────────────────────────────────────────
        CalendarTopBar(
            title = topBarTitle,
            currentView = currentView,
            showBackButton = (currentView == CalendarViewType.MONTH && selectedDay != null) ||
                    currentView == CalendarViewType.YEAR,
            onBack = {
                if (currentView == CalendarViewType.MONTH && selectedDay != null) {
                    scope.launch { selectedDay = null }
                } else if (currentView == CalendarViewType.YEAR) {
                    currentView = CalendarViewType.MONTH
                }
            },
            listDisplayMode = listDisplayMode,
            onToggleDisplayMode = {
                listDisplayMode = when (listDisplayMode) {
                    ListDisplayMode.TIMELINE -> ListDisplayMode.CARD
                    ListDisplayMode.CARD -> ListDisplayMode.TIMELINE
                }
            },
            showViewPicker = showViewPicker,
            onViewPickerToggle = { showViewPicker = true },
            onViewSelected = { view ->
                showViewPicker = false
                if (view == CalendarViewType.YEAR) {
                    val targetPage = YEAR_PAGER_CENTRE + (displayedYear - todayYear)
                    scope.launch { yearPagerState.scrollToPage(targetPage) }
                }
                currentView = view
            },
            onViewPickerDismiss = { showViewPicker = false },
            onSettingsClick = onSettingsClick,
        )
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
    listDisplayMode: ListDisplayMode,
    onToggleDisplayMode: () -> Unit,
    showViewPicker: Boolean,
    onViewPickerToggle: () -> Unit,
    onViewSelected: (CalendarViewType) -> Unit,
    onViewPickerDismiss: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
        ),
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
                        contentDescription = null,
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

            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = stringResource(Res.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        },
    )
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconLarge),
        )
    }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(view.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (view == currentView) PrimaryBlue
                            else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (view == currentView) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(OpenTasksTheme.dimens.iconDefault),
                            )
                        }
                    }
                },
                onClick = { onViewSelected(view) },
            )
        }
    }
}

private val dimens @Composable get() = OpenTasksTheme.dimens

