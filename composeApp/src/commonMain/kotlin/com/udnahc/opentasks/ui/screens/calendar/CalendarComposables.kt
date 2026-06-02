package com.udnahc.opentasks.ui.screens.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.dayKeyFromDate
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue

// ── Day name headers (S M T W T F S) ────────────────────────────────────────

/**
 * Renders the "S M T W T F S" day-of-week header row.
 *
 * @param compact When false (default), uses [OpenTasksTheme.typography.calendarDayNumber] with
 *   medium weight inside a centered Box — matching the full MonthView header. When true, uses
 *   [OpenTasksTheme.typography.calendarEventOverflow] for mini-calendar contexts.
 */
@Composable
internal fun DayNameHeaders(compact: Boolean = false) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!compact) Modifier.padding(horizontal = dimens.paddingSmall) else Modifier),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(7) { dayIndex ->
            if (compact) {
                Text(
                    text = calendarWeekdayNarrow(dayIndex),
                    style = OpenTasksTheme.typography.calendarEventOverflow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = calendarWeekdayNarrow(dayIndex),
                        style = OpenTasksTheme.typography.calendarDayNumber,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (!compact) {
        Spacer(Modifier.height(dimens.spacerSmall))
    }
}

// ── Shared day circle (uniform today/selected/normal styling) ─────────────────

/**
 * Renders a day number inside a circle with uniform styling across all calendar views.
 *
 * - **Today**: `surfaceVariant` circle, `PrimaryBlue` text, bold
 * - **Selected**: `PrimaryBlue` circle, white text, bold
 * - **Normal (current month)**: no circle, `onBackground` text, normal weight
 * - **Other month**: no circle, `onSurfaceVariant` at 40% alpha, normal weight
 */
@Composable
internal fun CalendarDayCircle(
    text: String,
    isToday: Boolean,
    isSelected: Boolean,
    isCurrentMonth: Boolean = true,
    size: Dp,
) {
    val textColor = when {
        isSelected -> Color.White
        isToday -> PrimaryBlue
        isCurrentMonth -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier
            .size(size)
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
            text = text,
            style = OpenTasksTheme.typography.calendarDayNumber,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
    }
}

// ── Mini calendar grid ───────────────────────────────────────────────────────

/**
 * Shared mini-calendar month grid used by both WeekView and YearView.
 *
 * @param year Calendar year to display.
 * @param month Calendar month (1-12) to display.
 * @param todayYear Current "today" year.
 * @param todayMonth Current "today" month.
 * @param todayDay Current "today" day-of-month.
 * @param selectedDayMillis Optional local-millis of the selected day (unused by year view).
 * @param highlightedWeekSundayMillis Optional Sunday millis for animated week-band highlight
 *   (week view only).
 * @param tasksByDay Map of dayKey -> tasks for dot/color indicators.
 * @param onDayClick Callback when a day cell is tapped. Receives local start-of-day millis.
 *   Pass null to disable per-day click (year view uses card-level click instead).
 * @param showMonthHeader When true, renders the month name above the grid (year view).
 * @param useAspectRatioCells When true, day cells use aspectRatio(1f) instead of fillMaxHeight.
 *   Year view uses this for uniform square cells in a non-constrained column.
 */
@Composable
internal fun MiniCalendarGrid(
    year: Int,
    month: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    selectedDayMillis: Long? = null,
    highlightedWeekSundayMillis: Long? = null,
    tasksByDay: Map<Long, List<Task>>,
    onDayClick: ((dayMillis: Long) -> Unit)? = null,
    showMonthHeader: Boolean = false,
    useAspectRatioCells: Boolean = false,
) {
    val dimens = OpenTasksTheme.dimens
    val weeks = remember(year, month) { buildMonthWeeks(year, month) }

    // Month name header (year view)
    if (showMonthHeader) {
        val isCurrentMonth = year == todayYear && month == todayMonth
        Text(
            text = calendarMonthNameShort(month),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrentMonth) PrimaryBlue
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = dimens.paddingSmall),
        )
    }

    // Day name headers
    DayNameHeaders(compact = true)
    Spacer(Modifier.height(dimens.spacerTiny))

    if (useAspectRatioCells) {
        // Year view: simple column of rows with aspectRatio cells
        MiniCalendarAspectRatioGrid(
            weeks = weeks,
            todayYear = todayYear,
            todayMonth = todayMonth,
            todayDay = todayDay,
            tasksByDay = tasksByDay,
        )
    } else {
        // Week view: fills available space, supports animated week highlight
        MiniCalendarFillGrid(
            weeks = weeks,
            todayYear = todayYear,
            todayMonth = todayMonth,
            todayDay = todayDay,
            highlightedWeekSundayMillis = highlightedWeekSundayMillis,
            tasksByDay = tasksByDay,
            onDayClick = onDayClick,
        )
    }
}

// ── Aspect-ratio grid (YearView) ────────────────────────────────────────────

@Composable
private fun MiniCalendarAspectRatioGrid(
    weeks: List<List<CalendarDay>>,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    tasksByDay: Map<Long, List<Task>>,
) {
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
                            if (isToday) Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (day.isCurrentMonth) day.day.toString() else "",
                        style = OpenTasksTheme.typography.calendarEventOverflow,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isToday -> PrimaryBlue
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

// ── Fill grid with optional animated highlight (WeekView) ───────────────────

@Composable
private fun MiniCalendarFillGrid(
    weeks: List<List<CalendarDay>>,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    highlightedWeekSundayMillis: Long?,
    tasksByDay: Map<Long, List<Task>>,
    onDayClick: ((Long) -> Unit)?,
) {
    val dimens = OpenTasksTheme.dimens

    // Find which week row contains the highlighted week
    val highlightedWeekRowIndex = remember(weeks, highlightedWeekSundayMillis) {
        if (highlightedWeekSundayMillis == null) -1
        else weeks.indexOfFirst { week ->
            val weekSunMillis = startOfDayLocalMillis(week[0].year, week[0].month, week[0].day)
            weekSunMillis == highlightedWeekSundayMillis
        }
    }

    // Animate the highlight band position
    val animatedRowIndex =
        remember { Animatable(highlightedWeekRowIndex.coerceAtLeast(0).toFloat()) }
    LaunchedEffect(highlightedWeekRowIndex) {
        if (highlightedWeekRowIndex >= 0) {
            animatedRowIndex.animateTo(
                highlightedWeekRowIndex.toFloat(),
                animationSpec = tween(300),
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val rowCount = weeks.size
        if (rowCount == 0) return@BoxWithConstraints
        val rowHeightDp = maxHeight / rowCount
        val rowHeightPx = with(LocalDensity.current) { rowHeightDp.toPx() }

        // Animated highlight band
        if (highlightedWeekRowIndex >= 0) {
            val bandOffsetPx = animatedRowIndex.value * rowHeightPx
            val bandOffsetDp = with(LocalDensity.current) { bandOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeightDp)
                    .padding(horizontal = 1.dp)
                    .offset(y = bandOffsetDp)
                    .background(
                        PrimaryBlue.copy(alpha = 0.12f),
                        RoundedCornerShape(dimens.cornerSmall),
                    ),
            )
        }

        // Day cells
        Column(modifier = Modifier.fillMaxSize()) {
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(rowHeightDp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    week.forEach { day ->
                        val isToday =
                            day.year == todayYear && day.month == todayMonth && day.day == todayDay
                        val dk = dayKeyFromDate(day.year, day.month, day.day)
                        val hasTasks = tasksByDay.containsKey(dk)
                        val dayMillis = startOfDayLocalMillis(day.year, day.month, day.day)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (onDayClick != null) Modifier.clickable {
                                        onDayClick(
                                            dayMillis
                                        )
                                    }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dimens.miniCalTodayCircle)
                                    .then(
                                        if (isToday) Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (day.isCurrentMonth) day.day.toString() else "",
                                    style = OpenTasksTheme.typography.calendarEventOverflow,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isToday -> PrimaryBlue
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
}
