package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.formatDateLabel
import com.udnahc.opentasks.data.extensions.startOfWeekLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.mon
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.wed
import org.jetbrains.compose.resources.stringResource

// ═══════════════════════════════════════════════════════════════════════════
//  LIST VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun ListViewContent(
    tasks: List<Task>,
    todayMillis: Long,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDayMillis: Long,
    onDaySelected: (Long) -> Unit,
    weekPagerState: PagerState,
    weekPagerCentre: Int,
    tasksByDay: Map<Long, List<Task>>,
    topBarHeight: Dp,
    navBarHeight: Dp,
    displayMode: ListDisplayMode,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    // Compute the Sunday of the week containing "today"
    val todayWeekSunMillis = remember { startOfWeekLocalMillis(todayMillis) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(topBarHeight))

        // ── Swipeable week pager ───
        HorizontalPager(
            state = weekPagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val weekOffset = page - weekPagerCentre
            val weekSunMillis = todayWeekSunMillis + weekOffset * 7 * MILLIS_PER_DAY

            WeekStripPage(
                weekSundayMillis = weekSunMillis,
                todayMillis = todayMillis,
                selectedDayMillis = selectedDayMillis,
                tasksByDay = tasksByDay,
                onDaySelected = onDaySelected,
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = OpenTasksTheme.dimens.dividerThin,
        )

        // ── Tasks for the selected day ───
        val selectedDayKey = dayKey(selectedDayMillis)
        val isToday = selectedDayMillis == todayMillis

        val dayTasks = remember(selectedDayKey, tasks) {
            tasks.filter { val dl = it.deadline; dl != null && dayKey(dl) == selectedDayKey }
                .sortedBy { it.deadline }
        }

        when (displayMode) {
            ListDisplayMode.TIMELINE -> TimelineTaskList(
                dayTasks = dayTasks,
                navBarHeight = navBarHeight,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
            )

            ListDisplayMode.CARD -> CardTaskList(
                dayTasks = dayTasks,
                selectedDayMillis = selectedDayMillis,
                isToday = isToday,
                navBarHeight = navBarHeight,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
            )
        }
    }
}

@Composable
private fun TimelineTaskList(
    dayTasks: List<Task>,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge),
    ) {
        items(dayTasks, key = { it.id }) { task ->
            TimelineTaskRow(
                task = task,
                isFirst = dayTasks.first() == task,
                isLast = dayTasks.last() == task,
                onToggleComplete = { onToggleComplete(task) },
                onClick = { onTaskClick(task) },
            )
        }

        if (dayTasks.isEmpty()) {
            item(key = "empty") {
                EmptyDayPlaceholder()
            }
        }
    }
}

@Composable
private fun CardTaskList(
    dayTasks: List<Task>,
    selectedDayMillis: Long,
    isToday: Boolean,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Text(
        text = if (isToday) stringResource(Res.string.today).uppercase() else formatDateLabel(selectedDayMillis),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = dimens.paddingXLarge, end = dimens.paddingXLarge, top = dimens.paddingXLarge, bottom = dimens.paddingMedium),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge),
    ) {
        items(dayTasks, key = { it.id }) { task ->
            CardTaskRow(
                task = task,
                isToday = isToday,
                onToggleComplete = { onToggleComplete(task) },
                onClick = { onTaskClick(task) },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = dimens.dividerThin,
            )
        }

        if (dayTasks.isEmpty()) {
            item(key = "empty") {
                EmptyDayPlaceholder()
            }
        }
    }
}

/** A single week page inside the week pager. Shows Sun–Sat with selectable days. */
@Composable
private fun WeekStripPage(
    weekSundayMillis: Long,
    todayMillis: Long,
    selectedDayMillis: Long,
    tasksByDay: Map<Long, List<Task>>,
    onDaySelected: (Long) -> Unit,
) {
    val dayLabels = listOf(
        stringResource(Res.string.sun),
        stringResource(Res.string.mon),
        stringResource(Res.string.tue),
        stringResource(Res.string.wed),
        stringResource(Res.string.thu),
        stringResource(Res.string.fri),
        stringResource(Res.string.sat),
    )

    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingSmall, vertical = dimens.paddingMedium),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (i in 0..6) {
            val dayMillis = weekSundayMillis + i * MILLIS_PER_DAY
            val dayNum = extractDay(dayMillis)
            val isToday = dayMillis == todayMillis
            val isSelected = dayMillis == selectedDayMillis
            val dk = dayKey(dayMillis)
            val hasTasks = tasksByDay.containsKey(dk)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDaySelected(dayMillis) },
            ) {
                Text(
                    text = dayLabels[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected && !isToday) PrimaryBlue
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
                Box(
                    modifier = Modifier
                        .size(dimens.calendarDaySize)
                        .then(
                            when {
                                isToday -> Modifier.background(PrimaryBlue, CircleShape)
                                isSelected -> Modifier.background(
                                    PrimaryBlue.copy(alpha = 0.12f), CircleShape
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
                            isToday -> Color.White
                            isSelected -> PrimaryBlue
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
    }
}

@Composable
@Preview
private fun WeekStripPagePreview() {
    OpenTasksTheme {
        WeekStripPage(
            weekSundayMillis = PreviewSampleData.sampleWeekSundayMillis,
            todayMillis = PreviewSampleData.sampleTodayMillis,
            selectedDayMillis = PreviewSampleData.sampleTodayMillis,
            tasksByDay = PreviewSampleData.sampleTasksByDay,
            onDaySelected = {},
        )
    }
}

@Composable
@Preview
private fun ListViewContentTimelinePreview() {
    OpenTasksTheme {
        val weekPagerState = rememberPagerState(initialPage = 520) { 1040 }
        ListViewContent(
            tasks = PreviewSampleData.sampleTasks,
            todayMillis = PreviewSampleData.sampleTodayMillis,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            selectedDayMillis = PreviewSampleData.sampleTodayMillis,
            onDaySelected = {},
            weekPagerState = weekPagerState,
            weekPagerCentre = 520,
            tasksByDay = PreviewSampleData.sampleTasksByDay,
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            displayMode = ListDisplayMode.TIMELINE,
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@Preview
private fun ListViewContentCardPreview() {
    OpenTasksTheme {
        val weekPagerState = rememberPagerState(initialPage = 520) { 1040 }
        ListViewContent(
            tasks = PreviewSampleData.sampleTasks,
            todayMillis = PreviewSampleData.sampleTodayMillis,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            selectedDayMillis = PreviewSampleData.sampleTodayMillis,
            onDaySelected = {},
            weekPagerState = weekPagerState,
            weekPagerCentre = 520,
            tasksByDay = PreviewSampleData.sampleTasksByDay,
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            displayMode = ListDisplayMode.CARD,
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}
