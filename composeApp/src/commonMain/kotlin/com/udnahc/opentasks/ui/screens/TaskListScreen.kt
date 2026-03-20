package com.udnahc.opentasks.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
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
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.completed
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_dropdown
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.ic_check_box_outline
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.more
import opentasks.composeapp.generated.resources.no_tasks
import opentasks.composeapp.generated.resources.select
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    selectedListId: Long,
    onSelectedListChanged: (Long) -> Unit,
    onTaskClick: (Task) -> Unit,
) {
    // Sync parent's selectedListId into ViewModel for the derived flow
    LaunchedEffect(selectedListId) { viewModel.selectList(selectedListId) }

    val activeTasks by viewModel.activeTasksForSelectedList.collectAsState()
    val completedTasks by viewModel.completedTasksForSelectedList.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }

    val taskLists by viewModel.taskLists.collectAsState()
    val defaultListName = stringResource(Res.string.inbox)
    val selectedListName = remember(taskLists, selectedListId, defaultListName) {
        taskLists.find { it.id == selectedListId }?.name ?: defaultListName
    }

    TaskListContent(
        listName = selectedListName,
        activeTasks = activeTasks,
        completedTasks = completedTasks,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onListClick = { showCategoryPicker = true },
    )

    if (showCategoryPicker) {
        val listPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        CategoryPickerBottomSheet(
            sheetState = listPickerState,
            lists = taskLists,
            selectedListId = selectedListId,
            onListSelected = { taskList ->
                onSelectedListChanged(taskList.id)
                showCategoryPicker = false
            },
            onAddList = { name -> viewModel.addList(name) },
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Task list — fills entire screen, scrolls behind top bar and bottom nav
        if (activeTasks.isEmpty() && completedTasks.isEmpty()) {
            EmptyTasksPlaceholder()
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

                    item(key = "completed_header") {
                        CompletedSectionCard(
                            count = completedTasks.size,
                            isCollapsed = completedCollapsed,
                            onToggle = { completedCollapsed = !completedCollapsed },
                        )
                    }

                    item(key = "completed_content") {
                        CompletedTasksList(
                            tasks = completedTasks,
                            isCollapsed = completedCollapsed,
                            onTaskClick = onTaskClick,
                            onToggleComplete = onToggleComplete,
                        )
                    }
                }
            }
        }

        // Translucent Top bar overlay — content scrolls behind this
        TaskListTopBar(
            listName = listName,
            onListClick = onListClick,
        )
    }
}

@Composable
private fun EmptyTasksPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.no_tasks),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListTopBar(
    listName: String,
    onListClick: () -> Unit,
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
            IconButton(onClick = { /* TODO: implement menu */ }) {
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
private fun CompletedSectionCard(
    count: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingLarge),
        shape = if (isCollapsed) {
            RoundedCornerShape(dimens.cornerXLarge)
        } else {
            RoundedCornerShape(topStart = dimens.cornerXLarge, topEnd = dimens.cornerXLarge)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        CompletedSectionHeader(
            count = count,
            isCollapsed = isCollapsed,
            onClick = onToggle,
        )
    }
}

@Composable
private fun CompletedTasksList(
    tasks: List<Task>,
    isCollapsed: Boolean,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    AnimatedVisibility(
        visible = !isCollapsed,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.paddingLarge),
            shape = RoundedCornerShape(
                bottomStart = dimens.cornerXLarge,
                bottomEnd = dimens.cornerXLarge,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column {
                tasks.forEachIndexed { index, task ->
                    CompletedTaskRow(
                        task = task,
                        onToggleComplete = { onToggleComplete(task) },
                        onClick = { onTaskClick(task) },
                    )
                    if (index < tasks.lastIndex) {
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

@Composable
private fun CompletedSectionHeader(
    count: Int,
    isCollapsed: Boolean,
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
            text = stringResource(Res.string.completed).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(dimens.spacerSmall))
        Icon(
            painter = painterResource(
                if (isCollapsed) Res.drawable.ic_chevron_right
                else Res.drawable.ic_dropdown
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconMedium),
        )
    }
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
