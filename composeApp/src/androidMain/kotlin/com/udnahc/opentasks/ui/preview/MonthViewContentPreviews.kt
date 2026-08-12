package com.udnahc.opentasks.ui.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.ui.screens.calendar.CalendarDay
import com.udnahc.opentasks.ui.screens.calendar.DayNameHeaders
import com.udnahc.opentasks.ui.screens.calendar.MonthViewContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.domain.usecase.task.CalendarDayProjection
import com.udnahc.opentasks.domain.usecase.task.projectCalendarDay

@Composable
@LightDarkPreview
private fun DayHeadersPreview() {
    OpenTasksTheme {
        DayNameHeaders()
    }
}

@Composable
@LightDarkPreview
private fun MonthViewContentPreview() {
    OpenTasksTheme {
        MonthViewContent(
            collapseProgress = remember { Animatable(0f) },
            pagerState = rememberPagerState(initialPage = 120) { 240 },
            selectedDayProjection = CalendarDayProjection(),
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            selectedDay = null,
            centreIndex = 120,
            calendarDaysByDay = previewCalendarDaysByDay(),
            categoryNames = emptyMap(),
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onDayClick = {},
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun MonthViewContentCollapsedPreview() {
    OpenTasksTheme {
        MonthViewContent(
            collapseProgress = remember { Animatable(1f) },
            pagerState = rememberPagerState(initialPage = 120) { 240 },
            selectedDayProjection = projectCalendarDay(
                PreviewSampleData.sampleTasks,
                PreviewSampleData.SAMPLE_TODAY_DAY_KEY,
                PreviewSampleData.SAMPLE_TODAY_DAY_KEY,
            ),
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            selectedDay = CalendarDay(
                PreviewSampleData.SAMPLE_YEAR,
                PreviewSampleData.SAMPLE_MONTH,
                PreviewSampleData.SAMPLE_DAY,
                true,
            ),
            centreIndex = 120,
            calendarDaysByDay = previewCalendarDaysByDay(),
            categoryNames = emptyMap(),
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onDayClick = {},
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

private fun previewCalendarDaysByDay() = PreviewSampleData.sampleTasksByDay
    .map { (dayKey, tasks) ->
        dayKey to projectCalendarDay(
            tasks,
            dayKey,
            PreviewSampleData.SAMPLE_TODAY_DAY_KEY,
        )
    }
    .toMap()
