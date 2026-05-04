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
import androidx.compose.foundation.layout.width
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
import com.udnahc.opentasks.domain.usecase.task.truncateWithOverflow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.priorityColor
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.no_tasks
import opentasks.composeapp.generated.resources.today
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
//  MONTH VIEW
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun MonthViewContent(
    selectedTasks: List<Task>,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDay: CalendarDay?,
    collapseProgress: Animatable<Float, *>,
    pagerState: PagerState,
    centreIndex: Int,
    tasksByDay: Map<Long, List<Task>>,
    categoryNames: Map<String, String>,
    topBarHeight: Dp,
    navBarHeight: Dp,
    onDayClick: (CalendarDay) -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val progress = collapseProgress.value

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
        val gridAvailable = totalHeight - topBarHeight - dayHeadersHeight - navBarHeight - dimens.fabAreaBottom

        // Interpolate grid height: full available → collapsed week height
        val gridHeight = gridAvailable - (gridAvailable - collapsedWeekHeight) * progress

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topBarHeight))
            DayNameHeaders()

            // ── Animated month pager ───
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
                    tasksByDay = tasksByDay,
                    onDayClick = onDayClick,
                )
            }

            // ── Stacked events area (entire week's events under each day column) ───
            if (progress > 0f && selectedDay != null) {
                // Find the week containing the selected day
                val currentPageOffset = pagerState.currentPage - centreIndex
                var pageYear = todayYear
                var pageMonth = todayMonth + currentPageOffset
                while (pageMonth > 12) {
                    pageMonth -= 12; pageYear++
                }
                while (pageMonth < 1) {
                    pageMonth += 12; pageYear--
                }
                val currentWeeks =
                    remember(pageYear, pageMonth) { buildMonthWeeks(pageYear, pageMonth) }
                val selectedWeek =
                    currentWeeks.firstOrNull { week -> week.any { it == selectedDay } }

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
                            selectedWeek.forEach { day ->
                                val isSelected = day == selectedDay
                                val dk = dayKeyFromDate(day.year, day.month, day.day)
                                val dayEvents =
                                    tasksByDay[dk] ?: emptyList()

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
                                    val (visibleEvents, overflow) = truncateWithOverflow(dayEvents, 5)
                                    visibleEvents.forEach { task ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(dimens.calendarMonthGridEventHeight)
                                                .padding(vertical = 1.dp)
                                                .clip(RoundedCornerShape(dimens.cornerTiny))
                                                .background(
                                                    priorityColor(task.priority).copy(
                                                        alpha = 0.2f
                                                    )
                                                )
                                                .padding(horizontal = 2.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                text = task.title,
                                                style = OpenTasksTheme.typography.calendarEventTitle,
                                                color = priorityColor(task.priority),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (overflow > 0) {
                                        Text(
                                            text = "+$overflow",
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

            // ── Task list (fades in as collapse progresses) ───
            if (progress > 0f && selectedDay != null) {
                val defaultCategoryName = stringResource(Res.string.inbox)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .alpha(progress),
                    contentPadding = PaddingValues(bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge),
                ) {
                    item(key = "date_header") {
                        val isToday = selectedDay.year == todayYear &&
                                selectedDay.month == todayMonth &&
                                selectedDay.day == todayDay
                        Text(
                            text = if (isToday) stringResource(Res.string.today).uppercase()
                            else "${calendarMonthNameShort(selectedDay.month).uppercase()} ${selectedDay.day}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
                        )
                    }

                    items(selectedTasks, key = { it.id }) { task ->
                        CalendarTaskRow(
                            task = task,
                            categoryName = categoryNames[task.categoryId] ?: defaultCategoryName,
                            onToggleComplete = { onToggleComplete(task) },
                            onClick = { onTaskClick(task) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = dimens.dividerThin,
                        )
                    }

                    if (selectedTasks.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = dimens.calendarEmptyPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.no_tasks),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                // Bottom padding when fully expanded
                Spacer(Modifier.weight(1f))
            }
        }
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
    tasksByDay: Map<Long, List<Task>>,
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
                        week = week,
                        todayYear = todayYear,
                        todayMonth = todayMonth,
                        todayDay = todayDay,
                        selectedDay = selectedDay,
                        collapseProgress = collapseProgress,
                        isSelectedWeek = weekIndex == selectedWeekIndex,
                        tasksByDay = tasksByDay,
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
    week: List<CalendarDay>,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDay: CalendarDay?,
    collapseProgress: Float,
    isSelectedWeek: Boolean,
    tasksByDay: Map<Long, List<Task>>,
    onDayClick: (CalendarDay) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.paddingSmall),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        week.forEach { day ->
            val isToday = day.year == todayYear && day.month == todayMonth && day.day == todayDay
            val isSelected = day == selectedDay
            val dk = dayKeyFromDate(day.year, day.month, day.day)
            val dayTasks = tasksByDay[dk] ?: emptyList()

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
                if (eventBarAlpha > 0.01f && dayTasks.isNotEmpty()) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        val eventBarHeight = dimens.calendarEventBarHeight
                        val overflowHeight = dimens.calendarEventOverflowHeight
                        val availableHeight = maxHeight
                        val maxVisible =
                            ((availableHeight - overflowHeight) / eventBarHeight).toInt()
                                .coerceAtLeast(1)
                        val (visibleTasks, overflow) = truncateWithOverflow(dayTasks, maxVisible)

                        Column {
                            visibleTasks.forEach { task ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(eventBarHeight)
                                        .padding(vertical = 1.dp)
                                        .alpha(eventBarAlpha)
                                        .clip(RoundedCornerShape(dimens.cornerTiny))
                                        .background(priorityColor(task.priority).copy(alpha = 0.2f))
                                        .padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = task.title,
                                        style = OpenTasksTheme.typography.calendarEventTitle,
                                        color = priorityColor(task.priority),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (overflow > 0) {
                                Text(
                                    text = "+$overflow",
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
