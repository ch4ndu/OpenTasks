package com.udnahc.opentasks.ui.screens.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.udnahc.opentasks.data.extensions.computeLocalMillis
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.extensions.currentMonth
import com.udnahc.opentasks.data.extensions.currentYear
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import com.udnahc.opentasks.ui.screens.DialogCancelTextButton
import com.udnahc.opentasks.ui.screens.DialogOkTextButton
import com.udnahc.opentasks.ui.screens.MonthPagerHeader
import com.udnahc.opentasks.ui.screens.NoIconLabelValueNavigationRow
import com.udnahc.opentasks.ui.screens.OpenTasksCloseButton
import com.udnahc.opentasks.ui.screens.OpenTasksTopBar
import com.udnahc.opentasks.ui.screens.OpenTasksTopBarContainerStyle
import com.udnahc.opentasks.ui.screens.SelectableDayGrid
import com.udnahc.opentasks.ui.screens.SelectedOptionRow
import com.udnahc.opentasks.ui.screens.WeekdayHeader
import com.udnahc.opentasks.ui.screens.monthName
import com.udnahc.opentasks.ui.screens.monthNameShort
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add
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
import opentasks.composeapp.generated.resources.monthly
import opentasks.composeapp.generated.resources.name_label
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.reminder
import opentasks.composeapp.generated.resources.repeat
import opentasks.composeapp.generated.resources.save
import opentasks.composeapp.generated.resources.weekly
import opentasks.composeapp.generated.resources.yearly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// ---- Reminder options for countdowns ----

private enum class CountdownReminderOption(
    val labelRes: StringResource,
    val minutesValue: Int
) {
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

private fun monthYearToPage(
    month: Int,
    year: Int
): Int {
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
internal fun CreateCountdownContent(
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
            OpenTasksTopBar(
                title = stringResource(if (editCountdown != null) Res.string.edit else Res.string.add),
                containerStyle = OpenTasksTopBarContainerStyle.Transparent,
                navigationIcon = {
                    OpenTasksCloseButton(onClick = onBack)
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
                    NoIconLabelValueNavigationRow(
                        label = stringResource(Res.string.date),
                        value = "${monthNameShort(selectedMonth)} $selectedDay, $selectedYear",
                        onClick = { showDatePicker = true },
                    )

                    // Reminder row
                    NoIconLabelValueNavigationRow(
                        label = stringResource(Res.string.reminder),
                        value = selectedReminders.displayText(),
                        onClick = { showReminderPicker = true },
                    )

                    // Repeat row
                    NoIconLabelValueNavigationRow(
                        label = stringResource(Res.string.repeat),
                        value = stringResource(recurrenceLabelRes(selectedRecurrence)),
                        onClick = { showRepeatPicker = true },
                    )

                    // Type row
                    NoIconLabelValueNavigationRow(
                        label = stringResource(Res.string.countdown_type),
                        value = stringResource(countdownTypeLabelRes(selectedType)),
                        onClick = { showTypePicker = true },
                    )

                    // Counting Mode row
                    NoIconLabelValueNavigationRow(
                        label = stringResource(Res.string.countdown_counting_mode),
                        value = stringResource(countingModeLabelRes(selectedCountingMode)),
                        onClick = { showCountingModePicker = true },
                    )

                    // Smart List row
                    NoIconLabelValueNavigationRow(
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
                    val targetDateMillis =
                        computeLocalMillis(selectedYear, selectedMonth, selectedDay, 0, 0)
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
                        todayDay = if (month == currentMonth() && year == currentYear()) currentDay() else 0,
                        useLargeCells = true,
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

// ---- Reminder picker dialog ----

@Composable
private fun CountdownReminderPickerDialog(
    selected: Set<CountdownReminderOption>,
    onConfirm: (Set<CountdownReminderOption>) -> Unit,
    onDismiss: () -> Unit,
) {
    var localSelected by remember { mutableStateOf(selected) }
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
                    SelectedOptionRow(
                        label = stringResource(option.labelRes),
                        isSelected = isSelected,
                        onClick = {
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
                    )
                }
            }
        },
        confirmButton = {
            DialogOkTextButton(onClick = {
                onConfirm(localSelected)
                onDismiss()
            })
        },
        dismissButton = {
            DialogCancelTextButton(onClick = onDismiss)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.repeat), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                RecurrenceType.entries.forEach { option ->
                    SelectedOptionRow(
                        label = stringResource(recurrenceLabelRes(option)),
                        isSelected = option == selected,
                        onClick = {
                            onSelected(option)
                            onDismiss()
                        }
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
                    SelectedOptionRow(
                        label = stringResource(countdownTypeLabelRes(option)),
                        isSelected = option == selected,
                        onClick = {
                            onSelected(option)
                            onDismiss()
                        },
                        leadingContent = {
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
                        }
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

// ---- Counting mode picker dialog ----

@Composable
private fun CountingModePickerDialog(
    selected: CountingMode,
    onSelected: (CountingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.countdown_counting_mode),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                CountingMode.entries.forEach { option ->
                    SelectedOptionRow(
                        label = stringResource(countingModeLabelRes(option)),
                        isSelected = option == selected,
                        onClick = {
                            onSelected(option)
                            onDismiss()
                        }
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

// ---- Smart list picker dialog ----

@Composable
private fun SmartListPickerDialog(
    selected: SmartListVisibility,
    onSelected: (SmartListVisibility) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.countdown_smart_list),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                SmartListVisibility.entries.forEach { option ->
                    SelectedOptionRow(
                        label = stringResource(smartListLabelRes(option)),
                        isSelected = option == selected,
                        onClick = {
                            onSelected(option)
                            onDismiss()
                        }
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
