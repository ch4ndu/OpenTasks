package com.udnahc.opentasks.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.domain.usecase.task.projectCalendarDay
import com.udnahc.opentasks.ui.screens.calendar.WeekViewContent
import com.udnahc.opentasks.ui.screens.calendar.WeekViewDayCell
import com.udnahc.opentasks.ui.screens.calendar.WeekViewMiniCalendar
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun WeekViewDayCellPreview() {
    OpenTasksTheme {
        Box(modifier = Modifier.size(200.dp)) {
            WeekViewDayCell(
                dayMillis = PreviewSampleData.sampleTodayMillis,
                todayMillis = PreviewSampleData.sampleTodayMillis,
                dayProjection = projectCalendarDay(
                    PreviewSampleData.sampleTasksByDay[PreviewSampleData.SAMPLE_TODAY_DAY_KEY].orEmpty(),
                    PreviewSampleData.SAMPLE_TODAY_DAY_KEY,
                    PreviewSampleData.SAMPLE_TODAY_DAY_KEY,
                ),
                onTaskClick = {},
                isSelected = true,
                onDaySelected = {},
            )
        }
    }
}

@Composable
@LightDarkPreview
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
                taskDayKeys = PreviewSampleData.sampleTasksByDay.keys,
                onDayClick = {},
            )
        }
    }
}

@Composable
@LightDarkPreview
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
            calendarDaysByDay = previewCalendarDaysByDay(),
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onTaskClick = {},
            onWeekSelected = {},
            selectedDayMillis = PreviewSampleData.sampleTodayMillis,
            onDaySelected = {},
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
