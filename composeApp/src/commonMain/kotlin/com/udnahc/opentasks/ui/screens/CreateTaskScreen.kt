package com.udnahc.opentasks.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.OutlinedTextField
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
import com.udnahc.opentasks.data.extensions.computeLocalMillis
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
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.PriorityLow
import com.udnahc.opentasks.ui.theme.PriorityMedium
import com.udnahc.opentasks.ui.theme.PriorityNone
import com.udnahc.opentasks.ui.util.rememberOpenInMapsAction
import kotlinx.coroutines.launch
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_subtask
import opentasks.composeapp.generated.resources.all_day
import opentasks.composeapp.generated.resources.am
import opentasks.composeapp.generated.resources.apr
import opentasks.composeapp.generated.resources.april
import opentasks.composeapp.generated.resources.attach
import opentasks.composeapp.generated.resources.attendees_hint
import opentasks.composeapp.generated.resources.aug
import opentasks.composeapp.generated.resources.august
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.clear_reminder
import opentasks.composeapp.generated.resources.close
import opentasks.composeapp.generated.resources.confirm
import opentasks.composeapp.generated.resources.custom
import opentasks.composeapp.generated.resources.daily
import opentasks.composeapp.generated.resources.date
import opentasks.composeapp.generated.resources.date_and_reminder
import opentasks.composeapp.generated.resources.dec
import opentasks.composeapp.generated.resources.december
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.delete_task_message
import opentasks.composeapp.generated.resources.delete_task_title
import opentasks.composeapp.generated.resources.ic_delete
import opentasks.composeapp.generated.resources.description_hint
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.duration
import opentasks.composeapp.generated.resources.duration_hours
import opentasks.composeapp.generated.resources.event_status_hint
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
import opentasks.composeapp.generated.resources.ic_dropdown
import opentasks.composeapp.generated.resources.ic_flag
import opentasks.composeapp.generated.resources.ic_group
import opentasks.composeapp.generated.resources.ic_info
import opentasks.composeapp.generated.resources.ic_label
import opentasks.composeapp.generated.resources.ic_link
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_location_on
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.ic_open_in_new
import opentasks.composeapp.generated.resources.ic_person
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
import opentasks.composeapp.generated.resources.location_hint
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
import opentasks.composeapp.generated.resources.more_details
import opentasks.composeapp.generated.resources.next_month
import opentasks.composeapp.generated.resources.no_priority
import opentasks.composeapp.generated.resources.none
import opentasks.composeapp.generated.resources.nov
import opentasks.composeapp.generated.resources.november
import opentasks.composeapp.generated.resources.oct
import opentasks.composeapp.generated.resources.october
import opentasks.composeapp.generated.resources.ok
import opentasks.composeapp.generated.resources.open_in_maps
import opentasks.composeapp.generated.resources.organizer_hint
import opentasks.composeapp.generated.resources.pm
import opentasks.composeapp.generated.resources.previous_month
import opentasks.composeapp.generated.resources.priority
import opentasks.composeapp.generated.resources.redo
import opentasks.composeapp.generated.resources.reminder
import opentasks.composeapp.generated.resources.reminder_1_day_early
import opentasks.composeapp.generated.resources.reminder_1_hour_early
import opentasks.composeapp.generated.resources.reminder_1_week_early
import opentasks.composeapp.generated.resources.reminder_2_days_early
import opentasks.composeapp.generated.resources.reminder_30_mins_early
import opentasks.composeapp.generated.resources.reminder_3_days_early
import opentasks.composeapp.generated.resources.reminder_5_mins_early
import opentasks.composeapp.generated.resources.reminder_at_the_end
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
import opentasks.composeapp.generated.resources.task_completed
import opentasks.composeapp.generated.resources.thu
import opentasks.composeapp.generated.resources.time
import opentasks.composeapp.generated.resources.title_hint
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.tue
import opentasks.composeapp.generated.resources.undo
import opentasks.composeapp.generated.resources.url_hint
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

internal enum class ReminderOption(val labelRes: StringResource, val minutesValue: Int) {
    NONE(Res.string.none, Int.MIN_VALUE),
    ON_TIME(Res.string.reminder_on_time, 0),
    FIVE_MINS_BEFORE(Res.string.reminder_5_mins_early, 5),
    THIRTY_MINS_BEFORE(Res.string.reminder_30_mins_early, 30),
    ONE_HOUR_BEFORE(Res.string.reminder_1_hour_early, 60),
    ONE_DAY_BEFORE(Res.string.reminder_1_day_early, 1440),
    TWO_DAYS_BEFORE(Res.string.reminder_2_days_early, 2880),
    THREE_DAYS_BEFORE(Res.string.reminder_3_days_early, 4320),
    ONE_WEEK_BEFORE(Res.string.reminder_1_week_early, 10080),
    AT_THE_END(Res.string.reminder_at_the_end, -1),
}

private fun Set<ReminderOption>.toRemindersString(): String =
    filter { it != ReminderOption.NONE }
        .joinToString(",") { it.minutesValue.toString() }

private fun String.toReminderSet(): Set<ReminderOption> {
    if (isBlank()) return emptySet()
    val values = split(",").mapNotNull { it.trim().toIntOrNull() }
    return ReminderOption.entries
        .filter { it != ReminderOption.NONE && it.minutesValue in values }
        .toSet()
}

@Composable
fun CreateTaskScreen(
    onBack: () -> Unit,
    initialPriority: TaskPriority = TaskPriority.HIGH,
    initialCategoryId: String = "00000000-0000-0000-0000-000000000001",
    initialTitle: String = "",
    initialDay: Int = 0,
    initialMonth: Int = 0,
    initialYear: Int = 0,
    editTask: Task? = null,
    categories: List<Category> = emptyList(),
    onAddCategory: (String) -> Unit = {},
    onSave: (TaskFormData) -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    CreateTaskContent(
        onBack = onBack,
        initialPriority = initialPriority,
        initialCategoryId = initialCategoryId,
        initialTitle = initialTitle,
        initialDay = initialDay,
        initialMonth = initialMonth,
        initialYear = initialYear,
        editTask = editTask,
        categories = categories,
        onAddCategory = onAddCategory,
        onSave = onSave,
        onDelete = onDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskContent(
    onBack: () -> Unit,
    initialPriority: TaskPriority = TaskPriority.HIGH,
    initialCategoryId: String = "00000000-0000-0000-0000-000000000001",
    initialTitle: String = "",
    initialDay: Int = 0,
    initialMonth: Int = 0,
    initialYear: Int = 0,
    editTask: Task? = null,
    categories: List<Category> = emptyList(),
    onAddCategory: (String) -> Unit = {},
    onSave: (TaskFormData) -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    val stateKey = editTask?.id ?: ""
    var title by remember(stateKey) { mutableStateOf(editTask?.title ?: initialTitle) }
    var description by remember(stateKey) { mutableStateOf(editTask?.content ?: "") }
    var priority by remember(stateKey) { mutableStateOf(editTask?.priority ?: initialPriority) }
    var isCompleted by remember(stateKey) { mutableStateOf(editTask?.isCompleted ?: false) }
    var selectedCategoryId by remember(stateKey) { mutableStateOf(editTask?.categoryId ?: initialCategoryId) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    var isSubtaskMode by remember(stateKey) { mutableStateOf(false) }
    var subtaskToggleCount by remember { mutableIntStateOf(0) }
    val subtasks = remember { mutableStateListOf<SubtaskItem>() }
    var location by remember(stateKey) { mutableStateOf(editTask?.location ?: "") }
    var taskUrl by remember(stateKey) { mutableStateOf(editTask?.url ?: "") }
    var organizer by remember(stateKey) { mutableStateOf(editTask?.organizer ?: "") }
    var eventStatus by remember(stateKey) { mutableStateOf(editTask?.eventStatus ?: "") }
    var attendees by remember(stateKey) { mutableStateOf(editTask?.attendees ?: "") }
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
    var selectedReminders by remember(stateKey) {
        val initial = if ((editTask?.durationReminders ?: "").isNotBlank()) {
            editTask?.durationReminders?.toReminderSet() ?: emptySet()
        } else {
            editTask?.dateReminders?.toReminderSet() ?: emptySet()
        }
        mutableStateOf(initial.ifEmpty { setOf(ReminderOption.ON_TIME) })
    }
    var selectedRecurrence by remember(stateKey) { mutableStateOf(editTask?.recurrenceType ?: RecurrenceType.NONE) }
    var durationReminders by remember(stateKey) { mutableStateOf(editTask?.durationReminders ?: "") }
    var endHour by remember(stateKey) {
        mutableIntStateOf(editTask?.endDeadline?.let { extractHour(it) } ?: -1)
    }
    var endMinute by remember(stateKey) {
        mutableIntStateOf(editTask?.endDeadline?.let { extractMinute(it) } ?: 0)
    }
    var isAllDay by remember(stateKey) {
        mutableStateOf(editTask?.isAllDay ?: false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        CreateTaskTopBar(
            listName = categories.find { it.id == selectedCategoryId }?.name ?: "Inbox",
            priority = priority,
            showPriorityMenu = showPriorityMenu,
            onShowPriorityMenu = { showPriorityMenu = it },
            onPrioritySelected = {
                priority = it
                showPriorityMenu = false
            },
            onBack = onBack,
            onListClick = { showCategoryPicker = true },
            onDelete = onDelete,
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
                endHour = endHour,
                endMinute = endMinute,
                isAllDay = isAllDay,
                durationReminders = durationReminders,
                selectedReminders = selectedReminders,
                selectedRecurrence = selectedRecurrence,
                isCompleted = isCompleted,
                onToggleComplete = { isCompleted = !isCompleted },
                onClick = { showDateSheet = true },
            )

            Spacer(Modifier.height(dimens.spacerLarge))

            TaskTitleField(
                title = title,
                onTitleChange = { title = it },
                onFocused = { },
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
                    onFocused = { },
                    modifier = Modifier.weight(1f),
                )
            }

        }

        // ── Detail Fields (outside weighted Column so no nested scroll conflict) ──
        val openInMaps = rememberOpenInMapsAction()
        var showDetails by remember {
            mutableStateOf(
                location.isNotBlank() || taskUrl.isNotBlank() || organizer.isNotBlank() ||
                    eventStatus.isNotBlank() || attendees.isNotBlank()
            )
        }

        TaskDetailFields(
            showDetails = showDetails,
            onToggleDetails = { showDetails = !showDetails },
            location = location,
            onLocationChange = { location = it },
            onOpenInMaps = { openInMaps(location) },
            taskUrl = taskUrl,
            onUrlChange = { taskUrl = it },
            organizer = organizer,
            onOrganizerChange = { organizer = it },
            eventStatus = eventStatus,
            onStatusChange = { eventStatus = it },
            attendees = attendees,
            onAttendeesChange = { attendees = it },
            modifier = Modifier.padding(horizontal = dimens.paddingXLarge),
        )

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
                    if (isSubtaskMode) syncSubtasksToDescription()
                    onSave(
                        TaskFormData(
                            title = title,
                            content = description,
                            priority = priority,
                            deadline = deadlineMs,
                            endDeadline = if (endHour >= 0 && selectedDay > 0) computeDeadlineMillis(selectedYear, selectedMonth, selectedDay, endHour, endMinute) else null,
                            isAllDay = isAllDay,
                            recurrence = selectedRecurrence,
                            categoryId = selectedCategoryId,
                            isCompleted = isCompleted,
                            location = location,
                            url = taskUrl,
                            organizer = organizer,
                            eventStatus = eventStatus,
                            attendees = attendees,
                            durationReminders = durationReminders,
                            dateReminders = if (durationReminders.isBlank()) selectedReminders.toRemindersString() else "",
                        )
                    )
                }
                onBack()
            },
        )
    }

    if (showDateSheet) {
        DateReminderBottomSheet(
            selectedDay = selectedDay,
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            selectedReminders = selectedReminders,
            selectedRecurrence = selectedRecurrence,
            initialDurationReminders = durationReminders,
            initialEndHour = endHour,
            initialEndMinute = endMinute,
            initialIsAllDay = isAllDay,
            initialTab = if (durationReminders.isNotBlank()) 1 else 0,
            onDaySelected = { day, month, year ->
                selectedDay = day
                selectedMonth = month
                selectedYear = year
            },
            onTimeSelected = { hour, minute ->
                selectedHour = hour
                selectedMinute = minute
            },
            onEndTimeSelected = { hour, minute ->
                endHour = hour
                endMinute = minute
            },
            onAllDayChanged = { isAllDay = it },
            onRemindersSelected = { selectedReminders = it },
            onRecurrenceSelected = { selectedRecurrence = it },
            onDurationRemindersChanged = { durationReminders = it },
            onDismiss = { showDateSheet = false },
            onConfirm = { showDateSheet = false },
        )
    }

    if (showCategoryPicker) {
        val listPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        CategoryPickerBottomSheet(
            sheetState = listPickerState,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { category ->
                selectedCategoryId = category.id
                showCategoryPicker = false
            },
            onAddCategory = onAddCategory,
            onDismiss = { showCategoryPicker = false },
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
internal fun CreateTaskTopBar(
    listName: String = "Inbox",
    priority: TaskPriority,
    showPriorityMenu: Boolean,
    onShowPriorityMenu: (Boolean) -> Unit,
    onPrioritySelected: (TaskPriority) -> Unit,
    onBack: () -> Unit,
    onListClick: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
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
            if (onDelete != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete_task_title)) },
            text = { Text(stringResource(Res.string.delete_task_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun PriorityDropdown(
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
internal fun DateReminderRow(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    selectedHour: Int,
    selectedMinute: Int,
    endHour: Int = -1,
    endMinute: Int = 0,
    isAllDay: Boolean = false,
    durationReminders: String = "",
    selectedReminders: Set<ReminderOption>,
    selectedRecurrence: RecurrenceType,
    isCompleted: Boolean,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val hasDate = selectedDay > 0 && selectedMonth > 0 && selectedYear > 0
    val hasTime = selectedHour >= 0
    val hasReminder = selectedReminders.isNotEmpty()
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
                    .then(
                        if (isCompleted) {
                            Modifier.background(PriorityHigh, RoundedCornerShape(dimens.cornerMedium))
                        } else {
                            Modifier.border(dimens.priorityIndicatorBorder, PriorityHigh, RoundedCornerShape(dimens.cornerMedium))
                        }
                    )
                    .clickable(onClick = onToggleComplete),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = stringResource(Res.string.task_completed),
                        tint = Color.White,
                        modifier = Modifier.size(dimens.iconSmall),
                    )
                }
            }
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
                val hasDuration = durationReminders.isNotBlank()
                val dateText = buildString {
                    append("$dowName, $monthShort $selectedDay")
                    if (isAllDay) {
                        append(" · ")
                        append(stringResource(Res.string.all_day))
                    } else if (hasDuration && endHour >= 0) {
                        append(", ")
                        append(formatTime(selectedHour, selectedMinute).replace(" ", ""))
                        append(" - ")
                        append(formatTime(endHour, endMinute).replace(" ", ""))
                    } else if (hasTime) {
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
internal fun CreateTaskBottomBar(
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        // Duration tab — propagate duration date back to parent
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
                reminderLabel,
                color = if (hasReminders) PrimaryBlue
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasReminders) {
                Spacer(Modifier.width(dimens.spacerSmall))
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.clear_reminder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(dimens.iconSmall)
                        .clickable(onClick = onClearReminders),
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

@Composable
internal fun TaskDetailFields(
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    onOpenInMaps: () -> Unit,
    taskUrl: String,
    onUrlChange: (String) -> Unit,
    organizer: String,
    onOrganizerChange: (String) -> Unit,
    eventStatus: String,
    onStatusChange: (String) -> Unit,
    attendees: String,
    onAttendeesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens

    Column(modifier = modifier.fillMaxWidth()) {
        // Toggle button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleDetails)
                .padding(vertical = dimens.paddingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (showDetails) Res.drawable.ic_dropdown else Res.drawable.ic_chevron_right
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.iconDefault),
            )
            Spacer(Modifier.width(dimens.spacerSmall))
            Text(
                text = stringResource(Res.string.more_details),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = showDetails) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacerMedium),
            ) {
                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = onLocationChange,
                    placeholder = { Text(stringResource(Res.string.location_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_location_on),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    trailingIcon = {
                        if (location.isNotBlank()) {
                            IconButton(onClick = onOpenInMaps) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_open_in_new),
                                    contentDescription = stringResource(Res.string.open_in_maps),
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(dimens.iconDefault),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // URL
                OutlinedTextField(
                    value = taskUrl,
                    onValueChange = onUrlChange,
                    placeholder = { Text(stringResource(Res.string.url_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_link),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Organizer
                OutlinedTextField(
                    value = organizer,
                    onValueChange = onOrganizerChange,
                    placeholder = { Text(stringResource(Res.string.organizer_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_person),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Status
                OutlinedTextField(
                    value = eventStatus,
                    onValueChange = onStatusChange,
                    placeholder = { Text(stringResource(Res.string.event_status_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_info),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Attendees
                OutlinedTextField(
                    value = attendees,
                    onValueChange = onAttendeesChange,
                    placeholder = { Text(stringResource(Res.string.attendees_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_group),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(dimens.spacerSmall))
            }
        }
    }
}

private fun computeDeadlineMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int
): Long = computeLocalMillis(year, month, day, hour, minute)

