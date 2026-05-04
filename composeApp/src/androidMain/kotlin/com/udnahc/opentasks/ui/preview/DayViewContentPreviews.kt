package com.udnahc.opentasks.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.domain.usecase.task.splitCalendarDayTasks
import com.udnahc.opentasks.ui.screens.calendar.DayViewContent
import com.udnahc.opentasks.ui.screens.calendar.DayViewStripItem
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
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
@LightDarkPreview
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
            timelineTasksByDay = PreviewSampleData.sampleTasksByDay.mapValues { (_, tasks) ->
                splitCalendarDayTasks(tasks)
            },
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}
