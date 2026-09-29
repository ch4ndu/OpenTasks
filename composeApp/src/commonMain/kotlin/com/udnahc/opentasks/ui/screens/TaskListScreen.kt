package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.priorityColor
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel.ActiveTaskListSection
import com.udnahc.opentasks.viewmodel.TaskListViewModel.SectionGroup
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.all_views
import opentasks.composeapp.generated.resources.completed
import opentasks.composeapp.generated.resources.ic_attach
import opentasks.composeapp.generated.resources.ic_chevron_left
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.image_attachment
import opentasks.composeapp.generated.resources.no_tasks
import opentasks.composeapp.generated.resources.overdue
import opentasks.composeapp.generated.resources.sort
import opentasks.composeapp.generated.resources.sort_by_deadline
import opentasks.composeapp.generated.resources.sort_by_priority
import opentasks.composeapp.generated.resources.sort_by_title
import opentasks.composeapp.generated.resources.sort_current_description
import opentasks.composeapp.generated.resources.sort_recently_updated
import opentasks.composeapp.generated.resources.task_view_board
import opentasks.composeapp.generated.resources.task_view_list
import opentasks.composeapp.generated.resources.upcoming
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onAllViewsClick: () -> Unit,
    onTaskClick: (Task) -> Unit,
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onTaskMutationFailure: () -> Unit = {},
    onModalBusyChanged: (Boolean) -> Unit = {},
) {
    val taskPendingSeriesChoice by viewModel.taskPendingSeriesChoice.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val categoryName by viewModel.categoryName.collectAsState()
    var isSortMenuBusy by remember { mutableStateOf(false) }
    ModalBusyEffect(
        taskPendingSeriesChoice != null || isSortMenuBusy,
        onModalBusyChanged,
    )
    val listName = if (viewModel.filter is TaskListFilter.Category) {
        categoryName
    } else {
        taskListFilterTitle(viewModel.filter)
    }
    val navBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    when (viewMode) {
        TaskListViewMode.LIST -> {
            val listProjection by viewModel.listProjection.collectAsState()
            val taskImageSummaries by viewModel.taskImageSummaries.collectAsState()
            val taskContentPreviews by viewModel.taskContentPreviews.collectAsState()
            val taskDueTextById by viewModel.taskDueTextById.collectAsState()
            TaskListContent(
                listName = listName,
                completedTasks = listProjection.completedTasks,
                groupedActiveTasks = listProjection.groupedActiveTasks,
                taskImageSummaries = taskImageSummaries,
                taskContentPreviews = taskContentPreviews,
                taskDueTextById = taskDueTextById,
                onTaskClick = onTaskClick,
                onToggleComplete = { viewModel.toggleComplete(it) },
                onToggleStar = { viewModel.toggleStar(it) },
                onAllViewsClick = onAllViewsClick,
                sortOption = sortOption,
                onSortOptionSelected = { viewModel.setSortOption(it) },
                viewMode = viewMode,
                onViewModeSelected = { viewModel.setViewMode(it) },
                isRefreshing = isRefreshing,
                syncEnabled = syncEnabled,
                onRefresh = onRefresh,
                onSortMenuBusyChanged = { isSortMenuBusy = it },
            )
        }

        TaskListViewMode.BOARD -> {
            val tasksByStatus by viewModel.tasksByStatus.collectAsState()
            val taskDueTextById by viewModel.boardTaskDueTextById.collectAsState()
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                TaskListTopBar(
                    listName = listName,
                    onAllViewsClick = onAllViewsClick,
                    sortOption = sortOption,
                    onSortOptionSelected = { viewModel.setSortOption(it) },
                    viewMode = viewMode,
                    onViewModeSelected = { viewModel.setViewMode(it) },
                    onSortMenuBusyChanged = { isSortMenuBusy = it },
                )
                SyncPullToRefresh(
                    isRefreshing = isRefreshing,
                    enabled = syncEnabled,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    KanbanBoardContent(
                        tasksByStatus = tasksByStatus,
                        taskDueTextById = taskDueTextById,
                        onTaskClick = onTaskClick,
                        onStatusChange = { task, newStatus ->
                            viewModel.moveTaskToStatus(
                                task,
                                newStatus
                            )
                        },
                        onToggleStar = { viewModel.toggleStar(it) },
                        topBarHeight = 0.dp,
                        navBarHeight = navBarHeight,
                    )
                }
            }
        }
    }

    if (taskPendingSeriesChoice != null) {
        CompleteSeriesDialog(
            onCompleteOccurrence = { viewModel.completeOccurrence() },
            onCompleteSeries = { viewModel.completeSeries() },
            onDismiss = { viewModel.dismissSeriesChoice() },
        )
    }

    TaskMutationFailureEffect(
        eventFlow = viewModel.taskMutationFailureEvent,
        consume = viewModel::consumeTaskMutationFailureEvent,
        onFailure = onTaskMutationFailure,
    )
}

@Composable
internal fun TaskListContent(
    listName: String,
    completedTasks: List<Task> = emptyList(),
    groupedActiveTasks: List<SectionGroup> = emptyList(),
    taskImageSummaries: Map<String, AttachmentSummary> = emptyMap(),
    taskContentPreviews: Map<String, String> = emptyMap(),
    taskDueTextById: Map<String, String> = emptyMap(),
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onToggleStar: (Task) -> Unit = {},
    onAllViewsClick: () -> Unit = {},
    sortOption: TaskSortOption = TaskSortOption.RECENTLY_UPDATED,
    onSortOptionSelected: (TaskSortOption) -> Unit = {},
    viewMode: TaskListViewMode = TaskListViewMode.LIST,
    onViewModeSelected: (TaskListViewMode) -> Unit = {},
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onSortMenuBusyChanged: (Boolean) -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    val navBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    var overdueCollapsed by rememberSaveable { mutableStateOf(false) }
    var upcomingCollapsed by rememberSaveable { mutableStateOf(false) }
    var completedCollapsed by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TaskListTopBar(
            listName = listName,
            onAllViewsClick = onAllViewsClick,
            sortOption = sortOption,
            onSortOptionSelected = onSortOptionSelected,
            viewMode = viewMode,
            onViewModeSelected = onViewModeSelected,
            onSortMenuBusyChanged = onSortMenuBusyChanged,
        )
        SyncPullToRefresh(
            isRefreshing = isRefreshing,
            enabled = syncEnabled,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            // Task list — fills entire screen, scrolls behind top bar and bottom nav
            if (groupedActiveTasks.isEmpty() && completedTasks.isEmpty()) {
                EmptyPlaceholder(
                    text = stringResource(Res.string.no_tasks),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = navBarHeight + dimens.fabAreaBottom + dimens.fabBottomPadding,
                    ),
                ) {
                    groupedActiveTasks.forEach { group ->
                        val isCollapsed = when (group.category) {
                            ActiveTaskListSection.OVERDUE -> overdueCollapsed
                            ActiveTaskListSection.UPCOMING -> upcomingCollapsed
                        }
                        item(key = "section_${group.category}") {
                            CollapsibleSection(
                                label = activeSectionLabel(group.category),
                                count = group.tasks.size,
                                isCollapsed = isCollapsed,
                                onToggle = {
                                    when (group.category) {
                                        ActiveTaskListSection.OVERDUE -> overdueCollapsed = !overdueCollapsed
                                        ActiveTaskListSection.UPCOMING -> upcomingCollapsed = !upcomingCollapsed
                                    }
                                },
                                headerCardModifier = Modifier.padding(horizontal = dimens.paddingLarge),
                                contentCardModifier = Modifier.padding(horizontal = dimens.paddingLarge),
                                showContent = false,
                            ) {}
                        }
                        if (!isCollapsed) {
                            items(group.tasks, key = { it.id }) { task ->
                                TaskRow(
                                    task = task,
                                    contentPreview = taskContentPreviews[task.id].orEmpty(),
                                    imageSummary = taskImageSummaries[task.id],
                                    dueText = taskDueTextById[task.id].orEmpty(),
                                    isOverdue = group.category == ActiveTaskListSection.OVERDUE,
                                    onToggleComplete = { onToggleComplete(task) },
                                    onClick = { onTaskClick(task) },
                                    onToggleStar = { onToggleStar(task) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    thickness = dimens.dividerThin,
                                )
                            }
                        }
                    }

                    // Completed section
                    if (completedTasks.isNotEmpty()) {
                        item(key = "completed_spacer") {
                            Spacer(Modifier.size(dimens.spacerXLarge))
                        }

                        item(key = "completed_section_header") {
                            CollapsibleSection(
                                label = stringResource(Res.string.completed).uppercase(),
                                count = completedTasks.size,
                                isCollapsed = completedCollapsed,
                                onToggle = { completedCollapsed = !completedCollapsed },
                                headerCardModifier = Modifier.padding(horizontal = dimens.paddingLarge),
                                contentCardModifier = Modifier.padding(horizontal = dimens.paddingLarge),
                                showContent = false,
                            ) {}
                        }

                        if (!completedCollapsed) {
                            itemsIndexed(
                                items = completedTasks,
                                key = { _, task -> "completed_${task.id}" },
                            ) { index, task ->
                                CompletedTaskRow(
                                    task = task,
                                    contentPreview = taskContentPreviews[task.id].orEmpty(),
                                    imageSummary = taskImageSummaries[task.id],
                                    dueText = taskDueTextById[task.id].orEmpty(),
                                    onToggleComplete = { onToggleComplete(task) },
                                    onClick = { onTaskClick(task) },
                                    onToggleStar = { onToggleStar(task) },
                                )
                                if (index < completedTasks.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        thickness = dimens.dividerThin,
                                        modifier = Modifier.padding(horizontal = dimens.paddingLarge),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskListTopBar(
    listName: String,
    onAllViewsClick: () -> Unit,
    sortOption: TaskSortOption = TaskSortOption.RECENTLY_UPDATED,
    onSortOptionSelected: (TaskSortOption) -> Unit = {},
    viewMode: TaskListViewMode = TaskListViewMode.LIST,
    onViewModeSelected: (TaskListViewMode) -> Unit = {},
    onSortMenuBusyChanged: (Boolean) -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    var showSortMenu by remember { mutableStateOf(false) }
    ModalBusyEffect(showSortMenu, onSortMenuBusyChanged)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingSmall),
    ) {
        TextButton(onClick = onAllViewsClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_left),
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(dimens.iconDefault),
            )
            Spacer(Modifier.width(dimens.spacerSmall))
            Text(text = stringResource(Res.string.all_views), color = PrimaryBlue)
        }
        Text(
            text = listName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .semantics { heading() }
                .padding(horizontal = dimens.paddingMedium, vertical = dimens.paddingSmall),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = dimens.paddingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                TaskListViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == viewMode,
                        onClick = { onViewModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, TaskListViewMode.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = PrimaryBlue,
                            activeContentColor = Color.White,
                        ),
                        icon = {
                            Icon(
                                painter = painterResource(viewModeIcon(mode)),
                                contentDescription = null,
                                modifier = Modifier.size(dimens.iconDefault),
                            )
                        },
                    ) {
                        Text(text = viewModeLabel(mode), maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.width(dimens.spacerLarge))
            Box {
                val sortDescription = stringResource(
                    Res.string.sort_current_description,
                    sortOptionLabel(sortOption),
                )
                OutlinedButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.semantics { contentDescription = sortDescription },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_unfold),
                        contentDescription = null,
                        modifier = Modifier.size(dimens.iconDefault),
                    )
                    Spacer(Modifier.width(dimens.spacerSmall))
                    Text(text = stringResource(Res.string.sort), maxLines = 1)
                }
                SortOptionDropdown(
                    expanded = showSortMenu,
                    currentOption = sortOption,
                    onOptionSelected = onSortOptionSelected,
                    onDismiss = { showSortMenu = false },
                )
            }
        }
    }
}

private fun viewModeIcon(mode: TaskListViewMode): DrawableResource = when (mode) {
    TaskListViewMode.LIST -> Res.drawable.ic_list
    TaskListViewMode.BOARD -> Res.drawable.ic_grid_view
}

@Composable
private fun viewModeLabel(mode: TaskListViewMode): String = when (mode) {
    TaskListViewMode.LIST -> stringResource(Res.string.task_view_list)
    TaskListViewMode.BOARD -> stringResource(Res.string.task_view_board)
}

@Composable
private fun SortOptionDropdown(
    expanded: Boolean,
    currentOption: TaskSortOption,
    onOptionSelected: (TaskSortOption) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        TaskSortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    SelectedOptionRow(
                        label = sortOptionLabel(option),
                        isSelected = option == currentOption,
                        onClick = {
                            onOptionSelected(option)
                            onDismiss()
                        },
                    )
                },
                onClick = {
                    onOptionSelected(option)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun sortOptionLabel(option: TaskSortOption): String = when (option) {
    TaskSortOption.RECENTLY_UPDATED -> stringResource(Res.string.sort_recently_updated)
    TaskSortOption.BY_DEADLINE -> stringResource(Res.string.sort_by_deadline)
    TaskSortOption.BY_PRIORITY -> stringResource(Res.string.sort_by_priority)
    TaskSortOption.BY_TITLE -> stringResource(Res.string.sort_by_title)
}

@Composable
internal fun TaskRow(
    task: Task,
    contentPreview: String = "",
    imageSummary: AttachmentSummary? = null,
    dueText: String = "",
    isOverdue: Boolean = false,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onToggleStar: () -> Unit = {},
) {
    TaskListRow(
        task = task,
        contentPreview = contentPreview,
        imageSummary = imageSummary,
        dueText = dueText,
        isCompleted = false,
        isOverdue = isOverdue,
        verticalPadding = OpenTasksTheme.dimens.listRowVerticalPadding,
        onToggleComplete = onToggleComplete,
        onClick = onClick,
        onToggleStar = onToggleStar,
    )
}

@Composable
internal fun CompletedTaskRow(
    task: Task,
    contentPreview: String = "",
    imageSummary: AttachmentSummary? = null,
    dueText: String = "",
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onToggleStar: () -> Unit = {},
) {
    TaskListRow(
        task = task,
        contentPreview = contentPreview,
        imageSummary = imageSummary,
        dueText = dueText,
        isCompleted = true,
        isOverdue = false,
        verticalPadding = OpenTasksTheme.dimens.listRowCompletedVerticalPadding,
        onToggleComplete = onToggleComplete,
        onClick = onClick,
        onToggleStar = onToggleStar,
    )
}

@Composable
private fun TaskListRow(
    task: Task,
    contentPreview: String,
    imageSummary: AttachmentSummary?,
    dueText: String,
    isCompleted: Boolean,
    isOverdue: Boolean,
    verticalPadding: Dp,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimens.paddingLarge,
                vertical = verticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckboxButton(
            isChecked = isCompleted,
            tint = if (isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            } else {
                priorityColor(task.priority)
            },
            onClick = onToggleComplete,
        )

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(
                title = task.title,
                isCompleted = isCompleted,
            )
            TaskDueDateText(
                dueText = dueText,
                isCompleted = isCompleted,
                isOverdue = isOverdue,
            )
            TaskContentPreviewText(contentPreview)
        }

        TaskImageSummaryAffordance(imageSummary)

        TaskStarButton(
            isStarred = task.isStarred,
            onClick = onToggleStar,
        )
    }
}

@Composable
private fun TaskImageSummaryAffordance(imageSummary: AttachmentSummary?) {
    if (imageSummary == null || imageSummary.imageCount <= 0) return
    val dimens = OpenTasksTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(Res.drawable.ic_attach),
            contentDescription = stringResource(Res.string.image_attachment),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconSmall),
        )
        AttachmentSyncBadge(imageSummary.worstSyncState)
        Spacer(Modifier.width(dimens.spacerSmall))
    }
}

@Composable
private fun activeSectionLabel(category: ActiveTaskListSection): String = when (category) {
    ActiveTaskListSection.OVERDUE -> stringResource(Res.string.overdue)
    ActiveTaskListSection.UPCOMING -> stringResource(Res.string.upcoming)
}

@Composable
private fun TaskDueDateText(
    dueText: String,
    isCompleted: Boolean,
    isOverdue: Boolean,
) {
    if (dueText.isBlank()) return
    Text(
        text = dueText,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            isOverdue -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
