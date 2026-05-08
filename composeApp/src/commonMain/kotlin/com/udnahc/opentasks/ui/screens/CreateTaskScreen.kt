package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.priorityColor
import com.udnahc.opentasks.ui.util.rememberOpenInMapsAction
import com.mohamedrejeb.richeditor.model.RichTextState
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.all_day
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.date_and_reminder
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.delete_task_message
import opentasks.composeapp.generated.resources.delete_task_title
import opentasks.composeapp.generated.resources.description_hint
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.ic_alarm
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_delete
import opentasks.composeapp.generated.resources.ic_flag
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_repeat
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.priority
import opentasks.composeapp.generated.resources.select
import opentasks.composeapp.generated.resources.subtasks
import opentasks.composeapp.generated.resources.task_completed
import opentasks.composeapp.generated.resources.title_hint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    filteredCategories: List<Category> = categories,
    categorySearchQuery: String = "",
    onCategorySearchQueryChange: (String) -> Unit = {},
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
        filteredCategories = filteredCategories,
        categorySearchQuery = categorySearchQuery,
        onCategorySearchQueryChange = onCategorySearchQueryChange,
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
    filteredCategories: List<Category> = categories,
    categorySearchQuery: String = "",
    onCategorySearchQueryChange: (String) -> Unit = {},
    onAddCategory: (String) -> Unit = {},
    onSave: (TaskFormData) -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    val stateKey = editTask?.id ?: ""
    var title by remember(stateKey) { mutableStateOf(editTask?.title ?: initialTitle) }
    var description by remember(stateKey) { mutableStateOf(editTask?.content ?: "") }
    var priority by remember(stateKey) { mutableStateOf(editTask?.priority ?: initialPriority) }
    var isCompleted by remember(stateKey) { mutableStateOf(editTask?.status == TaskStatus.DONE) }
    var selectedCategoryId by remember(stateKey) { mutableStateOf(editTask?.categoryId ?: initialCategoryId) }
    var section by remember(stateKey) { mutableStateOf(editTask?.section ?: "") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    var isSubtaskMode by remember(stateKey) { mutableStateOf(editTask?.subtasks?.isNotBlank() == true) }
    var subtaskToggleCount by remember { mutableIntStateOf(0) }
    val subtasks = remember(stateKey) { mutableStateListOf<SubtaskItem>() }
    var location by remember(stateKey) { mutableStateOf(editTask?.location ?: "") }
    var taskUrl by remember(stateKey) { mutableStateOf(editTask?.url ?: "") }
    var organizer by remember(stateKey) { mutableStateOf(editTask?.organizer ?: "") }
    var eventStatus by remember(stateKey) { mutableStateOf(editTask?.eventStatus ?: "") }
    var attendees by remember(stateKey) { mutableStateOf(editTask?.attendees ?: "") }
    val descriptionFocusRequester = remember { FocusRequester() }
    val subtaskFocusRequester = remember { FocusRequester() }
    val richTextState = rememberRichTextState()
    val inboxName = stringResource(Res.string.inbox)

    LaunchedEffect(stateKey) {
        if (editTask != null && editTask.content.isNotBlank()) {
            richTextState.setHtml(editTask.content)
        }
        subtasks.clear()
        subtasks.addAll(editTask?.subtasks?.toSubtaskItems().orEmpty())
        if (isSubtaskMode && subtasks.isEmpty()) {
            subtasks.add(SubtaskItem())
        }
    }

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
            listName = categories.find { it.id == selectedCategoryId }?.name ?: inboxName,
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
                        onSubtaskTextChange = { id, text ->
                            val index = subtasks.indexOfFirst { it.id == id }
                            if (index >= 0) subtasks[index] = subtasks[index].copy(text = text)
                        },
                        onSubtaskCheckedChange = { id, checked ->
                            val index = subtasks.indexOfFirst { it.id == id }
                            if (index >= 0) subtasks[index] = subtasks[index].copy(isChecked = checked)
                        },
                        onDeleteSubtask = { id ->
                            val index = subtasks.indexOfFirst { it.id == id }
                            if (index >= 0) subtasks.removeAt(index)
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
                TaskRichDescriptionField(
                    richTextState = richTextState,
                    focusRequester = descriptionFocusRequester,
                    onFocused = { },
                    modifier = Modifier.weight(1f),
                )
            }

        }

        // Formatting toolbar -- only visible when NOT in subtask mode
        if (!isSubtaskMode) {
            FormattingToolbar(
                richTextState = richTextState,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Detail Fields (outside weighted Column so no nested scroll conflict)
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
            section = section,
            onSectionChange = { section = it },
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
                    // Switching from subtask mode to rich text mode
                    syncSubtasksToDescription()
                    richTextState.setHtml(description)
                } else {
                    // Switching from rich text mode to subtask mode
                    description = richTextState.annotatedString.text
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
                    val contentToSave = if (isSubtaskMode) description else richTextState.toHtml()
                    val subtasksToSave = if (isSubtaskMode) subtasks.toSubtasksJson() else ""
                    onSave(
                        TaskFormData(
                            title = title,
                            content = contentToSave,
                            subtasks = subtasksToSave,
                            priority = priority,
                            deadline = deadlineMs,
                            endDeadline = if (endHour >= 0 && selectedDay > 0) computeDeadlineMillis(selectedYear, selectedMonth, selectedDay, endHour, endMinute) else null,
                            isAllDay = isAllDay,
                            recurrence = selectedRecurrence,
                            categoryId = selectedCategoryId,
                            section = section.takeIf { it.isNotBlank() },
                            status = if (isCompleted) TaskStatus.DONE else TaskStatus.TODO,
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
            categories = filteredCategories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { category ->
                selectedCategoryId = category.id
                onCategorySearchQueryChange("")
                showCategoryPicker = false
            },
            onAddCategory = onAddCategory,
            onDismiss = {
                onCategorySearchQueryChange("")
                showCategoryPicker = false
            },
            searchQuery = categorySearchQuery,
            onSearchQueryChange = onCategorySearchQueryChange,
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
private fun TaskRichDescriptionField(
    richTextState: RichTextState,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    BasicRichTextEditor(
        state = richTextState,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        cursorBrush = SolidColor(PrimaryBlue),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.minPagerHeight)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateTaskTopBar(
    listName: String,
    priority: TaskPriority,
    showPriorityMenu: Boolean,
    onShowPriorityMenu: (Boolean) -> Unit,
    onPrioritySelected: (TaskPriority) -> Unit,
    onBack: () -> Unit,
    onListClick: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    OpenTasksTopBar(
        containerStyle = OpenTasksTopBarContainerStyle.Transparent,
        navigationIcon = {
            OpenTasksBackButton(onClick = onBack)
        },
        titleContent = {
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
        // No date selected -- show placeholder row
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
        // Date selected -- show formatted date/time/reminder/repeat
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
        IconButton(onClick = onToggleSubtaskMode) {
            Icon(
                painter = painterResource(Res.drawable.ic_list),
                contentDescription = stringResource(Res.string.subtasks),
                tint = if (isSubtaskMode) PrimaryBlue
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onDone) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = stringResource(Res.string.done),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
