package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.daily
import opentasks.composeapp.generated.resources.end
import opentasks.composeapp.generated.resources.every_weekday
import opentasks.composeapp.generated.resources.monthly_with_day
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.reminder
import opentasks.composeapp.generated.resources.repeat
import opentasks.composeapp.generated.resources.start
import opentasks.composeapp.generated.resources.time
import opentasks.composeapp.generated.resources.weekly_with_day
import opentasks.composeapp.generated.resources.yearly_with_date
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.time), fontWeight = FontWeight.Bold) },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            DialogOkTextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            })
        },
        dismissButton = {
            DialogCancelTextButton(onClick = onDismiss)
        },
    )
}

@Composable
internal fun <T> MultiSelectReminderDialog(
    selected: Set<T>,
    options: List<T>,
    noneOption: T,
    optionLabel: @Composable (T) -> String,
    onConfirm: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var localSelected by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = if (option == noneOption) {
                        localSelected.isEmpty()
                    } else {
                        option in localSelected
                    }
                    SelectedOptionRow(
                        label = optionLabel(option),
                        isSelected = isSelected,
                        onClick = {
                            localSelected = if (option == noneOption) {
                                emptySet()
                            } else {
                                if (option in localSelected) {
                                    localSelected - option
                                } else {
                                    localSelected + option
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            DialogOkTextButton(onClick = {
                onConfirm(localSelected)
            })
        },
        dismissButton = {
            DialogCancelTextButton(onClick = onDismiss)
        },
    )
}

@Composable
internal fun ReminderDialog(
    selected: Set<ReminderOption>,
    onConfirm: (Set<ReminderOption>) -> Unit,
    onDismiss: () -> Unit,
) {
    MultiSelectReminderDialog(
        selected = selected,
        options = ReminderOption.entries,
        noneOption = ReminderOption.NONE,
        optionLabel = { option -> stringResource(option.labelRes) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun RepeatDialog(
    selected: RecurrenceType,
    onSelected: (RecurrenceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.repeat), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                RecurrenceType.entries.forEach { option ->
                    SelectedOptionRow(
                        label = recurrenceLabel(option),
                        isSelected = option == selected,
                        onClick = { onSelected(option) },
                    )
                }
            }
        },
        dismissButton = {
            DialogCancelTextButton(onClick = onDismiss)
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarDatePickerDialog(
    currentDate: LocalDate,
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    onDaySelected: (day: Int, month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit,
    initialPage: Int = PAGER_INITIAL_PAGE,
    pageCount: Int = PAGER_MONTH_RANGE * 2,
    useLargeCells: Boolean = false,
    pageToMonthYear: (Int) -> Pair<Int, Int> = { page -> pageToMonthYear(page, currentDate) },
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount },
    )
    val coroutineScope = rememberCoroutineScope()
    val (displayMonth, displayYear) = pageToMonthYear(pagerState.currentPage)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            MonthPagerHeader(
                title = "${monthName(displayMonth)} $displayYear",
                onPreviousMonth = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNextMonth = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
            )
        },
        text = {
            Column {
                WeekdayHeader()
                Spacer(Modifier.height(OpenTasksTheme.dimens.spacerLarge))
                HorizontalPager(state = pagerState) { page ->
                    val (month, year) = pageToMonthYear(page)
                    SelectableDayGrid(
                        month = month,
                        year = year,
                        selectedDay = if (month == selectedMonth && year == selectedYear) selectedDay else 0,
                        todayDay = if (month == currentDate.monthNumber && year == currentDate.year) currentDate.dayOfMonth else 0,
                        useLargeCells = useLargeCells,
                        onDayClick = { day ->
                            onDaySelected(day, month, year)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogCancelTextButton(onClick = onDismiss)
        },
    )
}

@Composable
internal fun DurationDateDialog(
    currentDate: LocalDate,
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    onDaySelected: (day: Int, month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    CalendarDatePickerDialog(
        selectedDay = selectedDay,
        selectedMonth = selectedMonth,
        selectedYear = selectedYear,
        currentDate = currentDate,
        onDaySelected = onDaySelected,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationTimeRangeDialog(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onConfirm: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Start, 1 = End
    val startState = rememberTimePickerState(
        initialHour = startHour,
        initialMinute = startMinute,
        is24Hour = false,
    )
    val endState = rememberTimePickerState(
        initialHour = endHour,
        initialMinute = endMinute,
        is24Hour = false,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row {
                TextButton(onClick = { selectedTab = 0 }) {
                    Text(
                        stringResource(Res.string.start),
                        color = if (selectedTab == 0) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.width(OpenTasksTheme.dimens.spacerXXLarge))
                TextButton(onClick = { selectedTab = 1 }) {
                    Text(
                        stringResource(Res.string.end),
                        color = if (selectedTab == 1) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        },
        text = {
            when (selectedTab) {
                0 -> TimePicker(state = startState)
                1 -> TimePicker(state = endState)
            }
        },
        confirmButton = {
            DialogOkTextButton(onClick = {
                onConfirm(startState.hour, startState.minute, endState.hour, endState.minute)
            })
        },
        dismissButton = {
            DialogCancelTextButton(onClick = onDismiss)
        },
    )
}

@Composable
internal fun DurationRepeatDialog(
    selected: RecurrenceType,
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    onSelected: (RecurrenceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.repeat), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                RecurrenceType.entries.forEach { option ->
                    val label = when (option) {
                        RecurrenceType.NONE -> stringResource(Res.string.none)
                        RecurrenceType.DAILY -> stringResource(Res.string.daily)
                        RecurrenceType.WEEKLY -> {
                            val dow = dayOfWeekName(
                                dayOfWeekIndex(selectedYear, selectedMonth, 1),
                                selectedDay
                            )
                            stringResource(Res.string.weekly_with_day, dow)
                        }

                        RecurrenceType.MONTHLY -> stringResource(
                            Res.string.monthly_with_day,
                            selectedDay
                        )

                        RecurrenceType.YEARLY -> stringResource(
                            Res.string.yearly_with_date,
                            monthNameShort(selectedMonth),
                            selectedDay
                        )

                        RecurrenceType.EVERY_WEEKDAY -> stringResource(Res.string.every_weekday)
                    }
                    SelectedOptionRow(
                        label = label,
                        isSelected = option == selected,
                        onClick = { onSelected(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogCancelTextButton(onClick = onDismiss)
        },
    )
}
