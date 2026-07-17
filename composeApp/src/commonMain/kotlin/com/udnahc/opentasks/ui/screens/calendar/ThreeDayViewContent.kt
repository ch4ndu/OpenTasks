package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.formatTime12Hr
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.usecase.task.CalendarDayTasks
import com.udnahc.opentasks.domain.usecase.task.truncateWithOverflow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.calendar_overflow
import org.jetbrains.compose.resources.stringResource

// ═══════════════════════════════════════════════════════════════════════════
//  THREE-DAY VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun ThreeDayViewContent(
    todayMillis: Long,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    pagerState: PagerState,
    pagerCentre: Int,
    tasksByDay: Map<Long, List<Task>>,
    timelineTasksByDay: Map<Long, CalendarDayTasks>,
    topBarHeight: Dp,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val timeColumnWidth = dimens.calendarTimeColumnWidth
    val hourHeight = dimens.calendarTimelineHeight
    val scrollState = rememberScrollState()
    val dayHeaderHeight =
        dimens.paddingMedium + dimens.calendarDayHeaderHeight + dimens.spacerSmall + dimens.calendarWeekDayCircle + dimens.paddingSmall

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(topBarHeight))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = navBarHeight + dimens.fabAreaBottom),
        ) {
            val dayColumnWidth = (maxWidth - timeColumnWidth) / 3

            Row(modifier = Modifier.fillMaxSize()) {
                // ── Time labels column (fixed left) ───
                Column(modifier = Modifier.width(timeColumnWidth)) {
                    // Spacer for day headers area
                    Spacer(Modifier.height(dayHeaderHeight))
                    // Spacer for all-day events area
                    Spacer(Modifier.height(dimens.threeDayAllDayHeight))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = dimens.dividerThin,
                    )
                    // Scrollable hour labels synced with timeline
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                    ) {
                        for (hour in 0..23) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(hourHeight),
                                contentAlignment = Alignment.TopEnd,
                            ) {
                                Text(
                                    text = formatTime12Hr(hour, 0).replace(":00 ", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.padding(
                                        end = dimens.paddingSmall,
                                        top = dimens.paddingTiny,
                                    ),
                                )
                            }
                        }
                    }
                }

                // ── Day columns (pager, 3 visible) ───
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(dayColumnWidth),
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val dayOffset = page - pagerCentre
                    val dayMillis = todayMillis + dayOffset * MILLIS_PER_DAY

                    ThreeDayColumn(
                        dayMillis = dayMillis,
                        todayMillis = todayMillis,
                        dayTasks = timelineTasksByDay[dayKey(dayMillis)] ?: CalendarDayTasks(),
                        hourHeight = hourHeight,
                        dayHeaderHeight = dayHeaderHeight,
                        scrollState = scrollState,
                        onTaskClick = onTaskClick,
                        onToggleComplete = onToggleComplete,
                    )
                }
            }
        }
    }
}

// ── Single day column ───────────────────────────────────────────────────────

@Composable
private fun ThreeDayColumn(
    dayMillis: Long,
    todayMillis: Long,
    dayTasks: CalendarDayTasks,
    hourHeight: Dp,
    dayHeaderHeight: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val isToday = dayMillis == todayMillis
    val dayOfMonth = remember(dayMillis) { extractDay(dayMillis) }
    val dayOfWeekIdx = remember(dayMillis) {
        dayOfWeekIndex(
            extractYear(dayMillis), extractMonth(dayMillis), extractDay(dayMillis)
        )
    }
    val allDayTasks = dayTasks.allDayTasks
    val timedTasks = dayTasks.timedTasks

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Day header ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(dayHeaderHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = calendarWeekdayShort(dayOfWeekIdx),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(dimens.spacerSmall))
            Box(
                modifier = Modifier
                    .size(dimens.calendarWeekDayCircle)
                    .then(
                        if (isToday) Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayOfMonth.toString(),
                    style = OpenTasksTheme.typography.calendarDayNumber,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) PrimaryBlue else MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // ── All-day events ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.threeDayAllDayHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp),
            ) {
                val (visibleAllDay, allDayOverflow) = truncateWithOverflow(allDayTasks, 3)
                visibleAllDay.forEach { task ->
                    TimelineEventBar(
                        task = task,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimens.threeDayEventMinHeight)
                            .padding(vertical = 2.dp),
                        onClick = { onTaskClick(task) },
                        onToggleComplete = { onToggleComplete(task) },
                    )
                }
                if (allDayOverflow > 0) {
                    Text(
                        text = stringResource(Res.string.calendar_overflow, allDayOverflow),
                        style = OpenTasksTheme.typography.calendarEventOverflow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = dimens.dividerThin,
        )

        // ── Scrollable timeline ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(scrollState),
        ) {
            // Hour grid lines
            Column(modifier = Modifier.fillMaxWidth()) {
                for (hour in 0..23) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourHeight),
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            thickness = dimens.dividerThin,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
            }

            // Positioned timed events
            timedTasks.forEach { task ->
                val yOffset = hourHeight * (dayTasks.timedTaskStartMinutes[task.id] ?: 0) / 60f
                TimelineEventBar(
                    task = task,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimens.threeDayEventMinHeight)
                        .offset(y = yOffset)
                        .padding(horizontal = 1.dp),
                    onClick = { onTaskClick(task) },
                    onToggleComplete = { onToggleComplete(task) },
                )
            }
        }
    }
}
