package com.udnahc.opentasks.ui.preview

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.ui.screens.calendar.ListDisplayMode
import com.udnahc.opentasks.ui.screens.calendar.ListViewContent
import com.udnahc.opentasks.ui.screens.calendar.WeekStripPage
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
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
@LightDarkPreview
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
            categoryNames = emptyMap(),
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            displayMode = ListDisplayMode.TIMELINE,
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@LightDarkPreview
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
            categoryNames = emptyMap(),
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            displayMode = ListDisplayMode.CARD,
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}
