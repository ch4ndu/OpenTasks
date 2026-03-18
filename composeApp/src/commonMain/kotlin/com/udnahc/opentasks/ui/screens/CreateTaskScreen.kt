package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.extensions.computeDeadlineUtcMillis
import com.udnahc.opentasks.data.extensions.currentDay
import com.udnahc.opentasks.data.extensions.currentMonth
import com.udnahc.opentasks.data.extensions.currentYear
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.daysInMonth
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskList
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.PriorityLow
import com.udnahc.opentasks.ui.theme.PriorityMedium
import com.udnahc.opentasks.ui.theme.PriorityNone
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_subtask
import opentasks.composeapp.generated.resources.all_day
import opentasks.composeapp.generated.resources.am
import opentasks.composeapp.generated.resources.apr
import opentasks.composeapp.generated.resources.april
import opentasks.composeapp.generated.resources.attach
import opentasks.composeapp.generated.resources.aug
import opentasks.composeapp.generated.resources.august
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.clear_reminder
import opentasks.composeapp.generated.resources.close
import opentasks.composeapp.generated.resources.confirm
import opentasks.composeapp.generated.resources.constant_reminder
import opentasks.composeapp.generated.resources.custom
import opentasks.composeapp.generated.resources.daily
import opentasks.composeapp.generated.resources.date
import opentasks.composeapp.generated.resources.date_and_reminder
import opentasks.composeapp.generated.resources.dec
import opentasks.composeapp.generated.resources.december
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.description_hint
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.duration
import opentasks.composeapp.generated.resources.duration_hours
import opentasks.composeapp.generated.resources.duration_hours_minutes
import opentasks.composeapp.generated.resources.duration_hours_plural
import opentasks.composeapp.generated.resources.duration_minutes
import opentasks.composeapp.generated.resources.end
import opentasks.composeapp.generated.resources.every_weekday
import opentasks.composeapp.generated.resources.feb
import opentasks.composeapp.generated.resources.february
import opentasks.composeapp.generated.resources.fri
import opentasks.composeapp.generated.resources.high_priority
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_alarm
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.ic_attach
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_chevron_left
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.ic_flag
import opentasks.composeapp.generated.resources.ic_label
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.ic_redo
import opentasks.composeapp.generated.resources.ic_repeat
import opentasks.composeapp.generated.resources.ic_schedule
import opentasks.composeapp.generated.resources.ic_undo
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.jan
import opentasks.composeapp.generated.resources.january
import opentasks.composeapp.generated.resources.jul
import opentasks.composeapp.generated.resources.july
import opentasks.composeapp.generated.resources.jun
import opentasks.composeapp.generated.resources.june
import opentasks.composeapp.generated.resources.low_priority
import opentasks.composeapp.generated.resources.mar
import opentasks.composeapp.generated.resources.march
import opentasks.composeapp.generated.resources.may
import opentasks.composeapp.generated.resources.may_short
import opentasks.composeapp.generated.resources.medium_priority
import opentasks.composeapp.generated.resources.mon
import opentasks.composeapp.generated.resources.monthly
import opentasks.composeapp.generated.resources.monthly_with_day
import opentasks.composeapp.generated.resources.more
import opentasks.composeapp.generated.resources.next_month
import opentasks.composeapp.generated.resources.no_priority
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.nov
import opentasks.composeapp.generated.resources.november
import opentasks.composeapp.generated.resources.oct
import opentasks.composeapp.generated.resources.october
import opentasks.composeapp.generated.resources.ok
import opentasks.composeapp.generated.resources.pm
import opentasks.composeapp.generated.resources.previous_month
import opentasks.composeapp.generated.resources.priority
import opentasks.composeapp.generated.resources.redo
import opentasks.composeapp.generated.resources.reminder
import opentasks.composeapp.generated.resources.reminder_1_day_early
import opentasks.composeapp.generated.resources.reminder_1_day_early_duration
import opentasks.composeapp.generated.resources.reminder_1_hour_early
import opentasks.composeapp.generated.resources.reminder_1_week_early
import opentasks.composeapp.generated.resources.reminder_2_days_early
import opentasks.composeapp.generated.resources.reminder_30_mins_early
import opentasks.composeapp.generated.resources.reminder_3_days_early
import opentasks.composeapp.generated.resources.reminder_5_mins_early
import opentasks.composeapp.generated.resources.reminder_at_the_end
import opentasks.composeapp.generated.resources.reminder_on_the_day
import opentasks.composeapp.generated.resources.reminder_on_time
import opentasks.composeapp.generated.resources.repeat
import opentasks.composeapp.generated.resources.sat
import opentasks.composeapp.generated.resources.select
import opentasks.composeapp.generated.resources.sep
import opentasks.composeapp.generated.resources.september
import opentasks.composeapp.generated.resources.start
import opentasks.composeapp.generated.resources.subtasks
import opentasks.composeapp.generated.resources.sun
import opentasks.composeapp.generated.resources.tags
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.time
import opentasks.composeapp.generated.resources.title_hint
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.undo
import opentasks.composeapp.generated.resources.wed
import opentasks.composeapp.generated.resources.weekly
import opentasks.composeapp.generated.resources.weekly_with_day
import opentasks.composeapp.generated.resources.yearly
import opentasks.composeapp.generated.resources.yearly_with_date
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

data class SubtaskItem(
    val text: String = "",
    val isChecked: Boolean = false,
)

internal enum class ReminderOption(val labelRes: StringResource) {
    NONE(Res.string.none),
    ON_THE_DAY(Res.string.reminder_on_the_day),
    ONE_DAY_EARLY(Res.string.reminder_1_day_early),
    TWO_DAYS_EARLY(Res.string.reminder_2_days_early),
    THREE_DAYS_EARLY(Res.string.reminder_3_days_early),
    ONE_WEEK_EARLY(Res.string.reminder_1_week_early),
}

private enum class DurationReminderOption(val labelRes: StringResource) {
    NONE(Res.string.none),
    ON_TIME(Res.string.reminder_on_time),
    FIVE_MINS_EARLY(Res.string.reminder_5_mins_early),
    THIRTY_MINS_EARLY(Res.string.reminder_30_mins_early),
    ONE_HOUR_EARLY(Res.string.reminder_1_hour_early),
    ONE_DAY_EARLY(Res.string.reminder_1_day_early_duration),
    AT_THE_END(Res.string.reminder_at_the_end),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    initialPriority: TaskPriority = TaskPriority.HIGH,
    initialListId: Long = 1L,
    initialDay: Int = 0,
    initialMonth: Int = 0,
    initialYear: Int = 0,
    editTask: Task? = null,
    taskLists: List<TaskList> = emptyList(),
    onAddList: (String) -> Unit = {},
    onSave: (title: String, content: String, priority: TaskPriority, deadline: Long?, reminder: Int, recurrence: RecurrenceType, listId: Long) -> Unit = { _, _, _, _, _, _, _ -> },
) {
    val stateKey = editTask?.id ?: 0L
    var title by remember(stateKey) { mutableStateOf(editTask?.title ?: "") }
    var description by remember(stateKey) { mutableStateOf(editTask?.content ?: "") }
    var priority by remember(stateKey) { mutableStateOf(editTask?.priority ?: initialPriority) }
    var selectedListId by remember(stateKey) { mutableStateOf(editTask?.listId ?: initialListId) }
    var showListPicker by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    var isSubtaskMode by remember(stateKey) { mutableStateOf(false) }
    var subtaskToggleCount by remember { mutableIntStateOf(0) }
    val subtasks = remember { mutableStateListOf<SubtaskItem>() }
    val scope = rememberCoroutineScope()
    val descriptionFocusRequester = remember { FocusRequester() }
    val subtaskFocusRequester = remember { FocusRequester() }

    fun syncDescriptionToSubtasks() {
        subtasks.clear()
        val lines = description.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            subtasks.add(SubtaskItem())
        } else {
            subtasks.addAll(lines.map { SubtaskItem(text = it) })
        }
    }

    fun syncSubtasksToDescription() {
        description = subtasks
            .filter { it.text.isNotBlank() }
            .joinToString("\n") { it.text }
    }

    // Date & Reminder state
    var showDateSheet by remember { mutableStateOf(false) }
    var selectedDay by remember(stateKey) { mutableIntStateOf(editTask?.deadline?.let { extractDay(it) } ?: initialDay) }
    var selectedMonth by remember(stateKey) { mutableIntStateOf(editTask?.deadline?.let { extractMonth(it) } ?: initialMonth) }
    var selectedYear by remember(stateKey) { mutableIntStateOf(editTask?.deadline?.let { extractYear(it) } ?: initialYear) }
    var selectedHour by remember(stateKey) { mutableIntStateOf(editTask?.deadline?.let { extractHour(it) } ?: 8) }
    var selectedMinute by remember(stateKey) { mutableIntStateOf(editTask?.deadline?.let { extractMinute(it) } ?: 0) }
    var selectedReminder by remember(stateKey) { mutableStateOf(editTask?.let { reminderFromTask(it) } ?: ReminderOption.ON_THE_DAY) }
    var selectedRecurrence by remember(stateKey) { mutableStateOf(editTask?.recurrenceType ?: RecurrenceType.NONE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            CreateTaskTopBar(
                listName = taskLists.find { it.id == selectedListId }?.name ?: "Inbox",
                priority = priority,
                showPriorityMenu = showPriorityMenu,
                onShowPriorityMenu = { showPriorityMenu = it },
                onPrioritySelected = {
                    priority = it
                    showPriorityMenu = false
                },
                onBack = onDismiss,
                onListClick = { showListPicker = true },
            )

            val dimens = OpenTasksTheme.dimens
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = dimens.paddingXLarge)
            ) {
                // Date and Reminder row
                DateReminderRow(
                    selectedDay = selectedDay,
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    selectedReminder = selectedReminder,
                    selectedRecurrence = selectedRecurrence,
                    onClick = { showDateSheet = true },
                )

                Spacer(Modifier.height(dimens.spacerLarge))

                TaskTitleField(
                    title = title,
                    onTitleChange = { title = it },
                    onFocused = { scope.launch { sheetState.expand() } },
                )

                Spacer(Modifier.height(dimens.spacerXLarge))

                if (isSubtaskMode) {
                    Box(modifier = Modifier.defaultMinSize(minHeight = dimens.minPagerHeight)) {
                        SubtaskList(
                            subtasks = subtasks,
                            onSubtaskTextChange = { index, text ->
                                subtasks[index] = subtasks[index].copy(text = text)
                            },
                            onSubtaskCheckedChange = { index, checked ->
                                subtasks[index] = subtasks[index].copy(isChecked = checked)
                            },
                            onDeleteSubtask = { index ->
                                subtasks.removeAt(index)
                            },
                            onAddSubtask = {
                                subtasks.add(SubtaskItem())
                            },
                            firstItemFocusRequester = subtaskFocusRequester,
                        )
                    }
                    if (subtaskToggleCount > 0) {
                        LaunchedEffect(subtaskToggleCount) {
                            if (subtasks.isNotEmpty()) {
                                subtaskFocusRequester.requestFocus()
                            }
                        }
                    }
                } else {
                    if (subtaskToggleCount > 0) {
                        LaunchedEffect(subtaskToggleCount) {
                            descriptionFocusRequester.requestFocus()
                        }
                    }
                    TaskDescriptionField(
                        description = description,
                        onDescriptionChange = { description = it },
                        focusRequester = descriptionFocusRequester,
                        onFocused = { scope.launch { sheetState.expand() } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            CreateTaskBottomBar(
                isSubtaskMode = isSubtaskMode,
                onToggleSubtaskMode = {
                    if (isSubtaskMode) {
                        syncSubtasksToDescription()
                    } else {
                        syncDescriptionToSubtasks()
                    }
                    isSubtaskMode = !isSubtaskMode
                    subtaskToggleCount++
                },
                onDone = {
                    if (title.isNotBlank()) {
                        val deadlineMs: Long? =
                            if (selectedYear > 0 && selectedMonth > 0 && selectedDay > 0) {
                                computeDeadlineMillis(
                                    selectedYear,
                                    selectedMonth,
                                    selectedDay,
                                    selectedHour,
                                    selectedMinute
                                )
                            } else null
                        val reminderDays = when (selectedReminder) {
                            ReminderOption.NONE -> 0
                            ReminderOption.ON_THE_DAY -> 0
                            ReminderOption.ONE_DAY_EARLY -> 1
                            ReminderOption.TWO_DAYS_EARLY -> 2
                            ReminderOption.THREE_DAYS_EARLY -> 3
                            ReminderOption.ONE_WEEK_EARLY -> 7
                        }
                        if (isSubtaskMode) syncSubtasksToDescription()
                        onSave(
                            title,
                            description,
                            priority,
                            deadlineMs,
                            reminderDays,
                            selectedRecurrence,
                            selectedListId,
                        )
                    }
                    onDismiss()
                },
            )
        }
    }

    if (showDateSheet) {
        DateReminderBottomSheet(
            selectedDay = selectedDay,
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            selectedReminder = selectedReminder,
            selectedRecurrence = selectedRecurrence,
            onDaySelected = { day, month, year ->
                selectedDay = day
                selectedMonth = month
                selectedYear = year
            },
            onTimeSelected = { hour, minute ->
                selectedHour = hour
                selectedMinute = minute
            },
            onReminderSelected = { selectedReminder = it },
            onRecurrenceSelected = { selectedRecurrence = it },
            onDismiss = { showDateSheet = false },
            onConfirm = { showDateSheet = false },
        )
    }

    if (showListPicker) {
        val listPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ListPickerBottomSheet(
            sheetState = listPickerState,
            lists = taskLists,
            selectedListId = selectedListId,
            onListSelected = { taskList ->
                selectedListId = taskList.id
                showListPicker = false
            },
            onAddList = onAddList,
            onDismiss = { showListPicker = false },
        )
    }
}

@Composable
private fun TaskTitleField(
    title: String,
    onTitleChange: (String) -> Unit,
    onFocused: () -> Unit,
) {
    BasicTextField(
        value = title,
        onValueChange = onTitleChange,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
        ),
        cursorBrush = SolidColor(PrimaryBlue),
        decorationBox = { innerTextField ->
            Box {
                if (title.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.title_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                innerTextField()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            },
    )
}

@Composable
private fun TaskDescriptionField(
    description: String,
    onDescriptionChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        BasicTextField(
            value = description,
            onValueChange = onDescriptionChange,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(PrimaryBlue),
            decorationBox = { innerTextField ->
                Box {
                    if (description.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.description_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = dimens.minPagerHeight)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onFocused()
                },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskTopBar(
    listName: String = "Inbox",
    priority: TaskPriority,
    showPriorityMenu: Boolean,
    onShowPriorityMenu: (Boolean) -> Unit,
    onPrioritySelected: (TaskPriority) -> Unit,
    onBack: () -> Unit,
    onListClick: () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = stringResource(Res.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        title = {
            val dimens = OpenTasksTheme.dimens
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onListClick),
            ) {
                Text(
                    text = listName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(dimens.spacerMedium))
                Icon(
                    painter = painterResource(Res.drawable.ic_unfold),
                    contentDescription = stringResource(Res.string.select),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimens.iconDefault),
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { onShowPriorityMenu(true) }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_flag),
                        contentDescription = stringResource(Res.string.priority),
                        tint = priorityColor(priority),
                    )
                }
                PriorityDropdown(
                    expanded = showPriorityMenu,
                    currentPriority = priority,
                    onDismiss = { onShowPriorityMenu(false) },
                    onSelected = onPrioritySelected,
                )
            }
            IconButton(onClick = { }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_more_vert),
                    contentDescription = stringResource(Res.string.more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PriorityDropdown(
    expanded: Boolean,
    currentPriority: TaskPriority,
    onDismiss: () -> Unit,
    onSelected: (TaskPriority) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        PriorityMenuItem(
            TaskPriority.HIGH,
            stringResource(Res.string.high_priority),
            PriorityHigh,
            currentPriority,
            onSelected
        )
        PriorityMenuItem(
            TaskPriority.MEDIUM,
            stringResource(Res.string.medium_priority),
            PriorityMedium,
            currentPriority,
            onSelected
        )
        PriorityMenuItem(
            TaskPriority.LOW,
            stringResource(Res.string.low_priority),
            PriorityLow,
            currentPriority,
            onSelected
        )
        PriorityMenuItem(
            TaskPriority.NONE,
            stringResource(Res.string.no_priority),
            MaterialTheme.colorScheme.onSurfaceVariant,
            currentPriority,
            onSelected
        )
    }
}

@Composable
private fun PriorityMenuItem(
    priority: TaskPriority,
    label: String,
    color: Color,
    currentPriority: TaskPriority,
    onSelected: (TaskPriority) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.ic_flag),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(dimens.iconDefault),
                )
                Spacer(Modifier.width(dimens.spacerXLarge))
                Text(label, color = MaterialTheme.colorScheme.onBackground)
                if (priority == currentPriority) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(dimens.iconDefault),
                    )
                }
            }
        },
        onClick = { onSelected(priority) },
    )
}

private fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> PriorityHigh
    TaskPriority.MEDIUM -> PriorityMedium
    TaskPriority.LOW -> PriorityLow
    TaskPriority.NONE -> PriorityNone
}

@Composable
private fun DateReminderRow(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    selectedHour: Int,
    selectedMinute: Int,
    selectedReminder: ReminderOption,
    selectedRecurrence: RecurrenceType,
    onClick: () -> Unit,
) {
    val hasDate = selectedDay > 0 && selectedMonth > 0 && selectedYear > 0
    val hasTime = selectedHour >= 0
    val hasReminder = selectedReminder != ReminderOption.NONE
    val hasRecurrence = selectedRecurrence != RecurrenceType.NONE

    val dimens = OpenTasksTheme.dimens

    if (!hasDate) {
        // No date selected — show placeholder row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.priorityIndicatorSize)
                    .border(dimens.priorityIndicatorBorder, PriorityHigh, RoundedCornerShape(dimens.cornerMedium)),
            )
            Spacer(Modifier.width(dimens.spacerXLarge))
            Text(
                text = stringResource(Res.string.date_and_reminder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        // Date selected — show formatted date/time/reminder/repeat
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = dimens.paddingMedium),
        ) {
            // Line 1: Date + Time + reminder icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Build date string: e.g., "Next Fri, Mar 20, 1:00AM"
                val fdow = dayOfWeekIndex(selectedYear, selectedMonth, 1)
                val dowName = dayOfWeekName(fdow, selectedDay)
                val monthShort = monthNameShort(selectedMonth)
                val dateText = buildString {
                    append("$dowName, $monthShort $selectedDay")
                    if (hasTime) {
                        append(", ")
                        append(formatTime(selectedHour, selectedMinute).replace(" ", ""))
                    }
                }
                Text(
                    text = dateText,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (hasReminder) {
                    Spacer(Modifier.width(dimens.spacerSmall))
                    Icon(
                        painter = painterResource(Res.drawable.ic_alarm),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimens.iconMedium),
                    )
                }
            }

            // Line 2: Recurrence
            if (hasRecurrence) {
                Spacer(Modifier.height(dimens.spacerTiny))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val recLabel = recurrenceLabel(selectedRecurrence)
                    // Build recurrence string with day: e.g., "Weekly on Fri"
                    val fdow = dayOfWeekIndex(selectedYear, selectedMonth, 1)
                    val dowName = dayOfWeekName(fdow, selectedDay)
                    val recText = if (selectedRecurrence == RecurrenceType.WEEKLY) {
                        "$recLabel on $dowName"
                    } else {
                        recLabel
                    }
                    Text(
                        text = recText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(dimens.spacerSmall))
                    Icon(
                        painter = painterResource(Res.drawable.ic_repeat),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimens.iconSmall),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtaskList(
    subtasks: List<SubtaskItem>,
    onSubtaskTextChange: (Int, String) -> Unit,
    onSubtaskCheckedChange: (Int, Boolean) -> Unit,
    onDeleteSubtask: (Int) -> Unit,
    onAddSubtask: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    LazyColumn {
        itemsIndexed(subtasks) { index, subtask ->
            SubtaskRow(
                subtask = subtask,
                onTextChange = { onSubtaskTextChange(index, it) },
                onCheckedChange = { onSubtaskCheckedChange(index, it) },
                onDelete = { onDeleteSubtask(index) },
                focusRequester = if (index == 0) firstItemFocusRequester else null,
            )
        }
        item {
            val dimens = OpenTasksTheme.dimens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddSubtask)
                    .padding(vertical = dimens.paddingMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimens.iconDefault),
                )
                Spacer(Modifier.width(dimens.spacerXLarge))
                Text(
                    stringResource(Res.string.add_subtask),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    subtask: SubtaskItem,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = subtask.isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedColor = PrimaryBlue,
            ),
            modifier = Modifier.size(dimens.priorityIndicatorSize),
        )
        Spacer(Modifier.width(dimens.spacerLarge))
        BasicTextField(
            value = subtask.text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(PrimaryBlue),
            modifier = Modifier.weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(dimens.iconXLarge)) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.delete),
                tint = PriorityHigh,
                modifier = Modifier.size(dimens.iconSmall),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = dimens.dividerThin)
}

@Composable
private fun CreateTaskBottomBar(
    isSubtaskMode: Boolean,
    onToggleSubtaskMode: () -> Unit,
    onDone: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = dimens.paddingMedium, vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { }) {
            Icon(
                painter = painterResource(Res.drawable.ic_label),
                contentDescription = stringResource(Res.string.tags),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleSubtaskMode) {
            Icon(
                painter = painterResource(Res.drawable.ic_list),
                contentDescription = stringResource(Res.string.subtasks),
                tint = if (isSubtaskMode) PrimaryBlue
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { }) {
            Icon(
                painter = painterResource(Res.drawable.ic_attach),
                contentDescription = stringResource(Res.string.attach),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        if (isSubtaskMode) {
            IconButton(onClick = { }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_undo),
                    contentDescription = stringResource(Res.string.undo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_redo),
                    contentDescription = stringResource(Res.string.redo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onDone) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = stringResource(Res.string.done),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateReminderBottomSheet(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    selectedHour: Int,
    selectedMinute: Int,
    selectedReminder: ReminderOption,
    selectedRecurrence: RecurrenceType,
    onDaySelected: (day: Int, month: Int, year: Int) -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onReminderSelected: (ReminderOption) -> Unit,
    onRecurrenceSelected: (RecurrenceType) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }

    // Duration tab state
    var durDay by remember { mutableIntStateOf(currentDay()) }
    var durMonth by remember { mutableIntStateOf(currentMonth()) }
    var durYear by remember { mutableIntStateOf(currentYear()) }
    var durStartHour by remember { mutableIntStateOf(16) }
    var durStartMinute by remember { mutableIntStateOf(0) }
    var durEndHour by remember { mutableIntStateOf(17) }
    var durEndMinute by remember { mutableIntStateOf(0) }
    var durAllDay by remember { mutableStateOf(false) }
    var durReminder by remember { mutableStateOf(DurationReminderOption.NONE) }
    var durRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
    var showDurDateDialog by remember { mutableStateOf(false) }
    var showDurTimeDialog by remember { mutableStateOf(false) }
    var showDurReminderDialog by remember { mutableStateOf(false) }
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
                        // Duration tab — propagate duration date back to parent
                        onDaySelected(durDay, durMonth, durYear)
                        if (durAllDay) {
                            // All-day tasks should have no time component (midnight)
                            onTimeSelected(0, 0)
                        } else {
                            // Propagate the start time from the duration tab
                            onTimeSelected(durStartHour, durStartMinute)
                        }
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
                    selectedReminder = selectedReminder,
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
                    selectedReminder = durReminder,
                    selectedRecurrence = durRecurrence,
                    onAllDayChanged = { durAllDay = it },
                    onShowDateDialog = { showDurDateDialog = true },
                    onShowTimeDialog = { showDurTimeDialog = true },
                    onShowReminderDialog = { showDurReminderDialog = true },
                    onShowRepeatDialog = { showDurRepeatDialog = true },
                    onClearReminder = { durReminder = DurationReminderOption.NONE },
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

    if (showDurReminderDialog) {
        DurationReminderDialog(
            selected = durReminder,
            onSelected = {
                durReminder = it
                showDurReminderDialog = false
            },
            onDismiss = { showDurReminderDialog = false },
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
            selected = selectedReminder,
            onSelected = {
                onReminderSelected(it)
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

private const val PAGER_MONTH_RANGE = 120 // 10 years in each direction
private const val PAGER_INITIAL_PAGE = PAGER_MONTH_RANGE // current month is at center

private fun pageToMonthYear(page: Int): Pair<Int, Int> {
    val offset = page - PAGER_INITIAL_PAGE
    val baseMonth = currentMonth() - 1 + offset // 0-indexed
    val year = currentYear() + baseMonth.floorDiv(12)
    val month = baseMonth.mod(12) + 1 // back to 1-indexed
    return month to year
}

@Composable
private fun DateTabContent(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    selectedHour: Int,
    selectedMinute: Int,
    selectedReminder: ReminderOption,
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
        // Month header with arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${monthName(displayMonth)} $displayYear",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
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

        // Day of week headers
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

        Spacer(Modifier.height(dimens.spacerLarge))

        // Calendar pager
        HorizontalPager(
            state = pagerState,
        ) { page ->
            val (month, year) = pageToMonthYear(page)
            CalendarGrid(
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
            value = stringResource(selectedReminder.labelRes),
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
private fun CalendarGrid(
    month: Int,
    year: Int,
    selectedDay: Int,
    todayDay: Int,
    onDayClick: (Int) -> Unit,
) {
    val daysInMonth = daysInMonth(year, month)
    val firstDayOfWeek = dayOfWeekIndex(year, month, 1)

    val dimens = OpenTasksTheme.dimens
    val rows = ((daysInMonth + firstDayOfWeek + 6) / 7).coerceAtLeast(6)
    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val dayIndex = row * 7 + col - firstDayOfWeek + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(dimens.reminderRowButtonHeight)
                            .then(
                                if (dayIndex in 1..daysInMonth) {
                                    Modifier.clickable { onDayClick(dayIndex) }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayIndex in 1..daysInMonth) {
                            val isSelected = dayIndex == selectedDay
                            val isToday = dayIndex == todayDay

                            if (isSelected || isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(dimens.reminderDayButtonSize)
                                        .background(
                                            color = if (isSelected) PrimaryBlue else PrimaryBlue.copy(
                                                alpha = 0.3f
                                            ),
                                            shape = RoundedCornerShape(50),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = dayIndex.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                Text(
                                    text = dayIndex.toString(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
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
    selectedReminder: DurationReminderOption,
    selectedRecurrence: RecurrenceType,
    onAllDayChanged: (Boolean) -> Unit,
    onShowDateDialog: () -> Unit,
    onShowTimeDialog: () -> Unit,
    onShowReminderDialog: () -> Unit,
    onShowRepeatDialog: () -> Unit,
    onClearReminder: () -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowReminderDialog)
                .padding(vertical = dimens.paddingLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_alarm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.iconDefault),
            )
            Spacer(Modifier.width(dimens.spacerXLarge))
            Text(
                stringResource(Res.string.reminder),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(selectedReminder.labelRes),
                color = if (selectedReminder != DurationReminderOption.NONE) PrimaryBlue
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (selectedReminder != DurationReminderOption.NONE) {
                Spacer(Modifier.width(dimens.spacerSmall))
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.clear_reminder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(dimens.iconSmall)
                        .clickable(onClick = onClearReminder),
                )
            } else {
                Spacer(Modifier.width(dimens.spacerSmall))
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimens.iconSmall),
                )
            }
        }

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
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconDefault),
        )
        Spacer(Modifier.width(dimens.spacerXLarge))
        Text(label, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(dimens.spacerSmall))
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconSmall),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
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
private fun ReminderDialog(
    selected: ReminderOption,
    onSelected: (ReminderOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ReminderOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
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
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
private fun RepeatDialog(
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

@Composable
private fun recurrenceLabel(type: RecurrenceType): String = when (type) {
    RecurrenceType.NONE -> stringResource(Res.string.none)
    RecurrenceType.DAILY -> stringResource(Res.string.daily)
    RecurrenceType.WEEKLY -> stringResource(Res.string.weekly)
    RecurrenceType.MONTHLY -> stringResource(Res.string.monthly)
    RecurrenceType.YEARLY -> stringResource(Res.string.yearly)
    RecurrenceType.EVERY_WEEKDAY -> stringResource(Res.string.every_weekday)
}

@Composable
private fun formatTime(
    hour: Int,
    minute: Int
): String {
    val amPm = if (hour < 12) stringResource(Res.string.am) else stringResource(Res.string.pm)
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
}

@Composable
private fun monthName(month: Int): String = when (month) {
    1 -> stringResource(Res.string.january)
    2 -> stringResource(Res.string.february)
    3 -> stringResource(Res.string.march)
    4 -> stringResource(Res.string.april)
    5 -> stringResource(Res.string.may)
    6 -> stringResource(Res.string.june)
    7 -> stringResource(Res.string.july)
    8 -> stringResource(Res.string.august)
    9 -> stringResource(Res.string.september)
    10 -> stringResource(Res.string.october)
    11 -> stringResource(Res.string.november)
    12 -> stringResource(Res.string.december)
    else -> ""
}


@Composable
private fun monthNameShort(month: Int): String = when (month) {
    1 -> stringResource(Res.string.jan)
    2 -> stringResource(Res.string.feb)
    3 -> stringResource(Res.string.mar)
    4 -> stringResource(Res.string.apr)
    5 -> stringResource(Res.string.may_short)
    6 -> stringResource(Res.string.jun)
    7 -> stringResource(Res.string.jul)
    8 -> stringResource(Res.string.aug)
    9 -> stringResource(Res.string.sep)
    10 -> stringResource(Res.string.oct)
    11 -> stringResource(Res.string.nov)
    12 -> stringResource(Res.string.dec)
    else -> ""
}

@Composable
private fun dayOfWeekName(
    firstDayOfMonth: Int,
    day: Int
): String {
    val dayOfWeek = (firstDayOfMonth + day - 1) % 7
    return when (dayOfWeek) {
        0 -> stringResource(Res.string.sun)
        1 -> stringResource(Res.string.mon)
        2 -> stringResource(Res.string.tue)
        3 -> stringResource(Res.string.wed)
        4 -> stringResource(Res.string.thu)
        5 -> stringResource(Res.string.fri)
        6 -> stringResource(Res.string.sat)
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationDateDialog(
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
                horizontalArrangement = Arrangement.SpaceBetween,
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
private fun DurationTimeRangeDialog(
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
private fun DurationReminderDialog(
    selected: DurationReminderOption,
    onSelected: (DurationReminderOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var constantReminder by remember { mutableStateOf(false) }
    val dimens = OpenTasksTheme.dimens

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DurationReminderOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = dimens.listRowCompletedVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
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
                        .padding(vertical = dimens.listRowCompletedVerticalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.constant_reminder),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = constantReminder,
                        onCheckedChange = { constantReminder = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
private fun DurationRepeatDialog(
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

private fun computeDeadlineMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int
): Long = computeDeadlineUtcMillis(year, month, day, hour, minute)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun CreateTaskScreenPreview() {
    OpenTasksTheme {
        CreateTaskBottomSheet(
            sheetState = rememberModalBottomSheetState(),
            onDismiss = { },
        )
    }
}


private fun reminderFromTask(task: Task): ReminderOption = when (task.notifyBeforeValue) {
    0 -> if (task.notifyBeforeUnit != NotifyBeforeUnit.NONE)
        ReminderOption.ON_THE_DAY else ReminderOption.NONE
    1 -> ReminderOption.ONE_DAY_EARLY
    2 -> ReminderOption.TWO_DAYS_EARLY
    3 -> ReminderOption.THREE_DAYS_EARLY
    7 -> ReminderOption.ONE_WEEK_EARLY
    else -> ReminderOption.NONE
}
