package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
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
import opentasks.composeapp.generated.resources.ic_settings
import opentasks.composeapp.generated.resources.not_urgent_important
import opentasks.composeapp.generated.resources.not_urgent_unimportant
import opentasks.composeapp.generated.resources.settings
import opentasks.composeapp.generated.resources.urgent_important
import opentasks.composeapp.generated.resources.urgent_unimportant
import opentasks.composeapp.generated.resources.view_more
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun EisenhowerMatrixScreen(
    viewModel: MatrixViewModel,
    onTaskClick: (Task) -> Unit,
    onQuadrantClick: (TaskPriority) -> Unit,
    onSettingsClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onTaskMutationFailure: () -> Unit = {},
) {
    val priorityProjections by viewModel.priorityProjections.collectAsState()
    val taskImageSummaries by viewModel.taskImageSummaries.collectAsState()
    val taskDueTextById by viewModel.taskDueTextById.collectAsState()
    val taskPendingSeriesChoice by viewModel.taskPendingSeriesChoice.collectAsState()

    EisenhowerMatrixContent(
        priorityProjections = priorityProjections,
        taskImageSummaries = taskImageSummaries,
        taskDueTextById = taskDueTextById,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onQuadrantClick = onQuadrantClick,
        onSettingsClick = onSettingsClick,
        isRefreshing = isRefreshing,
        syncEnabled = syncEnabled,
        onRefresh = onRefresh,
    )

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
internal fun EisenhowerMatrixContent(
    priorityProjections: Map<TaskPriority, MatrixViewModel.PriorityProjection>,
    taskImageSummaries: Map<String, AttachmentSummary> = emptyMap(),
    taskDueTextById: Map<String, String> = emptyMap(),
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onQuadrantClick: (TaskPriority) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
) {
    SyncPullToRefresh(
        isRefreshing = isRefreshing,
        enabled = syncEnabled,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            MatrixHeader(onSettingsClick = onSettingsClick)

            // 2x2 Grid
            val highTasks = priorityProjections[TaskPriority.HIGH] ?: MatrixViewModel.PriorityProjection()
            val lowTasks = priorityProjections[TaskPriority.LOW] ?: MatrixViewModel.PriorityProjection()
            val mediumTasks = priorityProjections[TaskPriority.MEDIUM] ?: MatrixViewModel.PriorityProjection()
            val noneTasks = priorityProjections[TaskPriority.NONE] ?: MatrixViewModel.PriorityProjection()

            QuadrantGrid(
                highTasks = highTasks,
                lowTasks = lowTasks,
                mediumTasks = mediumTasks,
                noneTasks = noneTasks,
                taskImageSummaries = taskImageSummaries,
                taskDueTextById = taskDueTextById,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onQuadrantClick = onQuadrantClick,
            )
        }
    }
}

@Composable
internal fun MatrixHeader(
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
    highTasks: MatrixViewModel.PriorityProjection,
    lowTasks: MatrixViewModel.PriorityProjection,
    mediumTasks: MatrixViewModel.PriorityProjection,
    noneTasks: MatrixViewModel.PriorityProjection,
    taskImageSummaries: Map<String, AttachmentSummary>,
    taskDueTextById: Map<String, String>,
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
                priorityProjection = highTasks,
                taskImageSummaries = taskImageSummaries,
                taskDueTextById = taskDueTextById,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onCardClick = { onQuadrantClick(TaskPriority.HIGH) },
            )
            QuadrantCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.urgent_unimportant),
                badge = "III",
                color = PriorityLow,
                priorityProjection = lowTasks,
                taskImageSummaries = taskImageSummaries,
                taskDueTextById = taskDueTextById,
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
                priorityProjection = mediumTasks,
                taskImageSummaries = taskImageSummaries,
                taskDueTextById = taskDueTextById,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onCardClick = { onQuadrantClick(TaskPriority.MEDIUM) },
            )
            QuadrantCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.not_urgent_unimportant),
                badge = "IV",
                color = PriorityNone,
                priorityProjection = noneTasks,
                taskImageSummaries = taskImageSummaries,
                taskDueTextById = taskDueTextById,
                onTaskClick = onTaskClick,
                onToggleComplete = onToggleComplete,
                onCardClick = { onQuadrantClick(TaskPriority.NONE) },
            )
        }

    }
}

@Composable
internal fun QuadrantCard(
    modifier: Modifier,
    title: String,
    badge: String,
    color: Color,
    priorityProjection: MatrixViewModel.PriorityProjection,
    taskImageSummaries: Map<String, AttachmentSummary> = emptyMap(),
    taskDueTextById: Map<String, String> = emptyMap(),
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onCardClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
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

            // Task list limited to visible count; "View more" navigates to detail
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(priorityProjection.visibleTasks, key = { it.id }) { task ->
                    QuadrantTaskRow(
                        task = task,
                        imageSummary = taskImageSummaries[task.id],
                        dueText = taskDueTextById[task.id].orEmpty(),
                        color = color,
                        onToggleComplete = { onToggleComplete(task) },
                        onClick = { onTaskClick(task) },
                    )
                    Spacer(Modifier.height(dimens.spacerSmall))
                }
            }

            if (priorityProjection.hasMore) {
                ViewMoreLabel()
            }
        }
    }
}

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
internal fun QuadrantTaskRow(
    task: Task,
    imageSummary: AttachmentSummary? = null,
    dueText: String = "",
    color: Color,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val isCompleted = task.status == TaskStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square checkbox matching screenshot style
        TaskSquareCompletionButton(
            isChecked = isCompleted,
            onClick = onToggleComplete,
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.checkboxSize)
                    .then(
                        if (isCompleted) {
                            Modifier.background(
                                color.copy(alpha = 0.4f),
                                RoundedCornerShape(dimens.checkboxCorner)
                            )
                        } else {
                            Modifier.border(
                                dimens.checkboxBorder,
                                color,
                                RoundedCornerShape(dimens.checkboxCorner)
                            )
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimens.checkboxIconSize),
                    )
                }
            }
        }

        Spacer(Modifier.width(dimens.spacerLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (task.status == TaskStatus.DONE) MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.4f
                )
                else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Show deadline date if present
            if (dueText.isNotBlank() && task.status != TaskStatus.DONE) {
                Text(
                    text = dueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = DateOrange,
                )
            }
            if (imageSummary != null && imageSummary.imageCount > 0) {
                Spacer(Modifier.height(dimens.spacerTiny))
                AttachmentSyncBadge(imageSummary.worstSyncState)
            }
        }
    }
}
