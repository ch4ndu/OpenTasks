package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.preview.PreviewSampleData
import com.udnahc.opentasks.ui.theme.DateOrange
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.PriorityLow
import com.udnahc.opentasks.ui.theme.PriorityMedium
import com.udnahc.opentasks.ui.theme.PriorityNone
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.eisenhower_matrix
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.not_urgent_important
import opentasks.composeapp.generated.resources.not_urgent_unimportant
import opentasks.composeapp.generated.resources.urgent_important
import opentasks.composeapp.generated.resources.urgent_unimportant
import opentasks.composeapp.generated.resources.ic_settings
import opentasks.composeapp.generated.resources.settings
import opentasks.composeapp.generated.resources.view_more
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun EisenhowerMatrixScreen(
    viewModel: MatrixViewModel,
    onTaskClick: (Task) -> Unit,
    onQuadrantClick: (TaskPriority) -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val tasksByPriority by viewModel.tasksByPriority.collectAsState()
    EisenhowerMatrixContent(
        tasksByPriority = tasksByPriority,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onQuadrantClick = onQuadrantClick,
        onSettingsClick = onSettingsClick,
    )
}

@Composable
private fun EisenhowerMatrixContent(
    tasksByPriority: Map<TaskPriority, List<Task>>,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onQuadrantClick: (TaskPriority) -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        MatrixHeader(onSettingsClick = onSettingsClick)

        // 2x2 Grid
        val highTasks = tasksByPriority[TaskPriority.HIGH].orEmpty()
        val lowTasks = tasksByPriority[TaskPriority.LOW].orEmpty()
        val mediumTasks = tasksByPriority[TaskPriority.MEDIUM].orEmpty()
        val noneTasks = tasksByPriority[TaskPriority.NONE].orEmpty()

        QuadrantGrid(
            highTasks = highTasks,
            lowTasks = lowTasks,
            mediumTasks = mediumTasks,
            noneTasks = noneTasks,
            onTaskClick = onTaskClick,
            onToggleComplete = onToggleComplete,
            onQuadrantClick = onQuadrantClick,
        )
    }
}

@Composable
private fun MatrixHeader(
    onSettingsClick: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.eisenhower_matrix),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_settings),
                contentDescription = stringResource(Res.string.settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuadrantGrid(
    highTasks: List<Task>,
    lowTasks: List<Task>,
    mediumTasks: List<Task>,
    noneTasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onQuadrantClick: (TaskPriority) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.paddingMedium)
            .navigationBarsPadding()
            .padding(bottom = dimens.fabAreaBottom),
        verticalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
    ) {
        // Top row: Urgent & Important | Urgent & Unimportant
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
        ) {
            QuadrantCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.urgent_important),
                badge = "I",
                color = PriorityHigh,
                tasks = highTasks,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onCardClick = { onQuadrantClick(TaskPriority.HIGH) },
            )
            QuadrantCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.urgent_unimportant),
                badge = "III",
                color = PriorityLow,
                tasks = lowTasks,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onCardClick = { onQuadrantClick(TaskPriority.LOW) },
            )
        }

        // Bottom row: Not Urgent & Important | Not Urgent & Unimportant
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
        ) {
            QuadrantCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.not_urgent_important),
                badge = "II",
                color = PriorityMedium,
                tasks = mediumTasks,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onCardClick = { onQuadrantClick(TaskPriority.MEDIUM) },
            )
            QuadrantCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.not_urgent_unimportant),
                badge = "IV",
                color = PriorityNone,
                tasks = noneTasks,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onCardClick = { onQuadrantClick(TaskPriority.NONE) },
            )
        }

    }
}

@Composable
private fun QuadrantCard(
    modifier: Modifier,
    title: String,
    badge: String,
    color: Color,
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onCardClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val hasMore = tasks.size > QUADRANT_MAX_VISIBLE

    Card(
        onClick = onCardClick,
        modifier = modifier,
        shape = RoundedCornerShape(dimens.cornerXLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(dimens.paddingLarge)) {
            QuadrantCardHeader(title = title, badge = badge, color = color)

            Spacer(Modifier.height(dimens.spacerLarge))

            // Scrollable task list fills remaining space
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(tasks, key = { it.id }) { task ->
                    QuadrantTaskRow(
                        task = task,
                        color = color,
                        onToggleComplete = { onToggleComplete(task) },
                        onClick = { onTaskClick(task) },
                    )
                    Spacer(Modifier.height(dimens.spacerSmall))
                }
            }

            if (hasMore) {
                ViewMoreLabel()
            }
        }
    }
}

private const val QUADRANT_MAX_VISIBLE = 6

@Composable
private fun QuadrantCardHeader(
    title: String,
    badge: String,
    color: Color,
) {
    val dimens = OpenTasksTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(dimens.badgeSize)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badge,
                style = if (badge.length > 2) OpenTasksTheme.typography.quadrantBadgeSmall
                else OpenTasksTheme.typography.quadrantBadge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(dimens.spacerMedium))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ViewMoreLabel() {
    val dimens = OpenTasksTheme.dimens
    Text(
        text = stringResource(Res.string.view_more),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spacerSmall),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun QuadrantTaskRow(
    task: Task,
    color: Color,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square checkbox matching screenshot style
        Box(
            modifier = Modifier
                .size(dimens.checkboxSize)
                .then(
                    if (task.isCompleted) {
                        Modifier.background(color.copy(alpha = 0.4f), RoundedCornerShape(dimens.checkboxCorner))
                    } else {
                        Modifier.border(dimens.checkboxBorder, color, RoundedCornerShape(dimens.checkboxCorner))
                    }
                )
                .clickable(onClick = onToggleComplete),
            contentAlignment = Alignment.Center,
        ) {
            if (task.isCompleted) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimens.checkboxIconSize),
                )
            }
        }

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Show deadline date if present
            if (task.deadline != null && !task.isCompleted) {
                Text(
                    text = formatDateShort(task.deadline),
                    style = MaterialTheme.typography.labelSmall,
                    color = DateOrange,
                )
            }
        }
    }
}


@Composable
@Preview
private fun EisenhowerMatrixScreenPreview() {
    OpenTasksTheme {
        EisenhowerMatrixContent(
            tasksByPriority = PreviewSampleData.sampleTasks.groupBy { it.priority },
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@Preview
private fun QuadrantCardPreview() {
    OpenTasksTheme {
        QuadrantCard(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            title = "Urgent & Important",
            badge = "I",
            color = PriorityHigh,
            tasks = PreviewSampleData.sampleTasks.filter { it.priority == TaskPriority.HIGH },
            onTaskClick = {},
            onToggleComplete = {},
            onCardClick = {},
        )
    }
}

@Composable
@Preview
private fun QuadrantTaskRowPreview() {
    OpenTasksTheme {
        QuadrantTaskRow(
            task = PreviewSampleData.sampleTasks.first(),
            color = PriorityHigh,
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@Preview
private fun MatrixHeaderPreview() {
    OpenTasksTheme {
        MatrixHeader(onSettingsClick = {})
    }
}
