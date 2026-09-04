package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.udnahc.opentasks.ExternalLaunchResult
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SubtaskItem
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.model.toSubtaskItems
import com.udnahc.opentasks.data.model.toSubtasksJson
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.minimumInteractiveTargetSize
import com.udnahc.opentasks.ui.theme.priorityColor
import com.udnahc.opentasks.ui.util.PlatformBackHandler
import com.udnahc.opentasks.ui.util.rememberTaskImagePickerActions
import com.udnahc.opentasks.ui.util.rememberOpenInMapsAction
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.all_day
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.date_and_reminder
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.discard_pending_images_message
import opentasks.composeapp.generated.resources.discard_pending_images_title
import opentasks.composeapp.generated.resources.discard
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
import opentasks.composeapp.generated.resources.image_add_failed
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.loading
import opentasks.composeapp.generated.resources.priority
import opentasks.composeapp.generated.resources.ok
import opentasks.composeapp.generated.resources.select
import opentasks.composeapp.generated.resources.subtasks
import opentasks.composeapp.generated.resources.title_hint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.LocalDate
import opentasks.composeapp.generated.resources.weekly_with_day

private fun <T : Enum<T>> enumStateSaver(
    entries: List<T>,
    fallback: T,
): Saver<MutableState<T>, String> = Saver(
    save = { state -> state.value.name },
    restore = { name ->
        mutableStateOf(entries.firstOrNull { it.name == name } ?: fallback)
    },
)

private val reminderStateSaver: Saver<MutableState<Set<ReminderOption>>, String> = Saver(
    save = { state -> state.value.toRemindersString() },
    restore = { serialized -> mutableStateOf(serialized.toReminderSet()) },
)

private val subtaskListSaver = listSaver<SnapshotStateList<SubtaskItem>, Any>(
    save = { items ->
        buildList(items.size * SUBTASK_SAVED_FIELD_COUNT) {
            items.forEach { item ->
                add(item.id)
                add(item.text)
                add(item.isChecked)
            }
        }
    },
    restore = { values ->
        if (values.size % SUBTASK_SAVED_FIELD_COUNT != 0) {
            return@listSaver mutableStateListOf()
        }
        val restored = mutableStateListOf<SubtaskItem>()
        for (index in values.indices step SUBTASK_SAVED_FIELD_COUNT) {
            val id = values[index] as? String ?: return@listSaver mutableStateListOf()
            val text = values[index + 1] as? String ?: return@listSaver mutableStateListOf()
            val isChecked = values[index + 2] as? Boolean
                ?: return@listSaver mutableStateListOf()
            restored.add(SubtaskItem(id = id, text = text, isChecked = isChecked))
        }
        restored
    },
)

private const val SUBTASK_SAVED_FIELD_COUNT = 3

@Composable
fun CreateTaskScreen(
    onBack: () -> Unit,
    currentDate: LocalDate,
    initialPriority: TaskPriority = TaskPriority.HIGH,
    initialCategoryId: String = AppConstants.DEFAULT_INBOX_ID,
    initialTitle: String = "",
    initialDescription: String = "",
    initialUrl: String = "",
    initialDay: Int = 0,
    initialMonth: Int = 0,
    initialYear: Int = 0,
    editTask: Task? = null,
    retainedFormData: TaskFormData? = null,
    categories: List<Category> = emptyList(),
    filteredCategories: List<Category> = categories,
    categorySearchQuery: String = "",
    onCategorySearchQueryChange: (String) -> Unit = {},
    onAddCategory: (String) -> Unit = {},
    onSave: (TaskFormData) -> Unit = {},
    isSaving: Boolean = false,
    existingImages: List<Attachment> = emptyList(),
    pendingImages: List<PickedImage> = emptyList(),
    onAddPendingImage: (PickedImage) -> Unit = {},
    onRemovePendingImage: (PickedImage) -> Unit = {},
    onDiscardPendingImages: () -> Unit = {},
    confirmDiscardPendingImagesOnBack: Boolean = false,
    onBackRequestChanged: (owner: Any, onBack: (() -> Unit)?) -> Unit = { _, _ -> },
    onRemoveTaskImage: (Attachment) -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onExternalLaunchFailure: () -> Unit = {},
) {
    CreateTaskContent(
        onBack = onBack,
        currentDate = currentDate,
        initialPriority = initialPriority,
        initialCategoryId = initialCategoryId,
        initialTitle = initialTitle,
        initialDescription = initialDescription,
        initialUrl = initialUrl,
        initialDay = initialDay,
        initialMonth = initialMonth,
        initialYear = initialYear,
        editTask = editTask,
        retainedFormData = retainedFormData,
        categories = categories,
        filteredCategories = filteredCategories,
        categorySearchQuery = categorySearchQuery,
        onCategorySearchQueryChange = onCategorySearchQueryChange,
        onAddCategory = onAddCategory,
        onSave = onSave,
        isSaving = isSaving,
        existingImages = existingImages,
        pendingImages = pendingImages,
        onAddPendingImage = onAddPendingImage,
        onRemovePendingImage = onRemovePendingImage,
        onDiscardPendingImages = onDiscardPendingImages,
        confirmDiscardPendingImagesOnBack = confirmDiscardPendingImagesOnBack,
        onBackRequestChanged = onBackRequestChanged,
        onRemoveTaskImage = onRemoveTaskImage,
        onDelete = onDelete,
        onExternalLaunchFailure = onExternalLaunchFailure,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskContent(
    onBack: () -> Unit,
    currentDate: LocalDate,
    initialPriority: TaskPriority = TaskPriority.HIGH,
    initialCategoryId: String = AppConstants.DEFAULT_INBOX_ID,
    initialTitle: String = "",
    initialDescription: String = "",
    initialUrl: String = "",
    initialDay: Int = 0,
    initialMonth: Int = 0,
    initialYear: Int = 0,
    editTask: Task? = null,
    retainedFormData: TaskFormData? = null,
    categories: List<Category> = emptyList(),
    filteredCategories: List<Category> = categories,
    categorySearchQuery: String = "",
    onCategorySearchQueryChange: (String) -> Unit = {},
    onAddCategory: (String) -> Unit = {},
    onSave: (TaskFormData) -> Unit = {},
    isSaving: Boolean = false,
    existingImages: List<Attachment> = emptyList(),
    pendingImages: List<PickedImage> = emptyList(),
    onAddPendingImage: (PickedImage) -> Unit = {},
    onRemovePendingImage: (PickedImage) -> Unit = {},
    onDiscardPendingImages: () -> Unit = {},
    confirmDiscardPendingImagesOnBack: Boolean = false,
    onBackRequestChanged: (owner: Any, onBack: (() -> Unit)?) -> Unit = { _, _ -> },
    onRemoveTaskImage: (Attachment) -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onExternalLaunchFailure: () -> Unit = {},
) {
    val stateKey = editTask?.id ?: listOf(
        initialPriority.name,
        initialCategoryId,
        initialTitle,
        initialDescription,
        initialUrl,
        initialDay.toString(),
        initialMonth.toString(),
        initialYear.toString(),
    ).joinToString("|")
    val seededFormData = retainedFormData ?: editTask?.toTaskFormData()
    val initialPriorityValue = seededFormData?.priority ?: initialPriority
    val initialRecurrenceValue = seededFormData?.recurrence ?: RecurrenceType.NONE
    val initialContent = seededFormData?.content ?: initialDescription
    var title by rememberSaveable(stateKey) { mutableStateOf(seededFormData?.title ?: initialTitle) }
    var description by rememberSaveable(stateKey) { mutableStateOf(initialContent) }
    var priority by rememberSaveable(
        stateKey,
        saver = enumStateSaver(TaskPriority.entries, initialPriorityValue),
    ) {
        mutableStateOf(initialPriorityValue)
    }
    var isCompleted by rememberSaveable(stateKey) {
        mutableStateOf(seededFormData?.status == TaskStatus.DONE)
    }
    var selectedCategoryId by rememberSaveable(stateKey) {
        mutableStateOf(
            seededFormData?.categoryId ?: initialCategoryId
        )
    }
    var section by rememberSaveable(stateKey) { mutableStateOf(seededFormData?.section ?: "") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    var isSubtaskMode by rememberSaveable(stateKey) {
        mutableStateOf(seededFormData?.subtasks?.isNotBlank() == true)
    }
    var subtaskToggleCount by remember { mutableIntStateOf(0) }
    val subtasks = rememberSaveable(stateKey, saver = subtaskListSaver) {
        mutableStateListOf<SubtaskItem>().apply {
            addAll(seededFormData?.subtasks?.toSubtaskItems().orEmpty())
            if (isSubtaskMode && isEmpty()) add(SubtaskItem())
        }
    }
    var location by rememberSaveable(stateKey) { mutableStateOf(seededFormData?.location ?: "") }
    var taskUrl by rememberSaveable(stateKey) {
        mutableStateOf(seededFormData?.url ?: initialUrl)
    }
    var organizer by rememberSaveable(stateKey) { mutableStateOf(seededFormData?.organizer ?: "") }
    var eventStatus by rememberSaveable(stateKey) { mutableStateOf(seededFormData?.eventStatus ?: "") }
    var attendees by rememberSaveable(stateKey) { mutableStateOf(seededFormData?.attendees ?: "") }
    var showDiscardPendingImagesConfirm by remember { mutableStateOf(false) }
    val descriptionFocusRequester = remember { FocusRequester() }
    val subtaskFocusRequester = remember { FocusRequester() }
    val richTextState = rememberSaveable(stateKey, saver = RichTextState.Saver) {
        RichTextState().apply {
            if (initialContent.isNotBlank()) setHtml(initialContent)
        }
    }
    val backHandlerOwner = remember(stateKey) { Any() }
    val inboxName = stringResource(Res.string.inbox)
    val selectedCategoryName = remember(categories, selectedCategoryId, inboxName) {
        categories.find { it.id == selectedCategoryId }?.name ?: inboxName
    }
    var imageError by remember { mutableStateOf(false) }
    val imagePickerActions = rememberTaskImagePickerActions(
        onImagePicked = onAddPendingImage,
        onError = { code ->
            if (!code.endsWith("_unavailable")) imageError = true
        },
    )
    fun requestBack() {
        if (confirmDiscardPendingImagesOnBack && pendingImages.isNotEmpty()) {
            showDiscardPendingImagesConfirm = true
        } else {
            onBack()
        }
    }
    val currentBackRequest by rememberUpdatedState { requestBack() }
    DisposableEffect(backHandlerOwner, confirmDiscardPendingImagesOnBack, onBackRequestChanged) {
        if (confirmDiscardPendingImagesOnBack) {
            onBackRequestChanged(backHandlerOwner) { currentBackRequest() }
        }
        onDispose {
            onBackRequestChanged(backHandlerOwner, null)
        }
    }

    PlatformBackHandler(
        enabled = confirmDiscardPendingImagesOnBack && pendingImages.isNotEmpty(),
        onBack = { requestBack() },
    )

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
    var selectedDay by rememberSaveable(stateKey) {
        mutableIntStateOf(seededFormData?.deadline?.let {
            extractDay(
                it
            )
        } ?: initialDay)
    }
    var selectedMonth by rememberSaveable(stateKey) {
        mutableIntStateOf(seededFormData?.deadline?.let {
            extractMonth(
                it
            )
        } ?: initialMonth)
    }
    var selectedYear by rememberSaveable(stateKey) {
        mutableIntStateOf(seededFormData?.deadline?.let {
            extractYear(
                it
            )
        } ?: initialYear)
    }
    var selectedHour by rememberSaveable(stateKey) {
        mutableIntStateOf(seededFormData?.deadline?.let {
            extractHour(
                it
            )
        } ?: 8)
    }
    var selectedMinute by rememberSaveable(stateKey) {
        mutableIntStateOf(seededFormData?.deadline?.let {
            extractMinute(
                it
            )
        } ?: 0)
    }
    var selectedReminders by rememberSaveable(stateKey, saver = reminderStateSaver) {
        val initial = if (seededFormData?.durationReminders?.isNotBlank() == true) {
            seededFormData.durationReminders.toReminderSet()
        } else {
            seededFormData?.dateReminders?.toReminderSet() ?: emptySet()
        }
        mutableStateOf(initial.ifEmpty { setOf(ReminderOption.ON_TIME) })
    }
    var selectedRecurrence by rememberSaveable(
        stateKey,
        saver = enumStateSaver(RecurrenceType.entries, initialRecurrenceValue),
    ) {
        mutableStateOf(initialRecurrenceValue)
    }
    val reminderDays = rememberSaveable(stateKey) { seededFormData?.reminderDays ?: 0 }
    var durationReminders by rememberSaveable(stateKey) {
        mutableStateOf(
            seededFormData?.durationReminders ?: ""
        )
    }
    var endHour by rememberSaveable(stateKey) {
        mutableIntStateOf(seededFormData?.endDeadline?.let { extractHour(it) } ?: -1)
    }
    var endMinute by rememberSaveable(stateKey) {
        mutableIntStateOf(seededFormData?.endDeadline?.let { extractMinute(it) } ?: 0)
    }
    var isAllDay by rememberSaveable(stateKey) {
        mutableStateOf(seededFormData?.isAllDay ?: false)
    }

    fun toggleSubtaskMode() {
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
    }

    fun saveTask() {
        if (!isSaving && title.isNotBlank()) {
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
                    endDeadline = if (endHour >= 0 && selectedDay > 0) computeDeadlineMillis(
                        selectedYear,
                        selectedMonth,
                        selectedDay,
                        endHour,
                        endMinute
                    ) else null,
                    isAllDay = isAllDay,
                    reminderDays = reminderDays,
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
                    pendingImages = pendingImages,
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        CreateTaskTopBar(
            listName = selectedCategoryName,
            priority = priority,
            showPriorityMenu = showPriorityMenu,
            onShowPriorityMenu = { showPriorityMenu = it },
            onPrioritySelected = {
                priority = it
                showPriorityMenu = false
            },
            onBack = { requestBack() },
            onListClick = { showCategoryPicker = true },
            onDelete = onDelete,
            isSubtaskMode = isSubtaskMode,
            onToggleSubtaskMode = { toggleSubtaskMode() },
            onDone = { saveTask() },
            isSaving = isSaving,
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

            TaskImageEditorStrip(
                existingImages = existingImages,
                pendingImages = pendingImages,
                onAddFromGallery = imagePickerActions.pickFromGallery,
                onAddFromCamera = imagePickerActions.captureFromCamera,
                onRemoveExisting = onRemoveTaskImage,
                onRemovePending = onRemovePendingImage,
            )

            Spacer(Modifier.height(dimens.spacerLarge))

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
                            if (index >= 0) subtasks[index] =
                                subtasks[index].copy(isChecked = checked)
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
            onOpenInMaps = {
                if (openInMaps(location) == ExternalLaunchResult.FAILURE) {
                    onExternalLaunchFailure()
                }
            },
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

    }

    if (isSaving) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(OpenTasksTheme.dimens.cornerMedium),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(OpenTasksTheme.dimens.paddingLarge),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(OpenTasksTheme.dimens.iconDefault))
                    Spacer(Modifier.width(OpenTasksTheme.dimens.spacerLarge))
                    Text(
                        text = stringResource(Res.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    if (showDateSheet) {
        DateReminderBottomSheet(
            currentDate = currentDate,
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

    if (imageError) {
        AlertDialog(
            onDismissRequest = { imageError = false },
            title = { Text(stringResource(Res.string.image_add_failed)) },
            confirmButton = {
                TextButton(onClick = { imageError = false }) {
                    Text(stringResource(Res.string.ok))
                }
            },
        )
    }

    if (showDiscardPendingImagesConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardPendingImagesConfirm = false },
            title = { Text(stringResource(Res.string.discard_pending_images_title)) },
            text = { Text(stringResource(Res.string.discard_pending_images_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardPendingImagesConfirm = false
                    onDiscardPendingImages()
                    onBack()
                }) {
                    Text(stringResource(Res.string.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardPendingImagesConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

internal fun Task.toTaskFormData(): TaskFormData = TaskFormData(
    title = title,
    content = content,
    subtasks = subtasks,
    priority = priority,
    deadline = deadline,
    endDeadline = endDeadline,
    isAllDay = isAllDay,
    reminderDays = notifyBeforeValue,
    recurrence = recurrenceType,
    categoryId = categoryId,
    section = section,
    status = status,
    location = location,
    url = url,
    organizer = organizer,
    eventStatus = eventStatus,
    attendees = attendees,
    durationReminders = durationReminders,
    dateReminders = dateReminders,
)

@Composable
private fun TaskTitleField(
    title: String,
    onTitleChange: (String) -> Unit,
    onFocused: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    BasicTextField(
        value = title,
        onValueChange = onTitleChange,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
        ),
        cursorBrush = SolidColor(PrimaryBlue),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(dimens.cornerMedium),
                    )
                    .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingMedium),
            ) {
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.minPagerHeight)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(dimens.cornerMedium),
            )
            .padding(dimens.paddingLarge),
    ) {
        if (richTextState.annotatedString.text.isEmpty()) {
            Text(
                text = stringResource(Res.string.description_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        BasicRichTextEditor(
            state = richTextState,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(PrimaryBlue),
            modifier = Modifier
                .fillMaxSize()
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
    listName: String,
    priority: TaskPriority,
    showPriorityMenu: Boolean,
    onShowPriorityMenu: (Boolean) -> Unit,
    onPrioritySelected: (TaskPriority) -> Unit,
    onBack: () -> Unit,
    onListClick: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    isSubtaskMode: Boolean,
    onToggleSubtaskMode: () -> Unit,
    onDone: () -> Unit,
    isSaving: Boolean = false,
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
                modifier = Modifier
                    .clickable(onClick = onListClick)
                    .minimumInteractiveTargetSize(),
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
            IconButton(onClick = onToggleSubtaskMode) {
                Icon(
                    painter = painterResource(Res.drawable.ic_list),
                    contentDescription = stringResource(Res.string.subtasks),
                    tint = if (isSubtaskMode) PrimaryBlue
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDone, enabled = !isSaving) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = stringResource(Res.string.done),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                .minimumInteractiveTargetSize()
                .padding(vertical = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskSquareCompletionButton(
                isChecked = isCompleted,
                onClick = onToggleComplete,
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.priorityIndicatorSize)
                        .then(
                            if (isCompleted) {
                                Modifier.background(
                                    PriorityHigh,
                                    RoundedCornerShape(dimens.cornerMedium)
                                )
                            } else {
                                Modifier.border(
                                    dimens.priorityIndicatorBorder,
                                    PriorityHigh,
                                    RoundedCornerShape(dimens.cornerMedium)
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(dimens.iconSmall),
                        )
                    }
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
                .minimumInteractiveTargetSize()
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
                    // Localized weekly labels include the selected day.
                    val fdow = dayOfWeekIndex(selectedYear, selectedMonth, 1)
                    val dowName = dayOfWeekName(fdow, selectedDay)
                    val recText = if (selectedRecurrence == RecurrenceType.WEEKLY) {
                        stringResource(Res.string.weekly_with_day, dowName)
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
