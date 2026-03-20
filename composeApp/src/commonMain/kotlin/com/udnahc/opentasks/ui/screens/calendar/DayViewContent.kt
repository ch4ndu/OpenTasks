package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_check_box_outline
import org.jetbrains.compose.resources.painterResource

// ═══════════════════════════════════════════════════════════════════════════
//  DAY VIEW
// ═══════════════════════════════════════════════════════════════════════════

private val DAY_NAMES_SHORT = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
internal fun DayViewContent(
    todayMillis: Long,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    pagerState: PagerState,
    pagerCentre: Int,
    tasksByDay: Map<Long, List<Task>>,
    topBarHeight: Dp,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val timeColumnWidth = dimens.calendarTimeColumnWidth
    val hourHeight = dimens.calendarTimelineHeight
    val timeColumnScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val stripListState = rememberLazyListState(
        initialFirstVisibleItemIndex = pagerCentre - 3,
    )

    val selectedDayMillis by remember {
        derivedStateOf { todayMillis + (pagerState.currentPage - pagerCentre) * MILLIS_PER_DAY }
    }

    // When timeline pager changes, scroll strip only if selected day is off-screen
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        val firstVisible = stripListState.firstVisibleItemIndex
        val lastVisible = firstVisible + stripListState.layoutInfo.visibleItemsInfo.size - 1
        if (page < firstVisible || page > lastVisible) {
            stripListState.animateScrollToItem((page - 3).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(topBarHeight))

        // ── Week strip (LazyRow, no snap) ───
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val dayStripWidth = maxWidth / 7
            LazyRow(
                state = stripListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.paddingMedium),
            ) {
                items(
                    count = pagerState.pageCount,
                    key = { it },
                ) { index ->
                    val dayOffset = index - pagerCentre
                    val dayMillis = todayMillis + dayOffset * MILLIS_PER_DAY
                    val onClick = remember(index) { { scope.launch { pagerState.scrollToPage(index) }; Unit } }
                    DayViewStripItem(
                        dayMillis = dayMillis,
                        todayMillis = todayMillis,
                        isSelected = index == pagerState.currentPage,
                        tasksByDay = tasksByDay,
                        modifier = Modifier.width(dayStripWidth),
                        onClick = onClick,
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = dimens.dividerThin,
        )

        // ── Timeline area ───
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = navBarHeight + dimens.fabAreaBottom),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Time labels column (fixed left) ───
                Column(modifier = Modifier.width(timeColumnWidth)) {
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
                            .verticalScroll(timeColumnScrollState),
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

                // ── Day timeline (pager, 1 day per page) ───
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val dayOffset = page - pagerCentre
                    val dayMillis = todayMillis + dayOffset * MILLIS_PER_DAY

                    DayViewTimeline(
                        dayMillis = dayMillis,
                        todayMillis = todayMillis,
                        tasksByDay = tasksByDay,
                        hourHeight = hourHeight,
                        onTimelineScrolled = { scrollValue ->
                            scope.launch { timeColumnScrollState.scrollTo(scrollValue) }
                        },
                        onTaskClick = onTaskClick,
                        onToggleComplete = onToggleComplete,
                    )
                }
            }
        }
    }
}

// ── Day strip item (single day in the week strip pager) ─────────────────────

@Composable
private fun DayViewStripItem(
    dayMillis: Long,
    todayMillis: Long,
    isSelected: Boolean,
    tasksByDay: Map<Long, List<Task>>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val isToday = dayMillis == todayMillis
    val dayNum = remember(dayMillis) { extractDay(dayMillis) }
    val dayOfWeekIdx = remember(dayMillis) {
        dayOfWeekIndex(
            extractYear(dayMillis), extractMonth(dayMillis), extractDay(dayMillis)
        )
    }
    val dk = remember(dayMillis) { dayKey(dayMillis) }
    val hasTasks = tasksByDay.containsKey(dk)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick),
    ) {
        Text(
            text = DAY_NAMES_SHORT[dayOfWeekIdx],
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(dimens.spacerSmall))
        Box(
            modifier = Modifier
                .size(dimens.calendarDaySize)
                .then(
                    when {
                        isSelected -> Modifier.background(
                            PrimaryBlue, CircleShape
                        )

                        isToday -> Modifier.background(
                            MaterialTheme.colorScheme.onBackground,
                            CircleShape
                        )

                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayNum.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isToday -> PrimaryBlue
                    else -> MaterialTheme.colorScheme.onBackground
                },
            )
        }
        if (hasTasks) {
            Spacer(Modifier.height(dimens.spacerTiny))
            Box(
                modifier = Modifier
                    .size(dimens.calendarDotSize)
                    .background(
                        if (isToday) Color.White else PrimaryBlue,
                        CircleShape,
                    ),
            )
        } else {
            Spacer(Modifier.height(dimens.spacerMedium))
        }
    }
}

// ── Single day timeline ─────────────────────────────────────────────────────

@Composable
private fun DayViewTimeline(
    dayMillis: Long,
    todayMillis: Long,
    tasksByDay: Map<Long, List<Task>>,
    hourHeight: Dp,
    onTimelineScrolled: (Int) -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { onTimelineScrolled(it) }
    }
    val dimens = OpenTasksTheme.dimens
    val dk = remember(dayMillis) { dayKey(dayMillis) }
    val dayTasks = remember(dk, tasksByDay) { tasksByDay[dk] ?: emptyList() }
    val allDayTasks = remember(dayTasks) {
        dayTasks.filter { it.deadline != null && extractHour(it.deadline) == 0 && extractMinute(it.deadline) == 0 }
    }
    val timedTasks = remember(dayTasks) {
        dayTasks.filter { it.deadline != null && !(extractHour(it.deadline) == 0 && extractMinute(it.deadline) == 0) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                allDayTasks.take(3).forEach { task ->
                    val onClick = remember(task.id) { { onTaskClick(task) } }
                    val onToggle = remember(task.id) { { onToggleComplete(task) } }
                    DayViewEventBar(
                        task = task,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimens.threeDayEventMinHeight)
                            .padding(vertical = 2.dp),
                        onClick = onClick,
                        onToggleComplete = onToggle,
                    )
                }
                if (allDayTasks.size > 3) {
                    Text(
                        text = "+${allDayTasks.size - 3}",
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
                val hour = extractHour(task.deadline!!)
                val minute = extractMinute(task.deadline!!)
                val yOffset = hourHeight * hour + hourHeight * (minute / 60f)
                val onClick = remember(task.id) { { onTaskClick(task) } }
                val onToggle = remember(task.id) { { onToggleComplete(task) } }

                DayViewEventBar(
                    task = task,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimens.threeDayEventMinHeight)
                        .offset(y = yOffset)
                        .padding(horizontal = 1.dp),
                    onClick = onClick,
                    onToggleComplete = onToggle,
                )
            }
        }
    }
}

// ── Event bar ───────────────────────────────────────────────────────────────

@Composable
private fun DayViewEventBar(
    task: Task,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val priorityColor = taskPriorityColor(task.priority)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.cornerTiny))
            .background(priorityColor.copy(alpha = if (task.isCompleted) 0.1f else 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                if (task.isCompleted) Res.drawable.ic_check_box
                else Res.drawable.ic_check_box_outline
            ),
            contentDescription = null,
            tint = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
            else priorityColor,
            modifier = Modifier
                .size(dimens.iconMedium)
                .clickable(onClick = onToggleComplete),
        )
        Spacer(Modifier.width(dimens.paddingSmall))
        Text(
            text = task.title,
            style = OpenTasksTheme.typography.calendarEventTitle,
            color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
            else priorityColor,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (task.deadline != null) {
            val hour = extractHour(task.deadline)
            val minute = extractMinute(task.deadline)
            if (hour != 0 || minute != 0) {
                Spacer(Modifier.width(dimens.paddingSmall))
                Text(
                    text = formatTime12Hr(hour, minute),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else priorityColor,
                    maxLines = 1,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  PREVIEWS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
@Preview
private fun DayViewEventBarPreview() {
    OpenTasksTheme {
        DayViewEventBar(
            task = PreviewSampleData.sampleTasks[0],
            modifier = Modifier.fillMaxWidth().height(24.dp),
            onClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@Preview
private fun DayViewStripItemPreview() {
    OpenTasksTheme {
        Box(modifier = Modifier.width(56.dp)) {
            DayViewStripItem(
                dayMillis = PreviewSampleData.sampleTodayMillis,
                todayMillis = PreviewSampleData.sampleTodayMillis,
                isSelected = true,
                tasksByDay = PreviewSampleData.sampleTasksByDay,
                onClick = {},
            )
        }
    }
}

@Composable
@Preview
private fun DayViewContentPreview() {
    OpenTasksTheme {
        DayViewContent(
            todayMillis = PreviewSampleData.sampleTodayMillis,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            pagerState = rememberPagerState(initialPage = 3650) { 7300 },
            pagerCentre = 3650,
            tasksByDay = PreviewSampleData.sampleTasksByDay,
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}
