package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

// ═══════════════════════════════════════════════════════════════════════════
//  YEAR VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun YearViewContent(
    pagerState: PagerState,
    centreIndex: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    tasksByDay: Map<Long, List<Task>>,
    topBarHeight: Dp,
    navBarHeight: Dp,
    onMonthClick: (Int, Int) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topBarHeight),
    ) { page ->
        val year = todayYear + (page - centreIndex)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 4 rows × 3 columns
            for (row in 0..3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
                ) {
                    for (col in 0..2) {
                        val month = row * 3 + col + 1
                        MiniMonthCard(
                            year = year,
                            month = month,
                            todayYear = todayYear,
                            todayMonth = todayMonth,
                            todayDay = todayDay,
                            tasksByDay = tasksByDay,
                            onClick = { onMonthClick(year, month) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(dimens.spacerLarge))
            }
            // Bottom padding for nav bar
            Spacer(Modifier.height(navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge))
        }
    }
}

@Composable
private fun MiniMonthCard(
    year: Int,
    month: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    tasksByDay: Map<Long, List<Task>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(dimens.cornerXLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(dimens.paddingMedium),
        ) {
            MiniCalendarGrid(
                year = year,
                month = month,
                todayYear = todayYear,
                todayMonth = todayMonth,
                todayDay = todayDay,
                tasksByDay = tasksByDay,
                onDayClick = null,
                showMonthHeader = true,
                useAspectRatioCells = true,
            )
        }
    }
}

@Composable
@Preview
private fun MiniMonthCardPreview() {
    OpenTasksTheme {
        MiniMonthCard(
            year = PreviewSampleData.SAMPLE_YEAR,
            month = PreviewSampleData.SAMPLE_MONTH,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            tasksByDay = PreviewSampleData.sampleTasksByDay,
            onClick = {},
        )
    }
}

@Composable
@Preview
private fun YearViewContentPreview() {
    OpenTasksTheme {
        val pagerState = rememberPagerState(initialPage = 10) { 20 }
        YearViewContent(
            pagerState = pagerState,
            centreIndex = 10,
            todayYear = PreviewSampleData.SAMPLE_YEAR,
            todayMonth = PreviewSampleData.SAMPLE_MONTH,
            todayDay = PreviewSampleData.SAMPLE_DAY,
            tasksByDay = PreviewSampleData.sampleTasksByDay,
            topBarHeight = 64.dp,
            navBarHeight = 0.dp,
            onMonthClick = { _, _ -> },
        )
    }
}
