package com.udnahc.opentasks.ui.preview

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.domain.usecase.task.projectCalendarDay
import com.udnahc.opentasks.ui.screens.calendar.ThreeDayViewContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun ThreeDayViewContentPreview() {
    OpenTasksTheme {
        ThreeDayViewContent(
            todayMillis = PreviewSampleData.sampleTodayMillis,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            pagerState = rememberPagerState(initialPage = 3650) { 7300 },
            pagerCentre = 3650,
            calendarDaysByDay = PreviewSampleData.sampleTasksByDay.map { (dayKey, tasks) ->
                dayKey to projectCalendarDay(
                    tasks,
                    dayKey,
                    PreviewSampleData.SAMPLE_TODAY_DAY_KEY,
                )
            }.toMap(),
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}
