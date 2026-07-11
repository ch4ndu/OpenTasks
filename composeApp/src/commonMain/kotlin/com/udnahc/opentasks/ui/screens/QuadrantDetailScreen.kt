package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskCategory
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.ui.theme.DateOrange
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.priorityColor
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_task
import opentasks.composeapp.generated.resources.completed
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_grid_view
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_repeat
import opentasks.composeapp.generated.resources.ic_unfold
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.later
import opentasks.composeapp.generated.resources.next_7_days
import opentasks.composeapp.generated.resources.no_date
import opentasks.composeapp.generated.resources.overdue
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.toggle_view_mode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuadrantDetailScreen(
    title: String,
    priority: TaskPriority,
    viewModel: MatrixViewModel,
    onBack: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onCreateTask: (TaskPriority) -> Unit,
) {
    LaunchedEffect(priority) { viewModel.selectPriority(priority) }
    val taskPendingSeriesChoice by viewModel.taskPendingSeriesChoice.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    val density = LocalDensity.current
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val dimens = OpenTasksTheme.dimens
    val topBarHeight = dimens.topBarHeight + statusBarHeight

    when (viewMode) {
        TaskListViewMode.LIST -> {
            val categorizedTasks by viewModel.categorizedTasks.collectAsState()
            val categoryNames by viewModel.categoryNames.collectAsState()
            val taskContentPreviews by viewModel.taskContentPreviews.collectAsState()
            val defaultCategoryName = stringResource(Res.string.inbox)
            QuadrantDetailContent(
                title = title,
                priority = priority,
                categorizedTasks = categorizedTasks,
                categoryNames = categoryNames,
                taskContentPreviews = taskContentPreviews,
                defaultCategoryName = defaultCategoryName,
                onBack = onBack,
                onTaskClick = onTaskClick,
                onToggleComplete = { viewModel.toggleComplete(it) },
                onAddTask = { onCreateTask(priority) },
                viewMode = viewMode,
                onViewModeToggle = { viewModel.setViewMode(TaskListViewMode.BOARD) },
            )
        }

        TaskListViewMode.BOARD -> {
            val tasksByStatus by viewModel.tasksByStatus.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                KanbanBoardContent(
                    tasksByStatus = tasksByStatus,
                    onTaskClick = onTaskClick,
                    onStatusChange = { task, newStatus ->
                        viewModel.moveTaskToStatus(
                            task,
                            newStatus
                        )
                    },
                    onToggleStar = { viewModel.toggleStar(it) },
                    topBarHeight = topBarHeight,
                    navBarHeight = 0.dp,
                )

                OpenTasksTopBar(
                    title = title,
                    containerStyle = OpenTasksTopBarContainerStyle.Translucent,
                    navigationIcon = {
                        OpenTasksBackButton(onClick = onBack)
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setViewMode(TaskListViewMode.LIST) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_list),
                                contentDescription = stringResource(Res.string.toggle_view_mode),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )

                FloatingActionButton(
                    onClick = { onCreateTask(priority) },
                    shape = CircleShape,
                    containerColor = PrimaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimens.paddingXLarge),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = stringResource(Res.string.add_task),
                        tint = Color.White,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuadrantDetailContent(
    title: String,
    priority: TaskPriority,
    categorizedTasks: List<MatrixViewModel.TaskCategoryGroup>,
    categoryNames: Map<String, String> = emptyMap(),
    taskContentPreviews: Map<String, String> = emptyMap(),
    defaultCategoryName: String,
    onBack: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onAddTask: () -> Unit = {},
    viewMode: TaskListViewMode = TaskListViewMode.LIST,
    onViewModeToggle: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens

    // Track collapsed state per category
    val collapsedState = remember { mutableStateMapOf<TaskCategory, Boolean>() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                shape = CircleShape,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = stringResource(Res.string.add_task),
                    tint = Color.White,
                )
            }
        },
        topBar = {
            OpenTasksTopBar(
                title = title,
                navigationIcon = {
                    OpenTasksBackButton(onClick = onBack)
                },
                actions = {
                    IconButton(onClick = onViewModeToggle) {
                        Icon(
                            painter = painterResource(
                                when (viewMode) {
                                    TaskListViewMode.LIST -> Res.drawable.ic_grid_view
                                    TaskListViewMode.BOARD -> Res.drawable.ic_list
                                },
                            ),
                            contentDescription = stringResource(Res.string.toggle_view_mode),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = dimens.paddingLarge),
        ) {
            categorizedTasks.forEach { group ->
                val category = group.category
                val categoryTasks = group.tasks
                if (categoryTasks.isEmpty()) return@forEach

                val isCollapsed = collapsedState[category] == true

                item(key = "section_$category") {
                    val label = categoryLabel(category)
                    QuadrantSectionHeader(
                        label = label,
                        count = categoryTasks.size,
                        isCollapsed = isCollapsed,
                        onToggle = { collapsedState[category] = !isCollapsed },
                        labelColor = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = dimens.paddingMedium),
                    )
                }

                if (!isCollapsed) {
                    items(categoryTasks, key = { it.id }) { task ->
                        DetailTaskRow(
                            task = task,
                            contentPreview = taskContentPreviews[task.id].orEmpty(),
                            priority = priority,
                            isOverdue = category == TaskCategory.OVERDUE,
                            categoryName = categoryNames[task.categoryId] ?: defaultCategoryName,
                            onToggleComplete = { onToggleComplete(task) },
                            onClick = { onTaskClick(task) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = dimens.dividerThin,
                            modifier = Modifier.padding(horizontal = dimens.paddingLarge),
                        )
                    }
                }

                item(key = "spacer_$category") {
                    Spacer(Modifier.size(dimens.spacerSmall))
                }
            }
        }
    }
}

@Composable
private fun QuadrantSectionHeader(
    label: String,
    count: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(dimens.cornerXLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(dimens.spacerSmall))
            Icon(
                painter = painterResource(
                    if (isCollapsed) Res.drawable.ic_list else Res.drawable.ic_unfold
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.iconSmall),
            )
        }
    }
}

@Composable
internal fun DetailTaskRow(
    task: Task,
    contentPreview: String = "",
    priority: TaskPriority,
    isOverdue: Boolean,
    categoryName: String,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimens.paddingLarge,
                vertical = dimens.listRowCompletedVerticalPadding
            ),
        verticalAlignment = Alignment.Top,
    ) {
        TaskCheckboxButton(
            isChecked = task.status == TaskStatus.DONE,
            tint = if (task.status == TaskStatus.DONE) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            } else {
                priorityColor(priority)
            },
            onClick = onToggleComplete,
        )

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(
                title = task.title,
                isCompleted = task.status == TaskStatus.DONE,
            )

            // Show deadline info if present
            if (task.deadline != null && task.status != TaskStatus.DONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDateShort(task.deadline),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOverdue) DateOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.recurrenceType != RecurrenceType.NONE) {
                        Spacer(Modifier.width(dimens.spacerSmall))
                        Icon(
                            painter = painterResource(Res.drawable.ic_repeat),
                            contentDescription = null,
                            tint = if (isOverdue) DateOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(dimens.iconTiny),
                        )
                    }
                }
            }

            // Show content preview if present
            TaskContentPreviewText(contentPreview)
        }

        // List label
        Text(
            text = categoryName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Bottom),
        )
    }
}

@Composable
private fun categoryLabel(category: TaskCategory): String = when (category) {
    TaskCategory.OVERDUE -> stringResource(Res.string.overdue)
    TaskCategory.TODAY -> stringResource(Res.string.today).uppercase()
    TaskCategory.NEXT_7_DAYS -> stringResource(Res.string.next_7_days)
    TaskCategory.LATER -> stringResource(Res.string.later)
    TaskCategory.NO_DATE -> stringResource(Res.string.no_date)
    TaskCategory.COMPLETED -> stringResource(Res.string.completed)
}
