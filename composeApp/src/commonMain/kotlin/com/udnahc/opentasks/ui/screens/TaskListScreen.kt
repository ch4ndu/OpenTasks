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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.PriorityLow
import com.udnahc.opentasks.ui.theme.PriorityMedium
import com.udnahc.opentasks.ui.theme.PriorityNone
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.import_from_calendar
import opentasks.composeapp.generated.resources.import_from_ics
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.completed
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.ic_check_box_outline
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.ic_settings
import opentasks.composeapp.generated.resources.more
import opentasks.composeapp.generated.resources.no_tasks
import opentasks.composeapp.generated.resources.select
import opentasks.composeapp.generated.resources.settings
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
) {
    // Sync parent's selectedCategoryId into ViewModel for the derived flow
    LaunchedEffect(selectedCategoryId) { viewModel.selectCategory(selectedCategoryId) }

    val activeTasks by viewModel.activeTasksForSelectedCategory.collectAsState()
    val completedTasks by viewModel.completedTasksForSelectedCategory.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsState()
    val defaultListName = stringResource(Res.string.inbox)
    val selectedListName = remember(categories, selectedCategoryId, defaultListName) {
        categories.find { it.id == selectedCategoryId }?.name ?: defaultListName
    }

    TaskListContent(
        listName = selectedListName,
        activeTasks = activeTasks,
        completedTasks = completedTasks,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onListClick = { showCategoryPicker = true },
        onSettingsClick = onSettingsClick,
    )

    if (showCategoryPicker) {
        val listPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        CategoryPickerBottomSheet(
            sheetState = listPickerState,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { category ->
                onSelectedCategoryChanged(category.id)
                showCategoryPicker = false
            },
            onAddCategory = { name -> viewModel.addCategory(name) },
            onDismiss = { showCategoryPicker = false },
            showTitle = false,
            showSearch = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListContent(
    listName: String = "Inbox",
    activeTasks: List<Task> = emptyList(),
    completedTasks: List<Task> = emptyList(),
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onListClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
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
                // Active tasks
                items(activeTasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggleComplete = { onToggleComplete(task) },
                        onClick = { onTaskClick(task) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = dimens.dividerThin,
                    )
                }

                // Completed section
                if (completedTasks.isNotEmpty()) {
                    item(key = "completed_spacer") {
                        Spacer(Modifier.size(dimens.spacerXLarge))
                    }

                    item(key = "completed_section") {
                        CollapsibleSection(
                            label = stringResource(Res.string.completed).uppercase(),
                            count = completedTasks.size,
                            isCollapsed = completedCollapsed,
                            onToggle = { completedCollapsed = !completedCollapsed },
                            headerCardModifier = Modifier.padding(horizontal = dimens.paddingLarge),
                            contentCardModifier = Modifier.padding(horizontal = dimens.paddingLarge),
                        ) {
                            Column {
                                completedTasks.forEachIndexed { index, task ->
                                    val onToggle = remember(task.id) { { onToggleComplete(task) } }
                                    val onClick = remember(task.id) { { onTaskClick(task) } }
                                    CompletedTaskRow(
                                        task = task,
                                        onToggleComplete = onToggle,
                                        onClick = onClick,
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

        // Translucent Top bar overlay — content scrolls behind this
        TaskListTopBar(
            listName = listName,
            onListClick = onListClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListTopBar(
    listName: String,
    onListClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
        ),
        title = {
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
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = stringResource(Res.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun TaskRow(
    task: Task,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.listRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(dimens.touchTargetMedium),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_check_box_outline),
                contentDescription = null,
                tint = priorityColor(task.priority),
                modifier = Modifier.size(dimens.iconLarge),
            )
        }

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.content.isNotBlank()) {
                Text(
                    text = task.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompletedTaskRow(
    task: Task,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.listRowCompletedVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(dimens.touchTargetMedium),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_check_box),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(dimens.iconLarge),
            )
        }

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.content.isNotBlank()) {
                Text(
                    text = task.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> PriorityHigh
    TaskPriority.MEDIUM -> PriorityMedium
    TaskPriority.LOW -> PriorityLow
    TaskPriority.NONE -> PriorityNone
}

@Composable
@Preview
private fun TaskListScreenPreview() {
    OpenTasksTheme {
        TaskListContent(
            activeTasks = PreviewSampleData.sampleTasks.filter { !it.isCompleted },
            completedTasks = PreviewSampleData.sampleTasks.filter { it.isCompleted },
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@Preview
private fun TaskListScreenEmptyPreview() {
    OpenTasksTheme {
        TaskListContent(
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@Preview
private fun TaskRowPreview() {
    OpenTasksTheme {
        TaskRow(
            task = PreviewSampleData.sampleTasks.first(),
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@Preview
private fun CompletedTaskRowPreview() {
    OpenTasksTheme {
        CompletedTaskRow(
            task = PreviewSampleData.sampleTasks.first { it.isCompleted },
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@Preview
private fun TaskListTopBarPreview() {
    OpenTasksTheme {
        TaskListTopBar(
            listName = "Inbox",
            onListClick = {},
            onSettingsClick = {},
        )
    }
}
