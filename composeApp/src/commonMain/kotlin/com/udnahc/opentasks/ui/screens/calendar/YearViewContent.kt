package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.dayKeyFromDate
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue

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
    val isCurrentMonth = year == todayYear && month == todayMonth

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
            // Month name header
            Text(
                text = monthNameShort(month),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrentMonth) PrimaryBlue
                else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = dimens.paddingSmall),
            )

            // Day headers
            val headers = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                headers.forEach {
                    Text(
                        text = it,
                        style = OpenTasksTheme.typography.calendarEventOverflow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(dimens.spacerTiny))

            // Mini calendar grid
            val weeks = remember(year, month) { buildMonthWeeks(year, month) }
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    week.forEach { day ->
                        val isToday =
                            day.year == todayYear && day.month == todayMonth && day.day == todayDay
                        val dayKey = dayKeyFromDate(day.year, day.month, day.day)
                        val hasTasks = tasksByDay.containsKey(dayKey)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .then(
                                    if (isToday) Modifier.background(PrimaryBlue, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (day.isCurrentMonth) day.day.toString() else "",
                                style = OpenTasksTheme.typography.calendarEventOverflow,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isToday -> Color.White
                                    hasTasks && day.isCurrentMonth -> PrimaryBlue
                                    day.isCurrentMonth -> MaterialTheme.colorScheme.onBackground
                                    else -> Color.Transparent
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
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
