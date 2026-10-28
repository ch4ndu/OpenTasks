package com.udnahc.opentasks.ui.screens.calendar

import com.udnahc.opentasks.ui.theme.isReimagined
import com.udnahc.opentasks.ui.theme.WindowSizeCategory
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
    taskDayKeys: Set<Long>,
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
            val columns = if (!isReimagined) 3 else when (OpenTasksTheme.windowSizeCategory) {
                WindowSizeCategory.COMPACT -> 2
                WindowSizeCategory.MEDIUM -> 3
                WindowSizeCategory.EXPANDED -> 4
            }
            for (row in 0 until 12 / columns) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
                ) {
                    for (col in 0 until columns) {
                        val month = row * columns + col + 1
                        MiniMonthCard(
                            year = year,
                            month = month,
                            todayYear = todayYear,
                            todayMonth = todayMonth,
                            todayDay = todayDay,
                            taskDayKeys = taskDayKeys,
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
internal fun MiniMonthCard(
    year: Int,
    month: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    taskDayKeys: Set<Long>,
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
                taskDayKeys = taskDayKeys,
                onDayClick = null,
                showMonthHeader = true,
                useAspectRatioCells = true,
            )
        }
    }
}
