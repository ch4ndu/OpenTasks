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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.priorityColor
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel.SectionGroup
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.completed
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.no_tasks
import opentasks.composeapp.generated.resources.select
import opentasks.composeapp.generated.resources.sort_by
import opentasks.composeapp.generated.resources.sort_by_deadline
import opentasks.composeapp.generated.resources.sort_by_priority
import opentasks.composeapp.generated.resources.sort_by_title
import opentasks.composeapp.generated.resources.sort_recently_updated
import opentasks.composeapp.generated.resources.due_this_week
import opentasks.composeapp.generated.resources.high_priority
import opentasks.composeapp.generated.resources.no_date
import opentasks.composeapp.generated.resources.overdue
import opentasks.composeapp.generated.resources.starred
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.toggle_view_mode
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
    onRefresh: () -> Unit = {},
) {
    // Sync parent's selectedCategoryId into ViewModel for the derived flow
    LaunchedEffect(selectedCategoryId) { viewModel.selectCategory(selectedCategoryId) }

    val taskPendingSeriesChoice by viewModel.taskPendingSeriesChoice.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsState()
    val filteredCategories by viewModel.filteredCategories.collectAsState()
    val categorySearchQuery by viewModel.categorySearchQuery.collectAsState()
    val defaultListName = stringResource(Res.string.inbox)
    val starredName = stringResource(Res.string.starred)
    val todayName = stringResource(Res.string.today)
    val overdueStr = stringResource(Res.string.overdue)
    val noDateStr = stringResource(Res.string.no_date)
    val highPriorityStr = stringResource(Res.string.high_priority)
    val dueThisWeekStr = stringResource(Res.string.due_this_week)
    val selectedListName = remember(categories, currentFilter, defaultListName, starredName, todayName, overdueStr, noDateStr, highPriorityStr, dueThisWeekStr) {
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
            val activeTasks by viewModel.activeTasksForSelectedCategory.collectAsState()
            val completedTasks by viewModel.completedTasksForSelectedCategory.collectAsState()
            val groupedTasks by viewModel.groupedActiveTasks.collectAsState()
            TaskListContent(
                listName = selectedListName,
                activeTasks = activeTasks,
                completedTasks = completedTasks,
                groupedActiveTasks = groupedTasks,
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
                onRefresh = onRefresh,
            )
        }
        TaskListViewMode.BOARD -> {
            val tasksByStatus by viewModel.tasksByStatus.collectAsState()
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SyncPullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    KanbanBoardContent(
                        tasksByStatus = tasksByStatus,
                        onTaskClick = onTaskClick,
                        onStatusChange = { task, newStatus -> viewModel.moveTaskToStatus(task, newStatus) },
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

    if (showCategoryPicker) {
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
    activeTasks: List<Task> = emptyList(),
    completedTasks: List<Task> = emptyList(),
    groupedActiveTasks: List<SectionGroup> = emptyList(),
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

    var completedCollapsed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SyncPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        // Task list — fills entire screen, scrolls behind top bar and bottom nav
        if (activeTasks.isEmpty() && completedTasks.isEmpty()) {
            EmptyPlaceholder(
                text = stringResource(Res.string.no_tasks),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeight,
                    bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge, // nav bar + FAB + spacing
                ),
            ) {
                // Active tasks — grouped by section when sections exist
                val useGrouped = groupedActiveTasks.size > 1 ||
                    groupedActiveTasks.any { it.name != null }
                if (useGrouped) {
                    groupedActiveTasks.forEach { group ->
                        if (group.name != null) {
                            item(key = "section_${group.name}") {
                                SectionHeader(
                                    name = group.name,
                                    count = group.tasks.size,
                                )
                            }
                        }
                        items(group.tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
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
                } else {
                    items(activeTasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
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
                        ) {}
                    }

                    if (!completedCollapsed) {
                        items(
                            items = completedTasks,
                            key = { "completed_${it.id}" },
                        ) { task ->
                            CompletedTaskRow(
                                task = task,
                                onToggleComplete = { onToggleComplete(task) },
                                onClick = { onTaskClick(task) },
                                onToggleStar = { onToggleStar(task) },
                            )
                            if (task.id != completedTasks.last().id) {
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
                modifier = Modifier.clickable(onClick = onListClick),
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
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onToggleStar: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.listRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckboxButton(
            isChecked = false,
            tint = priorityColor(task.priority),
            onClick = onToggleComplete,
        )

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(
                title = task.title,
                isCompleted = false,
            )
            TaskContentPreviewText(task.content)
        }

        TaskStarButton(
            isStarred = task.isStarred,
            onClick = onToggleStar,
        )
    }
}

@Composable
internal fun CompletedTaskRow(
    task: Task,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onToggleStar: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.listRowCompletedVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckboxButton(
            isChecked = true,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            onClick = onToggleComplete,
        )

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(
                title = task.title,
                isCompleted = true,
            )
            TaskContentPreviewText(task.content)
        }

        TaskStarButton(
            isStarred = task.isStarred,
            onClick = onToggleStar,
        )
    }
}

@Composable
private fun SectionHeader(
    name: String,
    count: Int,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
