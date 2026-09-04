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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.minimumInteractiveTargetSize
import com.udnahc.opentasks.ui.theme.priorityColor
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel.ActiveTaskListSection
import com.udnahc.opentasks.viewmodel.TaskListViewModel.SectionGroup
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.completed
import opentasks.composeapp.generated.resources.due_this_week
import opentasks.composeapp.generated.resources.high_priority
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_attach
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.image_attachment
import opentasks.composeapp.generated.resources.no_date
import opentasks.composeapp.generated.resources.no_tasks
import opentasks.composeapp.generated.resources.overdue
import opentasks.composeapp.generated.resources.select
import opentasks.composeapp.generated.resources.sort_by
import opentasks.composeapp.generated.resources.sort_by_deadline
import opentasks.composeapp.generated.resources.sort_by_priority
import opentasks.composeapp.generated.resources.sort_by_title
import opentasks.composeapp.generated.resources.sort_recently_updated
import opentasks.composeapp.generated.resources.starred
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.toggle_view_mode
import opentasks.composeapp.generated.resources.upcoming
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    selectedCategoryId: String,
    onSelectedCategoryChanged: (String) -> Unit,
    onTaskClick: (Task) -> Unit,
    onSettingsClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onTaskMutationFailure: () -> Unit = {},
) {
    // Sync parent's selectedCategoryId into ViewModel for the derived flow
    LaunchedEffect(selectedCategoryId) { viewModel.selectCategory(selectedCategoryId) }

    val taskPendingSeriesChoice by viewModel.taskPendingSeriesChoice.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsState()
    val defaultListName = stringResource(Res.string.inbox)
    val starredName = stringResource(Res.string.starred)
    val todayName = stringResource(Res.string.today)
    val overdueStr = stringResource(Res.string.overdue)
    val noDateStr = stringResource(Res.string.no_date)
    val highPriorityStr = stringResource(Res.string.high_priority)
    val dueThisWeekStr = stringResource(Res.string.due_this_week)
    val selectedListName = remember(
        categories,
        currentFilter,
        defaultListName,
        starredName,
        todayName,
        overdueStr,
        noDateStr,
        highPriorityStr,
        dueThisWeekStr
    ) {
        when (currentFilter) {
            is TaskListFilter.Starred -> starredName
            is TaskListFilter.Today -> todayName
            is TaskListFilter.Overdue -> overdueStr
            is TaskListFilter.NoDate -> noDateStr
            is TaskListFilter.HighPriority -> highPriorityStr
            is TaskListFilter.DueThisWeek -> dueThisWeekStr
            is TaskListFilter.Category -> {
                val catId = (currentFilter as TaskListFilter.Category).id
                categories.find { it.id == catId }?.name ?: defaultListName
            }
        }
    }

    val density = LocalDensity.current
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val navBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val dimens = OpenTasksTheme.dimens
    val topBarHeight = dimens.topBarHeight + statusBarHeight

    when (viewMode) {
        TaskListViewMode.LIST -> {
            val listProjection by viewModel.listProjection.collectAsState()
            val taskImageSummaries by viewModel.taskImageSummaries.collectAsState()
            val taskContentPreviews by viewModel.taskContentPreviews.collectAsState()
            val taskDueTextById by viewModel.taskDueTextById.collectAsState()
            TaskListContent(
                listName = selectedListName,
                completedTasks = listProjection.completedTasks,
                groupedActiveTasks = listProjection.groupedActiveTasks,
                taskImageSummaries = taskImageSummaries,
                taskContentPreviews = taskContentPreviews,
                taskDueTextById = taskDueTextById,
                onTaskClick = onTaskClick,
                onToggleComplete = { viewModel.toggleComplete(it) },
                onToggleStar = { viewModel.toggleStar(it) },
                onListClick = { showCategoryPicker = true },
                onSettingsClick = onSettingsClick,
                sortOption = sortOption,
                onSortOptionSelected = { viewModel.setSortOption(it) },
                viewMode = viewMode,
                onViewModeToggle = { viewModel.setViewMode(TaskListViewMode.BOARD) },
                isRefreshing = isRefreshing,
                syncEnabled = syncEnabled,
                onRefresh = onRefresh,
            )
        }

        TaskListViewMode.BOARD -> {
            val tasksByStatus by viewModel.tasksByStatus.collectAsState()
            val taskDueTextById by viewModel.boardTaskDueTextById.collectAsState()
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                SyncPullToRefresh(
                    isRefreshing = isRefreshing,
                    enabled = syncEnabled,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
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
                        topBarHeight = topBarHeight,
                        navBarHeight = navBarHeight,
                    )
                }

                // Translucent top bar overlay for board mode
                TaskListTopBar(
                    listName = selectedListName,
                    onListClick = { showCategoryPicker = true },
                    onSettingsClick = onSettingsClick,
                    sortOption = sortOption,
                    onSortOptionSelected = { viewModel.setSortOption(it) },
                    viewMode = viewMode,
                    onViewModeToggle = { viewModel.setViewMode(TaskListViewMode.LIST) },
                )
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

    if (showCategoryPicker) {
        val filteredCategories by viewModel.filteredCategories.collectAsState()
        val categorySearchQuery by viewModel.categorySearchQuery.collectAsState()
        val listPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        CategoryPickerBottomSheet(
            sheetState = listPickerState,
            categories = filteredCategories,
            selectedCategoryId = if (currentFilter is TaskListFilter.Category) {
                (currentFilter as TaskListFilter.Category).id
            } else {
                ""
            },
            onCategorySelected = { category ->
                viewModel.selectFilter(TaskListFilter.Category(category.id))
                onSelectedCategoryChanged(category.id)
                viewModel.setCategorySearchQuery("")
                showCategoryPicker = false
            },
            onAddCategory = { name -> viewModel.addCategory(name) },
            onDismiss = {
                viewModel.setCategorySearchQuery("")
                showCategoryPicker = false
            },
            showTitle = false,
            showSearch = false,
            searchQuery = categorySearchQuery,
            onSearchQueryChange = { viewModel.setCategorySearchQuery(it) },
            selectedFilter = currentFilter,
            onFilterSelected = { filter ->
                viewModel.selectFilter(filter)
                viewModel.setCategorySearchQuery("")
                showCategoryPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onListClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    sortOption: TaskSortOption = TaskSortOption.RECENTLY_UPDATED,
    onSortOptionSelected: (TaskSortOption) -> Unit = {},
    viewMode: TaskListViewMode = TaskListViewMode.LIST,
    onViewModeToggle: () -> Unit = {},
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    val density = LocalDensity.current
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val navBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    // TopAppBar default height + status bar
    val topBarHeight = dimens.topBarHeight + statusBarHeight

    var overdueCollapsed by remember { mutableStateOf(false) }
    var upcomingCollapsed by remember { mutableStateOf(false) }
    var completedCollapsed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SyncPullToRefresh(
            isRefreshing = isRefreshing,
            enabled = syncEnabled,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
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
                        top = topBarHeight,
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

        // Translucent Top bar overlay — content scrolls behind this
        TaskListTopBar(
            listName = listName,
            onListClick = onListClick,
            onSettingsClick = onSettingsClick,
            sortOption = sortOption,
            onSortOptionSelected = onSortOptionSelected,
            viewMode = viewMode,
            onViewModeToggle = onViewModeToggle,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskListTopBar(
    listName: String,
    onListClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    sortOption: TaskSortOption = TaskSortOption.RECENTLY_UPDATED,
    onSortOptionSelected: (TaskSortOption) -> Unit = {},
    viewMode: TaskListViewMode = TaskListViewMode.LIST,
    onViewModeToggle: () -> Unit = {},
) {
    var showSortMenu by remember { mutableStateOf(false) }

    OpenTasksTopBar(
        containerStyle = OpenTasksTopBarContainerStyle.Translucent,
        titleContent = {
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
                Spacer(Modifier.width(OpenTasksTheme.dimens.spacerMedium))
                Icon(
                    painter = painterResource(Res.drawable.ic_unfold),
                    contentDescription = stringResource(Res.string.select),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(OpenTasksTheme.dimens.iconDefault),
                )
            }
        },
        actions = {
            IconButton(onClick = onViewModeToggle) {
                Icon(
                    painter = painterResource(
                        when (viewMode) {
                            TaskListViewMode.LIST -> Res.drawable.ic_grid_view
                            TaskListViewMode.BOARD -> Res.drawable.ic_list
                        }
                    ),
                    contentDescription = stringResource(Res.string.toggle_view_mode),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_unfold),
                        contentDescription = stringResource(Res.string.sort_by),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SortOptionDropdown(
                    expanded = showSortMenu,
                    currentOption = sortOption,
                    onOptionSelected = onSortOptionSelected,
                    onDismiss = { showSortMenu = false },
                )
            }
            OpenTasksSettingsButton(onClick = onSettingsClick)
        },
    )
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
