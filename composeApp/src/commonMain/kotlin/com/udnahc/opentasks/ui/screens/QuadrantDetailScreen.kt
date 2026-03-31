package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.DateOrange
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.PriorityLow
import com.udnahc.opentasks.ui.theme.PriorityMedium
import com.udnahc.opentasks.ui.theme.PriorityNone
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_task
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.completed
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_check_box_outline
import opentasks.composeapp.generated.resources.ic_repeat
import opentasks.composeapp.generated.resources.inbox
import opentasks.composeapp.generated.resources.later
import opentasks.composeapp.generated.resources.next_7_days
import opentasks.composeapp.generated.resources.no_date
import opentasks.composeapp.generated.resources.overdue
import opentasks.composeapp.generated.resources.today
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class TaskCategory {
    OVERDUE,
    TODAY,
    NEXT_7_DAYS,
    LATER,
    NO_DATE,
    COMPLETED,
}

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
    val tasks by viewModel.tasksForSelectedPriority.collectAsState()

    QuadrantDetailContent(
        title = title,
        priority = priority,
        tasks = tasks,
        onBack = onBack,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onAddTask = { onCreateTask(priority) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuadrantDetailContent(
    title: String,
    priority: TaskPriority,
    tasks: List<Task>,
    onBack: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onAddTask: () -> Unit = {},
    now: Long = localNow(),
    startOfToday: Long = run {
        val today = todayLocal()
        startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
    },
    startOfTomorrow: Long = run {
        val tomorrow = todayLocal().plus(1, DateTimeUnit.DAY)
        startOfDayLocalMillis(tomorrow.year, tomorrow.monthNumber, tomorrow.dayOfMonth)
    },
    endOfNext7Days: Long = run {
        val next7 = todayLocal().plus(7, DateTimeUnit.DAY)
        startOfDayLocalMillis(next7.year, next7.monthNumber, next7.dayOfMonth)
    },
) {
    val dimens = OpenTasksTheme.dimens

    val categorized = remember(tasks, now) {
        categorize(tasks, startOfToday, startOfTomorrow, endOfNext7Days)
    }

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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
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
            categorized.forEach { (category, categoryTasks) ->
                if (categoryTasks.isEmpty()) return@forEach

                val isCollapsed = collapsedState[category] == true

                item(key = "section_$category") {
                    val label = categoryLabel(category)
                    CollapsibleSection(
                        label = label,
                        count = categoryTasks.size,
                        isCollapsed = isCollapsed,
                        onToggle = { collapsedState[category] = !isCollapsed },
                        headerCardModifier = Modifier.padding(top = dimens.paddingMedium),
                        labelColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        Column {
                            categoryTasks.forEachIndexed { index, task ->
                                val onToggle = remember(task.id) { { onToggleComplete(task) } }
                                val onClick = remember(task.id) { { onTaskClick(task) } }
                                DetailTaskRow(
                                    task = task,
                                    priority = priority,
                                    isOverdue = category == TaskCategory.OVERDUE,
                                    onToggleComplete = onToggle,
                                    onClick = onClick,
                                )
                                if (index < categoryTasks.lastIndex) {
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

                item(key = "spacer_$category") {
                    Spacer(Modifier.size(dimens.spacerSmall))
                }
            }
        }
    }
}

@Composable
private fun DetailTaskRow(
    task: Task,
    priority: TaskPriority,
    isOverdue: Boolean,
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
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(dimens.touchTargetMedium),
        ) {
            Icon(
                painter = painterResource(
                    if (task.isCompleted) Res.drawable.ic_check_box
                    else Res.drawable.ic_check_box_outline
                ),
                contentDescription = null,
                tint = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else priorityColor(priority),
                modifier = Modifier.size(dimens.iconLarge),
            )
        }

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Show deadline info if present
            if (task.deadline != null && !task.isCompleted) {
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

        // List label
        Text(
            text = stringResource(Res.string.inbox),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Bottom),
        )
    }
}

private fun categorize(
    tasks: List<Task>,
    startOfToday: Long,
    startOfTomorrow: Long,
    endOfNext7Days: Long,
): List<Pair<TaskCategory, List<Task>>> {
    val incomplete = tasks.filter { !it.isCompleted }
    val completed = tasks.filter { it.isCompleted }

    val overdue = incomplete
        .filter { it.deadline != null && it.deadline < startOfToday }
        .sortedBy { it.deadline }

    val today = incomplete
        .filter { it.deadline != null && it.deadline >= startOfToday && it.deadline < startOfTomorrow }
        .sortedBy { it.deadline }

    val next7Days = incomplete
        .filter { it.deadline != null && it.deadline >= startOfTomorrow && it.deadline < endOfNext7Days }
        .sortedBy { it.deadline }

    val later = incomplete
        .filter { it.deadline != null && it.deadline >= endOfNext7Days }
        .sortedBy { it.deadline }

    val noDate = incomplete
        .filter { it.deadline == null }
        .sortedBy { it.createdAt }

    val sortedCompleted = completed.sortedByDescending { it.updatedAt }

    return listOf(
        TaskCategory.OVERDUE to overdue,
        TaskCategory.TODAY to today,
        TaskCategory.NEXT_7_DAYS to next7Days,
        TaskCategory.LATER to later,
        TaskCategory.NO_DATE to noDate,
        TaskCategory.COMPLETED to sortedCompleted,
    )
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

private fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> PriorityHigh
    TaskPriority.MEDIUM -> PriorityMedium
    TaskPriority.LOW -> PriorityLow
    TaskPriority.NONE -> PriorityNone
}


@Composable
@Preview
private fun QuadrantDetailPreview() {
    OpenTasksTheme {
        QuadrantDetailContent(
            title = "Urgent & Important",
            priority = TaskPriority.HIGH,
            tasks = PreviewSampleData.sampleTasks.filter { it.priority == TaskPriority.HIGH },
            onBack = {},
            onTaskClick = {},
            onToggleComplete = {},
            now = PreviewSampleData.sampleTodayMillis,
            startOfToday = PreviewSampleData.sampleTodayMillis,
            startOfTomorrow = PreviewSampleData.sampleTodayMillis + MILLIS_PER_DAY,
            endOfNext7Days = PreviewSampleData.sampleTodayMillis + 7 * MILLIS_PER_DAY,
        )
    }
}

@Composable
@Preview
private fun DetailTaskRowPreview() {
    OpenTasksTheme {
        DetailTaskRow(
            task = PreviewSampleData.sampleTasks.first(),
            priority = TaskPriority.HIGH,
            isOverdue = false,
            onToggleComplete = {},
            onClick = {},
        )
    }
}
