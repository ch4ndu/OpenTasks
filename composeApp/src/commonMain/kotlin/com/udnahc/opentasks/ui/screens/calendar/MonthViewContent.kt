package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.dayKeyFromDate
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.usecase.task.CalendarDayProjection
import com.udnahc.opentasks.domain.usecase.task.EMPTY_CALENDAR_DAY_PROJECTION
import com.udnahc.opentasks.domain.usecase.task.calendarTaskPrefix
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.priorityColor
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.calendar_overflow
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.today
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
//  MONTH VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun MonthViewContent(
    selectedDayProjection: CalendarDayProjection,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDay: CalendarDay?,
    collapseProgress: Animatable<Float, *>,
    pagerState: PagerState,
    centreIndex: Int,
    calendarDaysByDay: Map<Long, CalendarDayProjection>,
    categoryNames: Map<String, String>,
    topBarHeight: Dp,
    navBarHeight: Dp,
    onDayClick: (CalendarDay) -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    // We need to know available height so we can interpolate grid height
    // from "fill all space" → "single collapsed week row"
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val dimens = OpenTasksTheme.dimens
        val totalHeight = maxHeight
        val dayHeadersHeight = dimens.calendarDayHeaderHeight
        val collapsedWeekHeight = dimens.calendarCollapsedWeekHeight
        val stackedEventsHeight = dimens.calendarStackedEventsHeight
        val gridAvailable =
            totalHeight - topBarHeight - dayHeadersHeight - navBarHeight - dimens.fabAreaBottom

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topBarHeight))
            DayNameHeaders()

            CollapsibleMonthPager(
                collapseProgress = collapseProgress,
                pagerState = pagerState,
                centreIndex = centreIndex,
                todayYear = todayYear,
                todayMonth = todayMonth,
                todayDay = todayDay,
                selectedDay = selectedDay,
                gridAvailable = gridAvailable,
                collapsedWeekHeight = collapsedWeekHeight,
                calendarDaysByDay = calendarDaysByDay,
                onDayClick = onDayClick,
            )

            StackedMonthEvents(
                collapseProgress = collapseProgress,
                pagerState = pagerState,
                centreIndex = centreIndex,
                todayYear = todayYear,
                todayMonth = todayMonth,
                selectedDay = selectedDay,
                stackedEventsHeight = stackedEventsHeight,
                calendarDaysByDay = calendarDaysByDay,
                onDayClick = onDayClick,
            )

            MonthSelectedTaskList(
                collapseProgress = collapseProgress,
                selectedDayProjection = selectedDayProjection,
                selectedDay = selectedDay,
                todayYear = todayYear,
                todayMonth = todayMonth,
                todayDay = todayDay,
                categoryNames = categoryNames,
                navBarHeight = navBarHeight,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CollapsibleMonthPager(
    collapseProgress: Animatable<Float, *>,
    pagerState: PagerState,
    centreIndex: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDay: CalendarDay?,
    gridAvailable: Dp,
    collapsedWeekHeight: Dp,
    calendarDaysByDay: Map<Long, CalendarDayProjection>,
    onDayClick: (CalendarDay) -> Unit,
) {
    val progress = collapseProgress.value
    val gridHeight = gridAvailable - (gridAvailable - collapsedWeekHeight) * progress

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        userScrollEnabled = progress == 0f,
    ) { page ->
        val offset = page - centreIndex
        var y = todayYear
        var m = todayMonth + offset
        while (m > 12) {
            m -= 12; y++
        }
        while (m < 1) {
            m += 12; y--
        }

        val weeks = remember(y, m) { buildMonthWeeks(y, m) }
        val pageCalendarDaysByDay = remember(weeks, calendarDaysByDay) {
            weeks.flatten().associate { day ->
                dayKeyFromDate(day.year, day.month, day.day) to calendarDaysByDay[
                    dayKeyFromDate(day.year, day.month, day.day)
                ]
            }
        }
        val isCurrentPage = page == pagerState.currentPage
        val pageSelectedDay = if (isCurrentPage) selectedDay else null
        val pageProgress = if (isCurrentPage) progress else 0f

        AnimatedMonthGrid(
            weeks = weeks,
            todayYear = todayYear,
            todayMonth = todayMonth,
            todayDay = todayDay,
            selectedDay = pageSelectedDay,
            collapseProgress = pageProgress,
            calendarDaysByDay = pageCalendarDaysByDay,
            onDayClick = onDayClick,
        )
    }
}

@Composable
private fun StackedMonthEvents(
    collapseProgress: Animatable<Float, *>,
    pagerState: PagerState,
    centreIndex: Int,
    todayYear: Int,
    todayMonth: Int,
    selectedDay: CalendarDay?,
    stackedEventsHeight: Dp,
    calendarDaysByDay: Map<Long, CalendarDayProjection>,
    onDayClick: (CalendarDay) -> Unit,
) {
    val progress = collapseProgress.value
    if (progress <= 0f || selectedDay == null) return

    val dimens = OpenTasksTheme.dimens
    val currentPageOffset = pagerState.currentPage - centreIndex
    var pageYear = todayYear
    var pageMonth = todayMonth + currentPageOffset
    while (pageMonth > 12) {
        pageMonth -= 12; pageYear++
    }
    while (pageMonth < 1) {
        pageMonth += 12; pageYear--
    }
    val currentWeeks = remember(pageYear, pageMonth) { buildMonthWeeks(pageYear, pageMonth) }
    val selectedWeek = currentWeeks.firstOrNull { week -> week.any { it == selectedDay } }
    val selectedWeekProjections = remember(selectedWeek, calendarDaysByDay) {
        selectedWeek.orEmpty().map { day ->
            day to calendarDaysByDay[dayKeyFromDate(day.year, day.month, day.day)]
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(stackedEventsHeight * progress)
            .alpha(progress)
            .graphicsLayer { clip = true },
    ) {
        if (selectedWeek != null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.paddingSmall),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
            selectedWeekProjections.forEach { (day, dayProjection) ->
                val isSelected = day == selectedDay

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        PrimaryBlue.copy(alpha = 0.25f),
                                        RoundedCornerShape(
                                            topStart = 0.dp,
                                            topEnd = 0.dp,
                                            bottomStart = dimens.cornerLarge,
                                            bottomEnd = dimens.cornerLarge,
                                        ),
                                    )
                                } else Modifier
                            )
                            .clickable(enabled = day.isCurrentMonth) { onDayClick(day) }
                            .padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val monthPreview = dayProjection?.monthPreview
                            ?: EMPTY_CALENDAR_DAY_PROJECTION.monthPreview
                        monthPreview.rows.forEach { row ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dimens.calendarMonthGridEventHeight)
                                    .padding(vertical = 1.dp)
                                    .clip(RoundedCornerShape(dimens.cornerTiny))
                                    .background(priorityColor(row.task.priority).copy(alpha = 0.2f))
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = row.task.title,
                                    style = OpenTasksTheme.typography.calendarEventTitle,
                                    color = priorityColor(row.task.priority),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (monthPreview.overflowCount > 0) {
                            Text(
                                text = stringResource(Res.string.calendar_overflow, monthPreview.overflowCount),
                                style = OpenTasksTheme.typography.calendarEventOverflow,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = dimens.dividerThin,
        modifier = Modifier.alpha(progress),
    )
}

@Composable
private fun MonthSelectedTaskList(
    collapseProgress: Animatable<Float, *>,
    selectedDayProjection: CalendarDayProjection,
    selectedDay: CalendarDay?,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    categoryNames: Map<String, String>,
    navBarHeight: Dp,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = collapseProgress.value
    if (progress > 0f && selectedDay != null) {
        val dimens = OpenTasksTheme.dimens
        val defaultCategoryName = stringResource(Res.string.inbox)

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .alpha(progress),
            contentPadding = PaddingValues(
                bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge
            ),
        ) {
            item(key = "date_header") {
                Text(
                    text = if (selectedDayProjection.isToday) stringResource(Res.string.today).uppercase()
                    else selectedDayProjection.monthDateText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = dimens.paddingXLarge,
                        vertical = dimens.paddingLarge
                    ),
                )
            }

            items(selectedDayProjection.rows, key = { it.task.id }) { row ->
                CalendarTaskRow(
                    row = row,
                    categoryName = categoryNames[row.task.categoryId] ?: defaultCategoryName,
                    onToggleComplete = { onToggleComplete(row.task) },
                    onClick = { onTaskClick(row.task) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = dimens.dividerThin,
                )
            }

            if (selectedDayProjection.rows.isEmpty()) {
                item(key = "empty") {
                    EmptyDayPlaceholder()
                }
            }
        }
    } else {
        Spacer(modifier)
    }
}

// DayHeaders extracted to CalendarComposables.kt as DayNameHeaders()

// ── Animated month grid ─────────────────────────────────────────────────────

@Composable
private fun AnimatedMonthGrid(
    weeks: List<List<CalendarDay>>,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDay: CalendarDay?,
    collapseProgress: Float,
    calendarDaysByDay: Map<Long, CalendarDayProjection?>,
    onDayClick: (CalendarDay) -> Unit,
) {
    val selectedWeekIndex = if (selectedDay != null) {
        weeks.indexOfFirst { week -> week.any { it == selectedDay } }
    } else -1

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val expandedRowHeightPx = totalHeightPx / weeks.size
        val collapsedRowHeightPx =
            with(LocalDensity.current) { OpenTasksTheme.dimens.calendarCollapsedWeekHeight.toPx() }

        var cumulativeOffset = 0f

        weeks.forEachIndexed { weekIndex, week ->
            val rowHeight: Float
            val rowAlpha: Float

            if (selectedWeekIndex < 0) {
                rowHeight = expandedRowHeightPx
                rowAlpha = 1f
            } else if (weekIndex == selectedWeekIndex) {
                rowHeight = expandedRowHeightPx +
                        (collapsedRowHeightPx - expandedRowHeightPx) * collapseProgress
                rowAlpha = 1f
            } else {
                rowHeight = expandedRowHeightPx * (1f - collapseProgress)
                rowAlpha = 1f - collapseProgress
            }

            val rowHeightDp = with(LocalDensity.current) { rowHeight.toDp() }
            val yOffset = cumulativeOffset

            if (rowHeight > 0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeightDp)
                        .offset { IntOffset(0, yOffset.roundToInt()) }
                        .alpha(rowAlpha),
                ) {
                    WeekRowContent(
                        weekProjections = remember(week, calendarDaysByDay) {
                            week.map { day ->
                                day to calendarDaysByDay[dayKeyFromDate(day.year, day.month, day.day)]
                            }
                        },
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        selectedDay = selectedDay,
                        collapseProgress = collapseProgress,
                        isSelectedWeek = weekIndex == selectedWeekIndex,
                        onDayClick = onDayClick,
                    )
                }
            }

            cumulativeOffset += rowHeight
        }
    }
}

@Composable
private fun WeekRowContent(
    weekProjections: List<Pair<CalendarDay, CalendarDayProjection?>>,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDay: CalendarDay?,
    collapseProgress: Float,
    isSelectedWeek: Boolean,
    onDayClick: (CalendarDay) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.paddingSmall),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        weekProjections.forEach { (day, dayProjection) ->
            val isToday = day.year == todayYear && day.month == todayMonth && day.day == todayDay
            val isSelected = day == selectedDay

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (isSelected && collapseProgress > 0f) {
                            Modifier.background(
                                PrimaryBlue.copy(alpha = 0.25f * collapseProgress),
                                RoundedCornerShape(
                                    topStart = dimens.cornerLarge,
                                    topEnd = dimens.cornerLarge,
                                    bottomStart = 0.dp,
                                    bottomEnd = 0.dp,
                                ),
                            )
                        } else Modifier
                    )
                    .clickable(enabled = day.isCurrentMonth) { onDayClick(day) }
                    .padding(horizontal = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(dimens.spacerTiny))

                // Day number
                val textColor = when {
                    isSelected -> Color.White
                    isToday -> PrimaryBlue
                    day.isCurrentMonth -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }

                Box(
                    modifier = Modifier
                        .size(dimens.calendarWeekDayCircle)
                        .then(
                            when {
                                isSelected && collapseProgress > 0f -> Modifier.background(
                                    PrimaryBlue, CircleShape
                                )

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
                        text = day.day.toString(),
                        style = OpenTasksTheme.typography.calendarDayNumber,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                    )
                }

                Spacer(Modifier.height(dimens.spacerTiny))

                // Event bars — shown in expanded state, fade out when collapsing
                val eventBarAlpha = if (isSelectedWeek) 1f - collapseProgress else 1f
                if (eventBarAlpha > 0.01f && dayProjection?.rows?.isNotEmpty() == true) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        val eventBarHeight = dimens.calendarEventBarHeight
                        val overflowHeight = dimens.calendarEventOverflowHeight
                        val availableHeight = maxHeight
                        val maxVisible =
                            ((availableHeight - overflowHeight) / eventBarHeight).toInt()
                                .coerceAtLeast(1)
                        val prefix = remember(dayProjection, maxVisible) {
                            calendarTaskPrefix(dayProjection.rows, maxVisible)
                        }

                        Column {
                            prefix.rows.forEach { row ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(eventBarHeight)
                                        .padding(vertical = 1.dp)
                                        .alpha(eventBarAlpha)
                                        .clip(RoundedCornerShape(dimens.cornerTiny))
                                        .background(priorityColor(row.task.priority).copy(alpha = 0.2f))
                                        .padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = row.task.title,
                                        style = OpenTasksTheme.typography.calendarEventTitle,
                                        color = priorityColor(row.task.priority),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (prefix.overflowCount > 0) {
                                Text(
                                    text = stringResource(Res.string.calendar_overflow, prefix.overflowCount),
                                    style = OpenTasksTheme.typography.calendarEventOverflow,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .alpha(eventBarAlpha)
                                        .padding(start = dimens.paddingTiny),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
