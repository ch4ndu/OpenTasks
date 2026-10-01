package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.StarGold
import com.udnahc.opentasks.viewmodel.ListCount
import com.udnahc.opentasks.viewmodel.SmartViewCount
import com.udnahc.opentasks.viewmodel.TaskOverviewState
import com.udnahc.opentasks.viewmodel.TaskOverviewViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.filter_due_this_week
import opentasks.composeapp.generated.resources.filter_high_priority
import opentasks.composeapp.generated.resources.filter_no_date
import opentasks.composeapp.generated.resources.filter_overdue
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_alarm
import opentasks.composeapp.generated.resources.ic_calendar
import opentasks.composeapp.generated.resources.ic_checklist
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_flag
import opentasks.composeapp.generated.resources.ic_inbox
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.ic_schedule
import opentasks.composeapp.generated.resources.ic_star
import opentasks.composeapp.generated.resources.new_list
import opentasks.composeapp.generated.resources.shortlist
import opentasks.composeapp.generated.resources.shortlist_remaining
import opentasks.composeapp.generated.resources.shortlist_subtitle
import opentasks.composeapp.generated.resources.starred
import opentasks.composeapp.generated.resources.tasks
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.unfinished_task_count
import opentasks.composeapp.generated.resources.your_lists
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TaskOverviewScreen(
    viewModel: TaskOverviewViewModel,
    onViewClick: (TaskListFilter) -> Unit,
    onSettingsClick: () -> Unit,
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
    onModalBusyChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showNewListDialog by remember { mutableStateOf(false) }
    ModalBusyEffect(showNewListDialog, onModalBusyChanged)

    TaskOverviewContent(
        state = state,
        onViewClick = onViewClick,
        onNewListClick = { showNewListDialog = true },
        onSettingsClick = onSettingsClick,
        isRefreshing = isRefreshing,
        syncEnabled = syncEnabled,
        onRefresh = onRefresh,
    )

    if (showNewListDialog) {
        AddCategoryDialog(
            onDismiss = { showNewListDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name)
                showNewListDialog = false
            },
        )
    }
}

@Composable
internal fun TaskOverviewContent(
    state: TaskOverviewState,
    onViewClick: (TaskListFilter) -> Unit,
    onNewListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isRefreshing: Boolean = false,
    syncEnabled: Boolean = true,
    onRefresh: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SyncPullToRefresh(
            isRefreshing = isRefreshing,
            enabled = syncEnabled,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = dimens.taskOverviewMaxWidth)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dimens.paddingXLarge,
                    end = dimens.paddingXLarge,
                    top = dimens.topBarHeight + statusBarHeight,
                    bottom = navBarHeight + dimens.fabAreaBottom + dimens.fabBottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacerXLarge),
                verticalArrangement = Arrangement.spacedBy(dimens.spacerXLarge),
            ) {
                item(key = "shortlist", span = { GridItemSpan(maxLineSpan) }) {
                    ShortlistCard(
                        remaining = state.shortlistCount,
                        onClick = { onViewClick(TaskListFilter.Shortlist) },
                    )
                }
                items(state.smartViews, key = { "view:${it.filter}" }) { view ->
                    SmartViewTile(view = view, onClick = { onViewClick(view.filter) })
                }
                item(key = "lists_header", span = { GridItemSpan(maxLineSpan) }) {
                    YourListsHeader(onNewListClick = onNewListClick)
                }
                items(
                    state.lists,
                    key = { "list:${it.category.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { list ->
                    ListCountRow(
                        list = list,
                        onClick = { onViewClick(TaskListFilter.Category(list.category.id)) },
                    )
                }
            }
        }

        OpenTasksTopBar(
            title = stringResource(Res.string.tasks),
            containerStyle = OpenTasksTopBarContainerStyle.Translucent,
            actions = { OpenTasksSettingsButton(onClick = onSettingsClick) },
        )
    }
}

@Composable
private fun SmartViewTile(
    view: SmartViewCount,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(dimens.cornerXLarge),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = dimens.minimumInteractiveTargetSize)
                .padding(dimens.paddingLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(taskListFilterIcon(view.filter)),
                contentDescription = null,
                tint = taskListFilterTint(view.filter),
                modifier = Modifier.size(dimens.iconXLarge),
            )
            Spacer(Modifier.width(dimens.spacerXLarge))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = taskListFilterTitle(view.filter),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                UnfinishedCountText(view.count)
            }
            ChevronIcon()
        }
    }
}

@Composable
private fun ShortlistCard(
    remaining: Int,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(dimens.cornerXLarge),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = dimens.minimumInteractiveTargetSize)
                .padding(dimens.paddingLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_checklist),
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(dimens.iconXLarge),
            )
            Spacer(Modifier.width(dimens.spacerXLarge))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.shortlist),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.shortlist_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(dimens.spacerLarge))
            Text(
                text = stringResource(Res.string.shortlist_remaining, remaining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(dimens.spacerLarge))
            ChevronIcon()
        }
    }
}

@Composable
private fun YourListsHeader(onNewListClick: () -> Unit) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.your_lists),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onNewListClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_add),
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(dimens.iconDefault),
            )
            Spacer(Modifier.width(dimens.spacerSmall))
            Text(text = stringResource(Res.string.new_list), color = PrimaryBlue)
        }
    }
}

@Composable
private fun ListCountRow(
    list: ListCount,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(dimens.cornerXLarge),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = dimens.minimumInteractiveTargetSize)
                .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (list.category.icon == "inbox") Res.drawable.ic_inbox else Res.drawable.ic_list
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.iconXLarge),
            )
            Spacer(Modifier.width(dimens.spacerXLarge))
            Text(
                text = list.category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(dimens.spacerLarge))
            UnfinishedCountText(list.count)
            Spacer(Modifier.width(dimens.spacerLarge))
            ChevronIcon()
        }
    }
}

@Composable
private fun UnfinishedCountText(count: Int) {
    val description = stringResource(Res.string.unfinished_task_count, count)
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun ChevronIcon() {
    Icon(
        painter = painterResource(Res.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(OpenTasksTheme.dimens.iconDefault),
    )
}

/** Title for a smart view; a category's title is its name and is resolved by the caller. */
@Composable
internal fun taskListFilterTitle(filter: TaskListFilter): String = when (filter) {
    is TaskListFilter.Category -> ""
    TaskListFilter.Shortlist -> stringResource(Res.string.shortlist)
    TaskListFilter.Today -> stringResource(Res.string.today)
    TaskListFilter.Overdue -> stringResource(Res.string.filter_overdue)
    TaskListFilter.Starred -> stringResource(Res.string.starred)
    TaskListFilter.NoDate -> stringResource(Res.string.filter_no_date)
    TaskListFilter.HighPriority -> stringResource(Res.string.filter_high_priority)
    TaskListFilter.DueThisWeek -> stringResource(Res.string.filter_due_this_week)
}

private fun taskListFilterIcon(filter: TaskListFilter): DrawableResource = when (filter) {
    is TaskListFilter.Category -> Res.drawable.ic_list
    TaskListFilter.Shortlist -> Res.drawable.ic_checklist
    TaskListFilter.Today, TaskListFilter.Overdue -> Res.drawable.ic_alarm
    TaskListFilter.Starred -> Res.drawable.ic_star
    TaskListFilter.NoDate -> Res.drawable.ic_schedule
    TaskListFilter.HighPriority -> Res.drawable.ic_flag
    TaskListFilter.DueThisWeek -> Res.drawable.ic_calendar
}

@Composable
private fun taskListFilterTint(filter: TaskListFilter): Color = when (filter) {
    TaskListFilter.Shortlist, TaskListFilter.Today, TaskListFilter.DueThisWeek -> PrimaryBlue
    TaskListFilter.Overdue, TaskListFilter.HighPriority -> PriorityHigh
    TaskListFilter.Starred -> StarGold
    is TaskListFilter.Category, TaskListFilter.NoDate -> MaterialTheme.colorScheme.onSurfaceVariant
}
