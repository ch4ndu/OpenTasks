package com.udnahc.opentasks.ui.screens.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.extensions.computeLocalMillis
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.extensions.currentMonth
import com.udnahc.opentasks.data.extensions.currentYear
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.daysInMonth
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.computeLocalMillis
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.close
import opentasks.composeapp.generated.resources.countdown_counting_count_up
import opentasks.composeapp.generated.resources.countdown_counting_countdown
import opentasks.composeapp.generated.resources.countdown_counting_mode
import opentasks.composeapp.generated.resources.countdown_every_weekday
import opentasks.composeapp.generated.resources.countdown_reminder_1_day_early
import opentasks.composeapp.generated.resources.countdown_reminder_1_week_early
import opentasks.composeapp.generated.resources.countdown_reminder_2_days_early
import opentasks.composeapp.generated.resources.countdown_reminder_3_days_early
import opentasks.composeapp.generated.resources.countdown_reminder_on_the_day
import opentasks.composeapp.generated.resources.countdown_smart_list
import opentasks.composeapp.generated.resources.countdown_smart_list_3_days_early
import opentasks.composeapp.generated.resources.countdown_smart_list_7_days_early
import opentasks.composeapp.generated.resources.countdown_smart_list_always
import opentasks.composeapp.generated.resources.countdown_smart_list_do_not_show
import opentasks.composeapp.generated.resources.countdown_smart_list_on_the_day
import opentasks.composeapp.generated.resources.countdown_type
import opentasks.composeapp.generated.resources.daily
import opentasks.composeapp.generated.resources.date
import opentasks.composeapp.generated.resources.edit
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_chevron_left
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.mon
import opentasks.composeapp.generated.resources.monthly
import opentasks.composeapp.generated.resources.name_label
import opentasks.composeapp.generated.resources.next_month
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.ok
import opentasks.composeapp.generated.resources.previous_month
import opentasks.composeapp.generated.resources.reminder
import opentasks.composeapp.generated.resources.repeat
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.save
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.wed
import opentasks.composeapp.generated.resources.weekly
import opentasks.composeapp.generated.resources.yearly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ---- Reminder options for countdowns ----

private enum class CountdownReminderOption(val labelRes: StringResource, val minutesValue: Int) {
    NONE(Res.string.none, Int.MIN_VALUE),
    ON_THE_DAY(Res.string.countdown_reminder_on_the_day, 0),
    ONE_DAY_EARLY(Res.string.countdown_reminder_1_day_early, 1440),
    TWO_DAYS_EARLY(Res.string.countdown_reminder_2_days_early, 2880),
    THREE_DAYS_EARLY(Res.string.countdown_reminder_3_days_early, 4320),
    ONE_WEEK_EARLY(Res.string.countdown_reminder_1_week_early, 10080),
}

private fun Set<CountdownReminderOption>.toRemindersString(): String =
    filter { it != CountdownReminderOption.NONE }
        .joinToString(",") { it.minutesValue.toString() }

private fun String.toCountdownReminderSet(): Set<CountdownReminderOption> {
    if (isBlank()) return emptySet()
    val values = split(",").mapNotNull { it.trim().toIntOrNull() }
    return CountdownReminderOption.entries
        .filter { it != CountdownReminderOption.NONE && it.minutesValue in values }
        .toSet()
}

@Composable
private fun Set<CountdownReminderOption>.displayText(): String {
    if (isEmpty()) return stringResource(Res.string.none)
    val labels = map { stringResource(it.labelRes) }
    return labels.joinToString(", ")
}

// ---- Repeat display ----

private fun recurrenceLabelRes(type: RecurrenceType): StringResource = when (type) {
    RecurrenceType.NONE -> Res.string.none
    RecurrenceType.DAILY -> Res.string.daily
    RecurrenceType.WEEKLY -> Res.string.weekly
    RecurrenceType.MONTHLY -> Res.string.monthly
    RecurrenceType.YEARLY -> Res.string.yearly
    RecurrenceType.EVERY_WEEKDAY -> Res.string.countdown_every_weekday
}

private fun smartListLabelRes(visibility: SmartListVisibility): StringResource = when (visibility) {
    SmartListVisibility.ON_THE_DAY -> Res.string.countdown_smart_list_on_the_day
    SmartListVisibility.THREE_DAYS_EARLY -> Res.string.countdown_smart_list_3_days_early
    SmartListVisibility.SEVEN_DAYS_EARLY -> Res.string.countdown_smart_list_7_days_early
    SmartListVisibility.ALWAYS -> Res.string.countdown_smart_list_always
    SmartListVisibility.DO_NOT_SHOW -> Res.string.countdown_smart_list_do_not_show
}

private fun countingModeLabelRes(mode: CountingMode): StringResource = when (mode) {
    CountingMode.COUNTDOWN -> Res.string.countdown_counting_countdown
    CountingMode.COUNT_UP -> Res.string.countdown_counting_count_up
}

private const val PAGER_INITIAL_PAGE = 600
private const val PAGER_MONTH_RANGE = 600

private fun pageToMonthYear(page: Int): Pair<Int, Int> {
    val delta = page - PAGER_INITIAL_PAGE
    val currentMonth = currentMonth()
    val currentYear = currentYear()
    val totalMonths = (currentYear * 12 + currentMonth - 1) + delta
    val year = totalMonths / 12
    val month = totalMonths % 12 + 1
    return month to year
}

private fun monthYearToPage(month: Int, year: Int): Int {
    val currentMonth = currentMonth()
    val currentYear = currentYear()
    val currentTotal = currentYear * 12 + currentMonth - 1
    val targetTotal = year * 12 + month - 1
    return PAGER_INITIAL_PAGE + (targetTotal - currentTotal)
}

@Composable
fun CreateCountdownScreen(
    editCountdown: Countdown?,
    initialType: CountdownType,
    onSave: (Countdown) -> Unit,
    onBack: () -> Unit,
) {
    CreateCountdownContent(
        editCountdown = editCountdown,
        initialType = initialType,
        onSave = onSave,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCountdownContent(
    editCountdown: Countdown?,
    initialType: CountdownType,
    onSave: (Countdown) -> Unit,
    onBack: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val stateKey = editCountdown?.id ?: ""

    var name by remember(stateKey) { mutableStateOf(editCountdown?.title ?: "") }
    var selectedDay by remember(stateKey) {
        mutableIntStateOf(editCountdown?.targetDate?.let { extractDay(it) } ?: currentDay())
    }
    var selectedMonth by remember(stateKey) {
        mutableIntStateOf(editCountdown?.targetDate?.let { extractMonth(it) } ?: currentMonth())
    }
    var selectedYear by remember(stateKey) {
        mutableIntStateOf(editCountdown?.targetDate?.let { extractYear(it) } ?: currentYear())
    }
    var selectedReminders by remember(stateKey) {
        mutableStateOf(editCountdown?.reminders?.toCountdownReminderSet() ?: emptySet())
    }
    var selectedRecurrence by remember(stateKey) {
        mutableStateOf(editCountdown?.recurrenceType ?: RecurrenceType.NONE)
    }
    var selectedType by remember(stateKey) {
        mutableStateOf(editCountdown?.countdownType ?: initialType)
    }
    var selectedCountingMode by remember(stateKey) {
        mutableStateOf(editCountdown?.countingMode ?: CountingMode.COUNTDOWN)
    }
    var selectedSmartList by remember(stateKey) {
        mutableStateOf(editCountdown?.smartListVisibility ?: SmartListVisibility.ON_THE_DAY)
    }

    // Dialog visibility
    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var showRepeatPicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showCountingModePicker by remember { mutableStateOf(false) }
    var showSmartListPicker by remember { mutableStateOf(false) }

    val typeColor = countdownTypeColor(selectedType)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = dimens.fabAreaBottom),
        ) {
            // Top bar
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.close),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(if (editCountdown != null) Res.string.edit else Res.string.add),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )

            // Icon placeholder
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = dimens.paddingXLarge),
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.calendarEmptyPadding)
                        .clip(CircleShape)
                        .background(typeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = countdownTypeInitial(selectedType),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = typeColor,
                    )
                }
            }

            // Name field
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingSmall),
                shape = RoundedCornerShape(dimens.cornerXLarge),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.paddingMedium, vertical = dimens.paddingSmall),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        cursorColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                    ),
                )
            }

            Spacer(Modifier.height(dimens.spacerLarge))

            // Form rows card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.paddingXLarge),
                shape = RoundedCornerShape(dimens.cornerXLarge),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column {
                    // Date row
                    FormRow(
                        label = stringResource(Res.string.date),
                        value = "${monthNameShort(selectedMonth)} $selectedDay, $selectedYear",
                        onClick = { showDatePicker = true },
                    )

                    // Reminder row
                    FormRow(
                        label = stringResource(Res.string.reminder),
                        value = selectedReminders.displayText(),
                        onClick = { showReminderPicker = true },
                    )

                    // Repeat row
                    FormRow(
                        label = stringResource(Res.string.repeat),
                        value = stringResource(recurrenceLabelRes(selectedRecurrence)),
                        onClick = { showRepeatPicker = true },
                    )

                    // Type row
                    FormRow(
                        label = stringResource(Res.string.countdown_type),
                        value = stringResource(countdownTypeLabelRes(selectedType)),
                        onClick = { showTypePicker = true },
                    )

                    // Counting Mode row
                    FormRow(
                        label = stringResource(Res.string.countdown_counting_mode),
                        value = stringResource(countingModeLabelRes(selectedCountingMode)),
                        onClick = { showCountingModePicker = true },
                    )

                    // Smart List row
                    FormRow(
                        label = stringResource(Res.string.countdown_smart_list),
                        value = stringResource(smartListLabelRes(selectedSmartList)),
                        onClick = { showSmartListPicker = true },
                    )
                }
            }

            Spacer(Modifier.height(dimens.paddingXXLarge))

            // Save button
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val targetDateMillis = computeLocalMillis(selectedYear, selectedMonth, selectedDay, 0, 0)
                    val countdown = if (editCountdown != null) {
                        editCountdown.copy(
                            title = name,
                            targetDate = targetDateMillis,
                            countdownType = selectedType,
                            countingMode = selectedCountingMode,
                            reminders = selectedReminders.toRemindersString(),
                            recurrenceType = selectedRecurrence,
                            smartListVisibility = selectedSmartList,
                        )
                    } else {
                        Countdown(
                            title = name,
                            targetDate = targetDateMillis,
                            countdownType = selectedType,
                            countingMode = selectedCountingMode,
                            reminders = selectedReminders.toRemindersString(),
                            recurrenceType = selectedRecurrence,
                            smartListVisibility = selectedSmartList,
                        )
                    }
                    onSave(countdown)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.paddingXLarge),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(dimens.cornerXLarge),
            ) {
                Text(
                    text = stringResource(Res.string.save),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = dimens.paddingSmall),
                )
            }
        }
    }

    // ---- Dialogs ----

    if (showDatePicker) {
        CountdownDatePickerDialog(
            selectedDay = selectedDay,
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            onDaySelected = { day, month, year ->
                selectedDay = day
                selectedMonth = month
                selectedYear = year
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showReminderPicker) {
        CountdownReminderPickerDialog(
            selected = selectedReminders,
            onConfirm = { selectedReminders = it },
            onDismiss = { showReminderPicker = false },
        )
    }

    if (showRepeatPicker) {
        CountdownRepeatPickerDialog(
            selected = selectedRecurrence,
            onSelected = { selectedRecurrence = it },
            onDismiss = { showRepeatPicker = false },
        )
    }

    if (showTypePicker) {
        CountdownTypePickerDialog(
            selected = selectedType,
            onSelected = { type ->
                selectedType = type
                // Auto-set yearly recurrence for birthday/anniversary
                if ((type == CountdownType.BIRTHDAY || type == CountdownType.ANNIVERSARY)
                    && selectedRecurrence == RecurrenceType.NONE
                ) {
                    selectedRecurrence = RecurrenceType.YEARLY
                }
            },
            onDismiss = { showTypePicker = false },
        )
    }

    if (showCountingModePicker) {
        CountingModePickerDialog(
            selected = selectedCountingMode,
            onSelected = { selectedCountingMode = it },
            onDismiss = { showCountingModePicker = false },
        )
    }

    if (showSmartListPicker) {
        SmartListPickerDialog(
            selected = selectedSmartList,
            onSelected = { selectedSmartList = it },
            onDismiss = { showSmartListPicker = false },
        )
    }
}

// ---- Reusable form row ----

@Composable
private fun FormRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(dimens.spacerSmall))
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconMedium),
        )
    }
}

// ---- Short month name helper ----

private fun monthNameShort(month: Int): String = when (month) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
    5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
    9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
    else -> ""
}

private fun monthNameFull(month: Int): String = when (month) {
    1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
    5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
    9 -> "September"; 10 -> "October"; 11 -> "November"; 12 -> "December"
    else -> ""
}

// ---- Date picker dialog (calendar grid, same pattern as CreateTaskScreen) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownDatePickerDialog(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    onDaySelected: (day: Int, month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialPage = monthYearToPage(selectedMonth, selectedYear)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
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
                    "${monthNameFull(displayMonth)} $displayYear",
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
                // Day-of-week headers
                val dayNames = listOf(
                    stringResource(Res.string.sun),
                    stringResource(Res.string.mon),
                    stringResource(Res.string.tue),
                    stringResource(Res.string.wed),
                    stringResource(Res.string.thu),
                    stringResource(Res.string.fri),
                    stringResource(Res.string.sat),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    dayNames.forEach { day ->
                        Text(
                            text = day,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun CalendarGrid(
    month: Int,
    year: Int,
    selectedDay: Int,
    todayDay: Int,
    onDayClick: (Int) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val totalDays = daysInMonth(year, month)
    val firstDow = dayOfWeekIndex(year, month, 1)

    Column {
        var dayCounter = 1
        for (week in 0 until 6) {
            if (dayCounter > totalDays) break
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val cellDay = if (week == 0 && dow < firstDow || dayCounter > totalDays) {
                        0
                    } else {
                        dayCounter++
                        dayCounter - 1
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(dimens.touchTargetLarge)
                            .then(
                                if (cellDay > 0) {
                                    Modifier.clickable { onDayClick(cellDay) }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cellDay > 0) {
                            val isSelected = cellDay == selectedDay
                            val isToday = cellDay == todayDay
                            Box(
                                modifier = Modifier
                                    .size(dimens.calendarDaySize)
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .clip(CircleShape)
                                                .background(PrimaryBlue)
                                        } else if (isToday) {
                                            Modifier
                                                .clip(CircleShape)
                                                .background(PrimaryBlue.copy(alpha = 0.2f))
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = cellDay.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) Color.White
                                    else MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Reminder picker dialog ----

@Composable
private fun CountdownReminderPickerDialog(
    selected: Set<CountdownReminderOption>,
    onConfirm: (Set<CountdownReminderOption>) -> Unit,
    onDismiss: () -> Unit,
) {
    var localSelected by remember { mutableStateOf(selected) }
    val dimens = OpenTasksTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CountdownReminderOption.entries.forEach { option ->
                    val isSelected = if (option == CountdownReminderOption.NONE) {
                        localSelected.isEmpty()
                    } else {
                        option in localSelected
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                localSelected = if (option == CountdownReminderOption.NONE) {
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
            TextButton(onClick = { onConfirm(localSelected); onDismiss() }) {
                Text(stringResource(Res.string.ok), color = PrimaryBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ---- Repeat picker dialog ----

@Composable
private fun CountdownRepeatPickerDialog(
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
                            .clickable {
                                onSelected(option)
                                onDismiss()
                            }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(recurrenceLabelRes(option)),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

// ---- Type picker dialog ----

@Composable
private fun CountdownTypePickerDialog(
    selected: CountdownType,
    onSelected: (CountdownType) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.countdown_type), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CountdownType.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                onDismiss()
                            }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dimens.touchTargetSmall)
                                .clip(CircleShape)
                                .background(countdownTypeColor(option).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = countdownTypeInitial(option),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = countdownTypeColor(option),
                            )
                        }
                        Spacer(Modifier.width(dimens.spacerXLarge))
                        Text(
                            text = stringResource(countdownTypeLabelRes(option)),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

// ---- Counting mode picker dialog ----

@Composable
private fun CountingModePickerDialog(
    selected: CountingMode,
    onSelected: (CountingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.countdown_counting_mode), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CountingMode.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                onDismiss()
                            }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(countingModeLabelRes(option)),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

// ---- Smart list picker dialog ----

@Composable
private fun SmartListPickerDialog(
    selected: SmartListVisibility,
    onSelected: (SmartListVisibility) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.countdown_smart_list), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SmartListVisibility.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                onDismiss()
                            }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(smartListLabelRes(option)),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

// -- Previews ------------------------------------------------------------------

@Composable
@Preview
private fun CreateCountdownContentPreview() {
    OpenTasksTheme {
        CreateCountdownContent(
            editCountdown = null,
            initialType = CountdownType.COUNTDOWN,
            onSave = {},
            onBack = {},
        )
    }
}

@Composable
@Preview
private fun CreateCountdownEditPreview() {
    OpenTasksTheme {
        CreateCountdownContent(
            editCountdown = Countdown(
                id = "preview-edit",
                title = "Christmas",
                targetDate = 1766620800000L,
                countdownType = CountdownType.HOLIDAY,
            ),
            initialType = CountdownType.HOLIDAY,
            onSave = {},
            onBack = {},
        )
    }
}
