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
import androidx.compose.ui.unit.Dp
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.startOfWeekLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.usecase.task.CalendarDayProjection
import com.udnahc.opentasks.domain.usecase.task.CalendarTaskRowProjection
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.inbox
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
    dayProjection: CalendarDayProjection,
    todayMillis: Long,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDayMillis: Long,
    onDaySelected: (Long) -> Unit,
    weekPagerState: PagerState,
    weekPagerCentre: Int,
    calendarDaysByDay: Map<Long, CalendarDayProjection>,
    categoryNames: Map<String, String>,
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
                calendarDaysByDay = calendarDaysByDay,
                onDaySelected = onDaySelected,
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = OpenTasksTheme.dimens.dividerThin,
        )

        // ── Tasks for the selected day ───
        when (displayMode) {
            ListDisplayMode.TIMELINE -> TimelineTaskList(
                dayRows = dayProjection.rows,
                navBarHeight = navBarHeight,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
            )

            ListDisplayMode.CARD -> CardTaskList(
                dayProjection = dayProjection,
                categoryNames = categoryNames,
                navBarHeight = navBarHeight,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
            )
        }
    }
}

@Composable
private fun TimelineTaskList(
    dayRows: List<CalendarTaskRowProjection>,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge),
    ) {
        items(dayRows, key = { it.task.id }) { row ->
            TimelineTaskRow(
                row = row,
                isFirst = dayRows.first() == row,
                isLast = dayRows.last() == row,
                onToggleComplete = { onToggleComplete(row.task) },
                onClick = { onTaskClick(row.task) },
            )
        }

        if (dayRows.isEmpty()) {
            item(key = "empty") {
                EmptyDayPlaceholder()
            }
        }
    }
}

@Composable
private fun CardTaskList(
    dayProjection: CalendarDayProjection,
    categoryNames: Map<String, String>,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Text(
        text = if (dayProjection.isToday) stringResource(Res.string.today).uppercase()
        else dayProjection.formattedDate,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = dimens.paddingXLarge,
            end = dimens.paddingXLarge,
            top = dimens.paddingXLarge,
            bottom = dimens.paddingMedium
        ),
    )

    val defaultCategoryName = stringResource(Res.string.inbox)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge),
    ) {
        items(dayProjection.rows, key = { it.task.id }) { row ->
            CardTaskRow(
                row = row,
                isToday = dayProjection.isToday,
                categoryName = categoryNames[row.task.categoryId] ?: defaultCategoryName,
                onToggleComplete = { onToggleComplete(row.task) },
                onClick = { onTaskClick(row.task) },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = dimens.dividerThin,
            )
        }

        if (dayProjection.rows.isEmpty()) {
            item(key = "empty") {
                EmptyDayPlaceholder()
            }
        }
    }
}

/** A single week page inside the week pager. Shows Sun–Sat with selectable days. */
@Composable
internal fun WeekStripPage(
    weekSundayMillis: Long,
    todayMillis: Long,
    selectedDayMillis: Long,
    calendarDaysByDay: Map<Long, CalendarDayProjection>,
    onDaySelected: (Long) -> Unit,
) {
    val sun = stringResource(Res.string.sun)
    val mon = stringResource(Res.string.mon)
    val tue = stringResource(Res.string.tue)
    val wed = stringResource(Res.string.wed)
    val thu = stringResource(Res.string.thu)
    val fri = stringResource(Res.string.fri)
    val sat = stringResource(Res.string.sat)
    val dayLabels = remember(sun, mon, tue, wed, thu, fri, sat) {
        listOf(sun, mon, tue, wed, thu, fri, sat)
    }

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
            val hasTasks = calendarDaysByDay.containsKey(dk)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDaySelected(dayMillis) },
            ) {
                Text(
                    text = dayLabels[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected || isToday) PrimaryBlue
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
                Box(
                    modifier = Modifier
                        .size(dimens.calendarDaySize)
                        .then(
                            when {
                                isSelected -> Modifier.background(PrimaryBlue, CircleShape)
                                isToday -> Modifier.background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
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
                            isSelected -> Color.White
                            isToday -> PrimaryBlue
                            else -> MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
                if (hasTasks) {
                    Spacer(Modifier.height(dimens.spacerTiny))
                    Box(
                        modifier = Modifier
                            .size(dimens.calendarDotSize)
                            .background(PrimaryBlue, CircleShape),
                    )
                } else {
                    Spacer(Modifier.height(dimens.spacerMedium))
                }
            }
        }
    }
}
