package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.extensions.currentMonth
import com.udnahc.opentasks.data.extensions.currentYear
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.custom
import opentasks.composeapp.generated.resources.daily
import opentasks.composeapp.generated.resources.end
import opentasks.composeapp.generated.resources.every_weekday
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_chevron_left
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.mon
import opentasks.composeapp.generated.resources.monthly_with_day
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.next_month
import opentasks.composeapp.generated.resources.ok
import opentasks.composeapp.generated.resources.previous_month
import opentasks.composeapp.generated.resources.reminder
import opentasks.composeapp.generated.resources.repeat
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.start
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.time
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.wed
import opentasks.composeapp.generated.resources.weekly_with_day
import opentasks.composeapp.generated.resources.yearly_with_date
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(stringResource(Res.string.ok), color = PrimaryBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}

@Composable
internal fun ReminderDialog(
    selected: Set<ReminderOption>,
    onConfirm: (Set<ReminderOption>) -> Unit,
    onDismiss: () -> Unit,
) {
    var localSelected by remember { mutableStateOf(selected) }
    val dimens = OpenTasksTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ReminderOption.entries.forEach { option ->
                    val isSelected = if (option == ReminderOption.NONE) {
                        localSelected.isEmpty()
                    } else {
                        option in localSelected
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                localSelected = if (option == ReminderOption.NONE) {
                                    emptySet()
                                } else {
                                    if (option in localSelected) {
                                        localSelected - option
                                    } else {
                                        localSelected + option
                                    }
                                }
                            }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isSelected) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(dimens.iconDefault),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localSelected) }) {
                Text(stringResource(Res.string.ok), color = PrimaryBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}

@Composable
internal fun RepeatDialog(
    selected: RecurrenceType,
    onSelected: (RecurrenceType) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.repeat), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                RecurrenceType.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = recurrenceLabel(option),
                            color = if (option == selected) PrimaryBlue else MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (option == selected) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(dimens.iconDefault),
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationDateDialog(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    onDaySelected: (day: Int, month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = PAGER_INITIAL_PAGE,
        pageCount = { PAGER_MONTH_RANGE * 2 },
    )
    val coroutineScope = rememberCoroutineScope()
    val (displayMonth, displayYear) = pageToMonthYear(pagerState.currentPage)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${monthName(displayMonth)} $displayYear",
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_chevron_left),
                            contentDescription = stringResource(Res.string.previous_month),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_chevron_right),
                            contentDescription = stringResource(Res.string.next_month),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        Res.string.sun,
                        Res.string.mon,
                        Res.string.tue,
                        Res.string.wed,
                        Res.string.thu,
                        Res.string.fri,
                        Res.string.sat
                    ).forEach { dayRes ->
                        Text(
                            text = stringResource(dayRes),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(OpenTasksTheme.dimens.spacerLarge))
                HorizontalPager(state = pagerState) { page ->
                    val (month, year) = pageToMonthYear(page)
                    CalendarGrid(
                        month = month,
                        year = year,
                        selectedDay = if (month == selectedMonth && year == selectedYear) selectedDay else 0,
                        todayDay = if (month == currentMonth() && year == currentYear()) currentDay() else 0,
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
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
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
            TextButton(onClick = {
                onConfirm(startState.hour, startState.minute, endState.hour, endState.minute)
            }) {
                Text(stringResource(Res.string.ok), color = PrimaryBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}

@Composable
internal fun DurationRepeatDialog(
    selected: RecurrenceType,
    selectedDay: Int,
    selectedMonth: Int,
    onSelected: (RecurrenceType) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
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
                                dayOfWeekIndex(currentYear(), selectedMonth, 1),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            color = if (option == selected) PrimaryBlue else MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (option == selected) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(dimens.iconDefault),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = dimens.dividerThin,
                    modifier = Modifier.padding(vertical = dimens.paddingSmall),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Custom not implemented yet */ }
                        .padding(vertical = dimens.listRowCompletedVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.custom),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}
