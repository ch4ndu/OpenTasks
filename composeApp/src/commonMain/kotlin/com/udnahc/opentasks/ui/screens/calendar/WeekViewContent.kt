package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.startOfWeekLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue

// Day name headers moved to CalendarComposables.kt (DayNameHeaders)

// ═══════════════════════════════════════════════════════════════════════════
//  WEEK VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun WeekViewContent(
    todayMillis: Long,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    weekPagerState: PagerState,
    weekPagerCentre: Int,
    weekSundayMillis: Long,
    calendarYear: Int,
    calendarMonth: Int,
    tasksByDay: Map<Long, List<Task>>,
    topBarHeight: Dp,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onWeekSelected: (sundayMillis: Long) -> Unit,
    selectedDayMillis: Long,
    onDaySelected: (Long) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val todayWeekSunMillis = remember { startOfWeekLocalMillis(todayMillis) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(topBarHeight))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = navBarHeight + dimens.fabAreaBottom),
        ) {
            val cellWidth = maxWidth / 2
            val cellHeight = maxHeight / 4

            // Pager fills entire grid; calendar overlays top-left
            Box(modifier = Modifier.fillMaxSize()) {
                // ── Full-grid pager (all 7 days) ───
                HorizontalPager(
                    state = weekPagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val weekOffset = page - weekPagerCentre
                    val pageSundayMillis = todayWeekSunMillis + weekOffset * 7 * MILLIS_PER_DAY

                    WeekViewDayPagerContent(
                        sundayMillis = pageSundayMillis,
                        todayMillis = todayMillis,
                        tasksByDay = tasksByDay,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        onTaskClick = onTaskClick,
                        selectedDayMillis = selectedDayMillis,
                        onDaySelected = onDaySelected,
                    )
                }

                // ── Calendar overlay at top-left, consumes touches ───
                Box(
                    modifier = Modifier
                        .size(cellWidth, cellHeight)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                ) {
                    WeekViewMiniCalendar(
                        year = calendarYear,
                        month = calendarMonth,
                        highlightedWeekSundayMillis = weekSundayMillis,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        tasksByDay = tasksByDay,
                        onDayClick = { dayMillis ->
                            val sundayMillis = startOfWeekLocalMillis(dayMillis)
                            onWeekSelected(sundayMillis)
                        },
                    )
                }
            }
        }
    }
}

// ── Pager content: 2x4 grid, [0,0] empty (calendar overlays), rest are days ─

@Composable
private fun WeekViewDayPagerContent(
    sundayMillis: Long,
    todayMillis: Long,
    tasksByDay: Map<Long, List<Task>>,
    cellWidth: Dp,
    cellHeight: Dp,
    onTaskClick: (Task) -> Unit,
    selectedDayMillis: Long,
    onDaySelected: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (row in 0..3) {
            Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                for (col in 0..1) {
                    if (row == 0 && col == 0) {
                        // Empty space — mini calendar overlays here
                        Spacer(modifier = Modifier.width(cellWidth).fillMaxHeight())
                    } else {
                        val dayIndex = row * 2 + col - 1  // 0=Sun..6=Sat
                        val dayMillis = sundayMillis + dayIndex * MILLIS_PER_DAY
                        WeekViewDayCell(
                            dayMillis = dayMillis,
                            todayMillis = todayMillis,
                            tasksByDay = tasksByDay,
                            onTaskClick = onTaskClick,
                            isSelected = dayMillis == selectedDayMillis,
                            onDaySelected = onDaySelected,
                            modifier = Modifier.width(cellWidth).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

// ── Single day cell ─────────────────────────────────────────────────────────

@Composable
private fun WeekViewDayCell(
    dayMillis: Long,
    todayMillis: Long,
    tasksByDay: Map<Long, List<Task>>,
    onTaskClick: (Task) -> Unit,
    isSelected: Boolean,
    onDaySelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    val isToday = dayMillis == todayMillis
    val dayOfMonth = remember(dayMillis) { extractDay(dayMillis) }
    val dayIndex = remember(dayMillis) {
        com.udnahc.opentasks.data.extensions.dayOfWeekIndex(
            extractYear(dayMillis), extractMonth(dayMillis), extractDay(dayMillis)
        )
    }
    val dk = remember(dayMillis) { dayKey(dayMillis) }
    val dayTasks = remember(dk, tasksByDay) {
        tasksByDay[dk] ?: emptyList()
    }

    val textColor = when {
        isSelected -> Color.White
        isToday -> PrimaryBlue
        else -> MaterialTheme.colorScheme.onBackground
    }

    Card(
        modifier = modifier.padding(2.dp),
        shape = RoundedCornerShape(dimens.cornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) Modifier.background(
                        PrimaryBlue.copy(alpha = 0.25f),
                        RoundedCornerShape(
                            topStart = dimens.cornerLarge,
                            topEnd = dimens.cornerLarge,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    ) else Modifier
                )
                .clickable { onDaySelected(dayMillis) }
                .padding(dimens.paddingSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: day name + date number
            Text(
                text = DAY_NAMES_SHORT[dayIndex],
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) PrimaryBlue
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(dimens.spacerSmall))
            Box(
                modifier = Modifier
                    .size(dimens.calendarWeekDayCircle)
                    .then(
                        when {
                            isSelected -> Modifier.background(PrimaryBlue, CircleShape)
                            isToday -> Modifier.background(
                                MaterialTheme.colorScheme.onBackground, CircleShape
                            )
                            else -> Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayOfMonth.toString(),
                    style = OpenTasksTheme.typography.calendarDayNumber,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                )
            }

            Spacer(Modifier.height(dimens.spacerSmall))

            // Event bars (limited, no LazyColumn to avoid scroll conflicts)
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val eventBarHeight = dimens.calendarMonthGridEventHeight
                val overflowHeight = dimens.calendarEventOverflowHeight
                val availableHeight = maxHeight
                val maxVisible = ((availableHeight - overflowHeight) / eventBarHeight).toInt()
                    .coerceAtLeast(1)

                Column(modifier = Modifier.fillMaxSize()) {
                    val visibleTasks = if (dayTasks.size <= maxVisible + 1) dayTasks
                    else dayTasks.take(maxVisible)
                    val overflow = dayTasks.size - visibleTasks.size

                    visibleTasks.forEach { task ->
                        val onClick = remember(task.id) { { onTaskClick(task) } }
                        TimelineEventBar(
                            task = task,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimens.calendarMonthGridEventHeight)
                                .padding(vertical = 1.dp),
                            onClick = onClick,
                        )
                    }
                    if (overflow > 0) {
                        Text(
                            text = "+$overflow",
                            style = OpenTasksTheme.typography.calendarEventOverflow,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Mini calendar with animated week highlight ──────────────────────────────

@Composable
private fun WeekViewMiniCalendar(
    year: Int,
    month: Int,
    highlightedWeekSundayMillis: Long,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    tasksByDay: Map<Long, List<Task>>,
    onDayClick: (dayMillis: Long) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens

    Card(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        shape = RoundedCornerShape(dimens.cornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimens.paddingSmall),
        ) {
            // Month name header
            Text(
                text = "${monthNameShort(month)} $year",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (year == todayYear && month == todayMonth) PrimaryBlue
                else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = dimens.spacerTiny),
            )

            // Grid (delegated to shared MiniCalendarGrid)
            MiniCalendarGrid(
                year = year,
                month = month,
                todayYear = todayYear,
                todayMonth = todayMonth,
                todayDay = todayDay,
                highlightedWeekSundayMillis = highlightedWeekSundayMillis,
                tasksByDay = tasksByDay,
                onDayClick = onDayClick,
                showMonthHeader = false,
                useAspectRatioCells = false,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  PREVIEWS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
@Preview
private fun WeekViewDayCellPreview() {
    OpenTasksTheme {
        Box(modifier = Modifier.size(200.dp)) {
            WeekViewDayCell(
                dayMillis = PreviewSampleData.sampleTodayMillis,
                todayMillis = PreviewSampleData.sampleTodayMillis,
                tasksByDay = PreviewSampleData.sampleTasksByDay,
                onTaskClick = {},
                isSelected = true,
                onDaySelected = {},
            )
        }
    }
}

@Composable
@Preview
private fun WeekViewMiniCalendarPreview() {
    OpenTasksTheme {
        Box(modifier = Modifier.size(200.dp)) {
            WeekViewMiniCalendar(
                year = PreviewSampleData.SAMPLE_YEAR,
                month = PreviewSampleData.SAMPLE_MONTH,
                highlightedWeekSundayMillis = PreviewSampleData.sampleWeekSundayMillis,
                todayYear = PreviewSampleData.SAMPLE_YEAR,
                todayMonth = PreviewSampleData.SAMPLE_MONTH,
                todayDay = PreviewSampleData.SAMPLE_DAY,
                tasksByDay = PreviewSampleData.sampleTasksByDay,
                onDayClick = {},
            )
        }
    }
}

@Composable
@Preview
private fun WeekViewContentPreview() {
    OpenTasksTheme {
        WeekViewContent(
            weekPagerState = rememberPagerState(initialPage = 520) { 1040 },
            weekPagerCentre = 520,
            todayMillis = PreviewSampleData.sampleTodayMillis,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            weekSundayMillis = PreviewSampleData.sampleWeekSundayMillis,
            calendarYear = PreviewSampleData.SAMPLE_YEAR,
            calendarMonth = PreviewSampleData.SAMPLE_MONTH,
            tasksByDay = PreviewSampleData.sampleTasksByDay,
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onTaskClick = {},
            onWeekSelected = {},
            selectedDayMillis = PreviewSampleData.sampleTodayMillis,
            onDaySelected = {},
        )
    }
}
