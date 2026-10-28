package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.ui.theme.isReimagined
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.priorityColor
import com.udnahc.opentasks.viewmodel.ShortlistRow
import com.udnahc.opentasks.viewmodel.ShortlistUiState
import com.udnahc.opentasks.viewmodel.ShortlistViewModel
import opentasks.composeapp.generated.resources.bulk_tasks
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.shortlist
import opentasks.composeapp.generated.resources.shortlist_choose_tasks
import opentasks.composeapp.generated.resources.shortlist_due_outside
import opentasks.composeapp.generated.resources.shortlist_due_overdue
import opentasks.composeapp.generated.resources.shortlist_empty
import opentasks.composeapp.generated.resources.shortlist_more_options_description
import opentasks.composeapp.generated.resources.shortlist_remaining
import opentasks.composeapp.generated.resources.shortlist_remove_from
import opentasks.composeapp.generated.resources.shortlist_subtitle
import opentasks.composeapp.generated.resources.shortlist_to_tackle
import opentasks.composeapp.generated.resources.shortlist_view_today
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShortlistScreen(
    viewModel: ShortlistViewModel,
    onAllViewsClick: () -> Unit,
    onBulkTasks: (() -> Unit)? = null,
    onViewTodayClick: () -> Unit,
    onTaskClick: (Task) -> Unit,
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onTaskMutationFailure: () -> Unit = {},
    onModalBusyChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val pendingTaskIds by viewModel.pendingTaskIds.collectAsState()
    val taskPendingSeriesChoice by viewModel.taskPendingSeriesChoice.collectAsState()
    var showChooser by rememberSaveable { mutableStateOf(false) }
    ModalBusyEffect(taskPendingSeriesChoice != null || showChooser, onModalBusyChanged)

    ShortlistContent(
        state = state,
        pendingTaskIds = pendingTaskIds,
        onAllViewsClick = onAllViewsClick,
        onBulkTasks = onBulkTasks,
        onChooseTasksClick = { showChooser = true },
        onViewTodayClick = onViewTodayClick,
        onTaskClick = onTaskClick,
        onToggleComplete = { viewModel.toggleComplete(it) },
        onRemove = { viewModel.setShortlisted(it.id, false) },
        isRefreshing = isRefreshing,
        syncEnabled = syncEnabled,
        onRefresh = onRefresh,
    )

    if (showChooser) {
        val chooserState by viewModel.chooserState.collectAsState()
        ChooseShortlistTasksBottomSheet(
            state = chooserState,
            pendingTaskIds = pendingTaskIds,
            onAdd = { viewModel.setShortlisted(it.id, true) },
            onDismiss = { showChooser = false },
        )
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
internal fun ShortlistContent(
    state: ShortlistUiState,
    pendingTaskIds: Set<String>,
    onAllViewsClick: () -> Unit,
    onBulkTasks: (() -> Unit)? = null,
    onChooseTasksClick: () -> Unit,
    onViewTodayClick: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onRemove: (Task) -> Unit,
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    val navBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ShortlistHeader(
            remaining = state.toTackle.size,
            onAllViewsClick = onAllViewsClick,
        onBulkTasks = onBulkTasks,
            onChooseTasksClick = onChooseTasksClick,
        )
        SyncPullToRefresh(
            isRefreshing = isRefreshing,
            enabled = syncEnabled,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dimens.paddingLarge,
                    end = dimens.paddingLarge,
                    bottom = navBarHeight + dimens.fabAreaBottom + dimens.fabBottomPadding,
                ),
            ) {
                if (state.toTackle.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(Res.string.shortlist_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(dimens.paddingXLarge),
                        )
                    }
                } else {
                    item(key = "to_tackle_header") {
                        Text(
                            text = stringResource(Res.string.shortlist_to_tackle, state.toTackle.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .semantics { heading() }
                                .padding(horizontal = dimens.paddingMedium, vertical = dimens.paddingMedium),
                        )
                    }
                    items(state.toTackle, key = { it.task.id }) { row ->
                        ShortlistTaskRow(
                            row = row,
                            isPending = row.task.id in pendingTaskIds,
                            onClick = { onTaskClick(row.task) },
                            onToggleComplete = { onToggleComplete(row.task) },
                            onRemove = { onRemove(row.task) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = dimens.dividerThin,
                        )
                    }
                }
                if (state.dueOutsideCount > 0) {
                    item(key = "due_outside") {
                        DueOutsideCard(
                            count = state.dueOutsideCount,
                            onViewTodayClick = onViewTodayClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortlistHeader(
    remaining: Int,
    onAllViewsClick: () -> Unit,
    onBulkTasks: (() -> Unit)? = null,
    onChooseTasksClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    if (isReimagined) {
        Column {
            ReimaginedScreenHeader(stringResource(Res.string.shortlist), onAllViewsClick)
            Column(Modifier.padding(horizontal = dimens.paddingXLarge)) {
                ReimaginedSectionLabel(stringResource(Res.string.shortlist_remaining, remaining))
                Text(stringResource(Res.string.shortlist_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimens.paddingMedium)) {
                    OutlinedButton(onClick = onChooseTasksClick) { Text(stringResource(Res.string.shortlist_choose_tasks)) }
                    if (onBulkTasks != null) TextButton(onClick = onBulkTasks) { Text(stringResource(Res.string.bulk_tasks)) }
                }
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingSmall),
    ) {
        androidx.compose.foundation.layout.FlowRow {
            AllViewsButton(onClick = onAllViewsClick)
            if (onBulkTasks != null) TextButton(onClick = onBulkTasks) { Text(stringResource(Res.string.bulk_tasks)) }
        }
        TaskViewTitle(title = stringResource(Res.string.shortlist))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.shortlist_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.shortlist_remaining, remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(dimens.spacerLarge))
            OutlinedButton(onClick = onChooseTasksClick) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = null,
                    tint = if (isReimagined) MaterialTheme.colorScheme.primary else PrimaryBlue,
                    modifier = Modifier.size(dimens.iconDefault),
                )
                Spacer(Modifier.width(dimens.spacerSmall))
                Text(text = stringResource(Res.string.shortlist_choose_tasks), color = PrimaryBlue, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShortlistTaskRow(
    row: ShortlistRow,
    isPending: Boolean,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onRemove: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = dimens.paddingSmall, top = dimens.paddingSmall, bottom = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckboxButton(
            isChecked = false,
            tint = priorityColor(row.task.priority),
            onClick = onToggleComplete,
        )
        Spacer(Modifier.width(dimens.spacerLarge))
        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(title = row.task.title, isCompleted = false)
            ShortlistMetaText(row)
        }
        Box {
            val moreDescription = stringResource(Res.string.shortlist_more_options_description, row.task.title)
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.semantics { contentDescription = moreDescription },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_more_vert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.shortlist_remove_from)) },
                    enabled = !isPending,
                    onClick = {
                        showMenu = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
internal fun ShortlistMetaText(row: ShortlistRow) {
    if (row.metaText.isEmpty()) return
    Text(
        text = row.metaText,
        style = MaterialTheme.typography.labelMedium,
        color = if (row.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DueOutsideCard(
    count: Int,
    onViewTodayClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Surface(
        onClick = onViewTodayClick,
        shape = RoundedCornerShape(dimens.cornerXLarge),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(top = dimens.paddingXLarge),
    ) {
        Row(
            modifier = Modifier.padding(dimens.paddingLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.shortlist_due_overdue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.shortlist_due_outside, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = stringResource(Res.string.shortlist_view_today), color = if (isReimagined) MaterialTheme.colorScheme.primary else PrimaryBlue)
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = if (isReimagined) MaterialTheme.colorScheme.primary else PrimaryBlue,
                modifier = Modifier.size(dimens.iconDefault),
            )
        }
    }
}
