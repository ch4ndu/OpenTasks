package com.udnahc.opentasks.ui.preview

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.ui.screens.calendar.MiniMonthCard
import com.udnahc.opentasks.ui.screens.calendar.YearViewContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun MiniMonthCardPreview() {
    OpenTasksTheme {
        MiniMonthCard(
            year = PreviewSampleData.SAMPLE_YEAR,
            month = PreviewSampleData.SAMPLE_MONTH,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            taskDayKeys = PreviewSampleData.sampleTasksByDay.keys,
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun YearViewContentPreview() {
    OpenTasksTheme {
        val pagerState = rememberPagerState(initialPage = 10) { 20 }
        YearViewContent(
            pagerState = pagerState,
            centreIndex = 10,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            taskDayKeys = PreviewSampleData.sampleTasksByDay.keys,
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onMonthClick = { _, _ -> },
        )
    }
}
