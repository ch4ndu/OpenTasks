package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import opentasks.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Overlay rail; navigation content retains its parent and lifecycle. */
@Composable
internal fun ReimaginedNavigation(selectedTab: Int, onTabSelected: (Int) -> Unit, onSettings: () -> Unit) {
    val d = OpenTasksTheme.dimens
    Surface(Modifier.fillMaxHeight().width(d.reimaginedRailWidth), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(d.paddingMedium), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = d.paddingLarge))
            val labels = listOf(Res.string.matrix, Res.string.tasks, Res.string.calendar, Res.string.notes, Res.string.countdown_title)
            val icons = listOf(Res.drawable.ic_grid_view, Res.drawable.ic_check_box, Res.drawable.ic_calendar, Res.drawable.ic_note, Res.drawable.ic_schedule)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                labels.forEachIndexed { index, label ->
                    NavigationRailItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        icon = { Icon(painterResource(icons[index]), contentDescription = null) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.padding(vertical = d.paddingSmall),
                    )
                }
            }
            OpenTasksSettingsButton(onClick = onSettings)
        }
    }
}

@Composable
internal fun ReimaginedAddButton(onClick: () -> Unit, label: StringResource = Res.string.add_task) {
    IconButton(onClick = onClick) {
        Icon(painterResource(Res.drawable.ic_add), stringResource(label), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
internal fun ReimaginedReadStatus(loading: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(OpenTasksTheme.dimens.paddingXXLarge), horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) CircularProgressIndicator() else {
                Text(stringResource(Res.string.saved_items_load_failed), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.try_again)) }
            }
            if (loading) Text(stringResource(Res.string.loading), modifier = Modifier.padding(top = OpenTasksTheme.dimens.paddingLarge))
        }
    }
}


@Composable
internal fun ReimaginedSyncFailure(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = OpenTasksTheme.dimens.paddingLarge), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.sync_saved_items_available), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.try_again)) }
        }
    }
}

/** Shared presentation only; callers keep their draft, navigation and mutation owners. */
@Composable
internal fun ReimaginedScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    OpenTasksTopBar(
        title = title,
        navigationIcon = { if (onBack != null) OpenTasksBackButton(onBack) },
        actions = actions,
    )
}

@Composable
internal fun ReimaginedSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(vertical = OpenTasksTheme.dimens.paddingMedium), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ReimaginedPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(OpenTasksTheme.dimens.cornerXLarge), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(OpenTasksTheme.dimens.paddingLarge), content = content)
    }
}

@Composable
internal fun ReimaginedViewModeControl(mode: com.udnahc.opentasks.data.model.TaskListViewMode, onSelected: (com.udnahc.opentasks.data.model.TaskListViewMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.paddingSmall)) {
        com.udnahc.opentasks.data.model.TaskListViewMode.entries.forEach { candidate ->
            FilterChip(selected = candidate == mode, onClick = { onSelected(candidate) }, label = {
                Text(stringResource(if (candidate == com.udnahc.opentasks.data.model.TaskListViewMode.LIST) Res.string.task_view_list else Res.string.task_view_board))
            }, modifier = Modifier.heightIn(min = OpenTasksTheme.dimens.minimumInteractiveTargetSize))
        }
    }
}
