package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.extensions.currentMonth
import com.udnahc.opentasks.data.extensions.currentYear
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.daysInMonth
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.all_day
import opentasks.composeapp.generated.resources.clear_reminder
import opentasks.composeapp.generated.resources.close
import opentasks.composeapp.generated.resources.confirm
import opentasks.composeapp.generated.resources.date
import opentasks.composeapp.generated.resources.duration
import opentasks.composeapp.generated.resources.duration_hours
import opentasks.composeapp.generated.resources.duration_hours_minutes
import opentasks.composeapp.generated.resources.duration_hours_plural
import opentasks.composeapp.generated.resources.duration_minutes
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.ic_alarm
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_chevron_left
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.ic_repeat
import opentasks.composeapp.generated.resources.ic_schedule
import opentasks.composeapp.generated.resources.mon
import opentasks.composeapp.generated.resources.next_month
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.previous_month
import opentasks.composeapp.generated.resources.reminder
import opentasks.composeapp.generated.resources.repeat
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.time
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.wed
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateReminderBottomSheet(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    selectedHour: Int,
    selectedMinute: Int,
    selectedReminders: Set<ReminderOption>,
    selectedRecurrence: RecurrenceType,
    initialDurationReminders: String = "",
    initialEndHour: Int = -1,
    initialEndMinute: Int = 0,
    initialIsAllDay: Boolean = false,
    initialTab: Int = 0,
    onDaySelected: (day: Int, month: Int, year: Int) -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onEndTimeSelected: (hour: Int, minute: Int) -> Unit = { _, _ -> },
    onAllDayChanged: (Boolean) -> Unit = {},
    onRemindersSelected: (Set<ReminderOption>) -> Unit,
    onRecurrenceSelected: (RecurrenceType) -> Unit,
    onDurationRemindersChanged: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }

    // Per-tab reminder state (prevents cross-tab contamination)
    var dateTabReminders by remember {
        mutableStateOf(if (initialTab == 0) selectedReminders else setOf(ReminderOption.ON_TIME))
    }
    var durationTabReminders by remember {
        mutableStateOf(if (initialTab == 1) selectedReminders else setOf(ReminderOption.ON_TIME))
    }
    val activeReminders = if (selectedTab == 0) dateTabReminders else durationTabReminders

    // Duration tab state
    var durDay by remember { mutableIntStateOf(if (initialTab == 1 && selectedDay > 0) selectedDay else currentDay()) }
    var durMonth by remember { mutableIntStateOf(if (initialTab == 1 && selectedMonth > 0) selectedMonth else currentMonth()) }
    var durYear by remember { mutableIntStateOf(if (initialTab == 1 && selectedYear > 0) selectedYear else currentYear()) }
    var durStartHour by remember { mutableIntStateOf(if (initialTab == 1 && selectedHour >= 0) selectedHour else 16) }
    var durStartMinute by remember { mutableIntStateOf(if (initialTab == 1) selectedMinute else 0) }
    var durEndHour by remember { mutableIntStateOf(if (initialTab == 1 && initialEndHour >= 0) initialEndHour else 17) }
    var durEndMinute by remember { mutableIntStateOf(if (initialTab == 1) initialEndMinute else 0) }
    var durAllDay by remember { mutableStateOf(if (initialTab == 1) initialIsAllDay else false) }
    var durRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
    var showDurDateDialog by remember { mutableStateOf(false) }
    var showDurTimeDialog by remember { mutableStateOf(false) }
    var showDurRepeatDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val dimens = OpenTasksTheme.dimens
            // Header: X, Date/Duration tabs, checkmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.paddingMedium, vertical = dimens.paddingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.close),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentColor = PrimaryBlue,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = PrimaryBlue,
                            )
                        }
                    },
                    divider = {},
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                stringResource(Res.string.date),
                                color = if (selectedTab == 0) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                stringResource(Res.string.duration),
                                color = if (selectedTab == 1) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                IconButton(onClick = {
                    if (selectedTab == 1) {
                        // Duration tab -- propagate duration date back to parent
                        onDaySelected(durDay, durMonth, durYear)
                        if (durAllDay) {
                            // All-day tasks should have no time component (midnight)
                            onTimeSelected(0, 0)
                        } else {
                            // Propagate the start time from the duration tab
                            onTimeSelected(durStartHour, durStartMinute)
                        }
                        onEndTimeSelected(durEndHour, durEndMinute)
                        onAllDayChanged(durAllDay)
                        onRemindersSelected(durationTabReminders)
                        onDurationRemindersChanged(durationTabReminders.toRemindersString())
                    } else {
                        onRemindersSelected(dateTabReminders)
                        onDurationRemindersChanged("")
                    }
                    onConfirm()
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = stringResource(Res.string.confirm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (selectedTab) {
                0 -> DateTabContent(
                    selectedDay = selectedDay,
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    selectedReminders = activeReminders,
                    selectedRecurrence = selectedRecurrence,
                    onDaySelected = onDaySelected,
                    onShowTimePicker = { showTimePicker = true },
                    onShowReminderDialog = { showReminderDialog = true },
                    onShowRepeatDialog = { showRepeatDialog = true },
                )

                1 -> DurationTabContent(
                    selectedDay = durDay,
                    selectedMonth = durMonth,
                    selectedYear = durYear,
                    startHour = durStartHour,
                    startMinute = durStartMinute,
                    endHour = durEndHour,
                    endMinute = durEndMinute,
                    isAllDay = durAllDay,
                    selectedReminders = activeReminders,
                    selectedRecurrence = durRecurrence,
                    onAllDayChanged = { durAllDay = it },
                    onShowDateDialog = { showDurDateDialog = true },
                    onShowTimeDialog = { showDurTimeDialog = true },
                    onShowReminderDialog = { showReminderDialog = true },
                    onShowRepeatDialog = { showDurRepeatDialog = true },
                    onClearReminders = { durationTabReminders = emptySet() },
                )
            }

            Spacer(Modifier.height(dimens.spacerXXLarge))
        }
    }

    if (showDurDateDialog) {
        DurationDateDialog(
            selectedDay = durDay,
            selectedMonth = durMonth,
            selectedYear = durYear,
            onDaySelected = { day, month, year ->
                durDay = day
                durMonth = month
                durYear = year
            },
            onDismiss = { showDurDateDialog = false },
        )
    }

    if (showDurTimeDialog) {
        DurationTimeRangeDialog(
            startHour = durStartHour,
            startMinute = durStartMinute,
            endHour = durEndHour,
            endMinute = durEndMinute,
            onConfirm = { sh, sm, eh, em ->
                durStartHour = sh
                durStartMinute = sm
                durEndHour = eh
                durEndMinute = em
                showDurTimeDialog = false
            },
            onDismiss = { showDurTimeDialog = false },
        )
    }

    if (showDurRepeatDialog) {
        DurationRepeatDialog(
            selected = durRecurrence,
            selectedDay = durDay,
            selectedMonth = durMonth,
            onSelected = {
                durRecurrence = it
                showDurRepeatDialog = false
            },
            onDismiss = { showDurRepeatDialog = false },
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = if (selectedHour >= 0) selectedHour else 1,
            initialMinute = if (selectedMinute >= 0) selectedMinute else 0,
            onConfirm = { hour, minute ->
                onTimeSelected(hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showReminderDialog) {
        ReminderDialog(
            selected = activeReminders,
            onConfirm = { newReminders ->
                if (selectedTab == 0) {
                    dateTabReminders = newReminders
                } else {
                    durationTabReminders = newReminders
                }
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false },
        )
    }

    if (showRepeatDialog) {
        RepeatDialog(
            selected = selectedRecurrence,
            onSelected = {
                onRecurrenceSelected(it)
                showRepeatDialog = false
            },
            onDismiss = { showRepeatDialog = false },
        )
    }
}

@Composable
private fun DateTabContent(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    selectedHour: Int,
    selectedMinute: Int,
    selectedReminders: Set<ReminderOption>,
    selectedRecurrence: RecurrenceType,
    onDaySelected: (day: Int, month: Int, year: Int) -> Unit,
    onShowTimePicker: () -> Unit,
    onShowReminderDialog: () -> Unit,
    onShowRepeatDialog: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = PAGER_INITIAL_PAGE,
        pageCount = { PAGER_MONTH_RANGE * 2 },
    )
    val coroutineScope = rememberCoroutineScope()

    val (displayMonth, displayYear) = pageToMonthYear(pagerState.currentPage)

    val dimens = OpenTasksTheme.dimens
    Column(modifier = Modifier.padding(horizontal = dimens.paddingXLarge)) {
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

        WeekdayHeader()

        Spacer(Modifier.height(dimens.spacerLarge))

        // Calendar pager
        HorizontalPager(
            state = pagerState,
        ) { page ->
            val (month, year) = pageToMonthYear(page)
            SelectableDayGrid(
                month = month,
                year = year,
                selectedDay = if (month == selectedMonth && year == selectedYear) selectedDay else 0,
                todayDay = if (month == currentMonth() && year == currentYear()) currentDay() else 0,
                onDayClick = { day -> onDaySelected(day, month, year) },
            )
        }

        Spacer(Modifier.height(dimens.spacerXXLarge))

        SettingRow(
            icon = Res.drawable.ic_schedule,
            label = stringResource(Res.string.time),
            value = if (selectedHour >= 0) formatTime(
                selectedHour,
                selectedMinute
            ) else stringResource(Res.string.none),
            onClick = onShowTimePicker,
        )

        SettingRow(
            icon = Res.drawable.ic_alarm,
            label = stringResource(Res.string.reminder),
            value = if (selectedReminders.isNotEmpty()) {
                selectedReminders.map { stringResource(it.labelRes) }.joinToString(", ")
            } else {
                stringResource(Res.string.none)
            },
            onClick = onShowReminderDialog,
        )

        SettingRow(
            icon = Res.drawable.ic_repeat,
            label = stringResource(Res.string.repeat),
            value = recurrenceLabel(selectedRecurrence),
            onClick = onShowRepeatDialog,
        )
    }
}

@Composable
private fun DurationTabContent(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    isAllDay: Boolean,
    selectedReminders: Set<ReminderOption>,
    selectedRecurrence: RecurrenceType,
    onAllDayChanged: (Boolean) -> Unit,
    onShowDateDialog: () -> Unit,
    onShowTimeDialog: () -> Unit,
    onShowReminderDialog: () -> Unit,
    onShowRepeatDialog: () -> Unit,
    onClearReminders: () -> Unit,
) {
    val isToday =
        selectedDay == currentDay() && selectedMonth == currentMonth() && selectedYear == currentYear()
    val dayOfWeek = dayOfWeekName(dayOfWeekIndex(selectedYear, selectedMonth, 1), selectedDay)
    val dateLabel = "$dayOfWeek, ${monthNameShort(selectedMonth)} $selectedDay"
    val timeLabel = "${formatTime(startHour, startMinute)} - ${formatTime(endHour, endMinute)}"
    val durationMinutes = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
    val durationLabel = if (durationMinutes >= 60) {
        val h = durationMinutes / 60
        val m = durationMinutes % 60
        if (m > 0) stringResource(Res.string.duration_hours_minutes, h, m)
        else if (h > 1) stringResource(Res.string.duration_hours_plural, h)
        else stringResource(Res.string.duration_hours, h)
    } else {
        stringResource(Res.string.duration_minutes, durationMinutes)
    }

    val dimens = OpenTasksTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.paddingXLarge),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacerXLarge),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(dimens.cornerXLarge),
                    )
                    .clickable(onClick = onShowDateDialog)
                    .padding(dimens.paddingXLarge),
            ) {
                Text(
                    stringResource(Res.string.date),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(dimens.spacerSmall))
                Text(
                    dateLabel,
                    color = PrimaryBlue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (isToday) {
                    Text(
                        stringResource(Res.string.today),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(dimens.cornerXLarge),
                    )
                    .clickable(onClick = onShowTimeDialog)
                    .padding(dimens.paddingXLarge),
            ) {
                Text(
                    stringResource(Res.string.time),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(dimens.spacerSmall))
                if (isAllDay) {
                    Text(
                        stringResource(Res.string.all_day),
                        color = PrimaryBlue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Text(
                        timeLabel,
                        color = PrimaryBlue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        durationLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(dimens.spacerXXLarge))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.all_day),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = isAllDay,
                onCheckedChange = onAllDayChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryBlue,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }

        Spacer(Modifier.height(dimens.spacerLarge))

        // Reminder row with clear button
        val hasReminders = selectedReminders.isNotEmpty()
        val reminderLabels = selectedReminders.map { stringResource(it.labelRes) }
        val reminderLabel = if (hasReminders) {
            reminderLabels.joinToString(", ")
        } else {
            stringResource(Res.string.none)
        }
        LabelValueNavigationRow(
            icon = Res.drawable.ic_alarm,
            label = stringResource(Res.string.reminder),
            value = reminderLabel,
            valueColor = if (hasReminders) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onShowReminderDialog,
            trailingContent = {
                if (hasReminders) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.clear_reminder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(dimens.iconSmall)
                            .clickable(onClick = onClearReminders),
                    )
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimens.iconSmall),
                    )
                }
            },
        )

        SettingRow(
            icon = Res.drawable.ic_repeat,
            label = stringResource(Res.string.repeat),
            value = recurrenceLabel(selectedRecurrence),
            onClick = onShowRepeatDialog,
        )
    }
}

@Composable
private fun SettingRow(
    icon: DrawableResource,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    LabelValueNavigationRow(
        icon = icon,
        label = label,
        value = value,
        onClick = onClick,
    )
}
