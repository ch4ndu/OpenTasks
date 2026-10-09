package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.domain.action.task.*
import com.udnahc.opentasks.domain.usecase.task.ReviewStep
import com.udnahc.opentasks.viewmodel.*
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import opentasks.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
private fun WorkflowLayout(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        OpenTasksTopBar(title = title, navigationIcon = { OpenTasksBackButton(onClick = onBack) })
        content()
    }
}

@Composable
private fun WorkflowMessage(text: String) {
    Text(text, modifier = Modifier.padding(OpenTasksTheme.dimens.paddingLarge), style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun WorkflowTaskItem(row: WorkflowTaskRow, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(row.task.title) },
        supportingContent = { Text(row.detail) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun WeeklyReviewScreen(viewModel: WeeklyReviewViewModel, onBack: () -> Unit, onTaskClick: (Task) -> Unit, onFailure: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val pending by viewModel.pendingIds.collectAsState()
    val choice by viewModel.completion.taskPendingSeriesChoice.collectAsState()
    WeeklyReviewContent(state, pending, onBack, onTaskClick, viewModel::selectStep, viewModel::complete, viewModel::setShortlisted)
    if (choice != null) CompleteSeriesDialog(viewModel.completion::completeOccurrence, viewModel.completion::completeSeries, viewModel.completion::dismissSeriesChoice)
    TaskMutationFailureEffect(viewModel.failure, viewModel::consumeFailure, onFailure)
}

@Composable
private fun WeeklyReviewContent(state: WeeklyReviewState, pending: Set<String>, onBack: () -> Unit, onTaskClick: (Task) -> Unit, onStep: (ReviewStep) -> Unit, onComplete: (Task) -> Unit, onShortlist: (Task) -> Unit) {
    WorkflowLayout(stringResource(Res.string.weekly_review), onBack) {
        WorkflowMessage(stringResource(Res.string.review_step, state.step.ordinal + 1, ReviewStep.entries.size))
        Text(stringResource(when (state.step) {
            ReviewStep.INBOX -> Res.string.review_inbox
            ReviewStep.OVERDUE -> Res.string.review_overdue
            ReviewStep.IMPORTANT -> Res.string.review_important
            ReviewStep.SHORTLIST -> Res.string.review_shortlist
        }), Modifier.padding(horizontal = OpenTasksTheme.dimens.paddingLarge), style = MaterialTheme.typography.titleLarge)
        WorkflowMessage(stringResource(when (state.step) {
            ReviewStep.INBOX -> Res.string.review_inbox_help
            ReviewStep.OVERDUE -> Res.string.review_overdue_help
            ReviewStep.IMPORTANT -> Res.string.review_important_help
            ReviewStep.SHORTLIST -> Res.string.review_shortlist_help
        }))
        LazyColumn(Modifier.weight(1f)) {
            if (state.rows.isEmpty()) item { WorkflowMessage(stringResource(Res.string.workflow_empty)) }
            items(state.rows, key = { it.task.id }) { row ->
                Column {
                    WorkflowTaskItem(row, { onTaskClick(row.task) })
                    FlowRow(Modifier.padding(horizontal = OpenTasksTheme.dimens.paddingMedium)) {
                        TextButton(onClick = { onComplete(row.task) }) { Text(stringResource(Res.string.workflow_done)) }
                        TextButton(onClick = { onShortlist(row.task) }, enabled = row.task.id !in pending) {
                            Text(stringResource(if (row.task.isShortlisted) Res.string.shortlist_remove_from else Res.string.shortlist_add_to))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(OpenTasksTheme.dimens.paddingMedium), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onStep(ReviewStep.entries[state.step.ordinal - 1]) }, enabled = state.step != ReviewStep.INBOX) { Text(stringResource(Res.string.review_previous)) }
            Button(onClick = { if (state.step == ReviewStep.SHORTLIST) onBack() else onStep(ReviewStep.entries[state.step.ordinal + 1]) }) {
                Text(stringResource(if (state.step == ReviewStep.SHORTLIST) Res.string.review_finish else Res.string.review_next))
            }
        }
    }
}

@Composable
fun LogbookScreen(viewModel: LogbookViewModel, onBack: () -> Unit, onTaskClick: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    LogbookContent(state, onBack, onTaskClick, viewModel::setPeriod, viewModel::setCategory)
}

@Composable
private fun LogbookContent(state: LogbookState, onBack: () -> Unit, onTaskClick: (String) -> Unit, onPeriod: (LogbookPeriod) -> Unit, onCategory: (String?) -> Unit) {
    var showLists by remember { mutableStateOf(false) }
    WorkflowLayout(stringResource(Res.string.completion_logbook), onBack) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                WorkflowMessage(stringResource(Res.string.logbook_help))
                FlowRow(Modifier.padding(horizontal = OpenTasksTheme.dimens.paddingMedium)) {
                    LogbookPeriod.entries.forEach { period ->
                        FilterChip(selected = period == state.period, onClick = { onPeriod(period) }, label = { Text(stringResource(when(period) {
                            LogbookPeriod.WEEK -> Res.string.logbook_week
                            LogbookPeriod.MONTH -> Res.string.logbook_month
                            LogbookPeriod.ALL -> Res.string.logbook_all
                        })) })
                    }
                    TextButton(onClick = { showLists = true }) { Text(state.categoryName.ifBlank { stringResource(Res.string.logbook_all_lists) }) }
                }
                WorkflowMessage(stringResource(Res.string.logbook_count, state.rows.size))
            }
            if (state.rows.isEmpty()) item { WorkflowMessage(stringResource(Res.string.workflow_empty)) }
            items(state.rows, key = { it.entry.id }) { row ->
                ListItem(headlineContent = { Text(row.entry.title) }, supportingContent = {
                    Column {
                        Text(row.dateText)
                        Text(row.entry.categoryName)
                        if (!row.taskAvailable) Text(stringResource(Res.string.logbook_missing_task))
                    }
                }, modifier = if (row.taskAvailable) Modifier.clickable { onTaskClick(row.entry.taskId) } else Modifier)
            }
        }
    }
    if (showLists) AlertDialog(onDismissRequest = { showLists = false }, title = { Text(stringResource(Res.string.logbook_all_lists)) }, text = {
        LazyColumn {
            item { TextButton(onClick = { onCategory(null); showLists = false }) { Text(stringResource(Res.string.logbook_all_lists)) } }
            items(state.categories, key = { it.id }) { category ->
                TextButton(onClick = { onCategory(category.id); showLists = false }) { Text(category.name) }
            }
        }
    }, confirmButton = { TextButton(onClick = { showLists = false }) { Text(stringResource(Res.string.cancel)) } })
}

@Composable
fun BulkTasksScreen(viewModel: BulkTasksViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val result by viewModel.result.collectAsState()
    BulkTasksContent(state, busy, result, onBack, viewModel::toggle, viewModel::selectAll, viewModel::clear, viewModel::apply)
}

@Composable
private fun BulkTasksContent(state: BulkTasksState, busy: Boolean, result: BulkTaskResult?, onBack: () -> Unit, onToggle: (String) -> Unit, onAll: () -> Unit, onClear: () -> Unit, onApply: (BulkTaskChange) -> Unit) {
    var showMove by remember { mutableStateOf(false) }
    var showPriority by remember { mutableStateOf(false) }
    WorkflowLayout(stringResource(Res.string.bulk_tasks), onBack) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                WorkflowMessage(stringResource(Res.string.bulk_help))
                FlowRow(Modifier.padding(horizontal = OpenTasksTheme.dimens.paddingMedium)) {
                    TextButton(onClick = onAll, enabled = !busy) { Text(stringResource(Res.string.bulk_select_all)) }
                    TextButton(onClick = onClear, enabled = !busy) { Text(stringResource(Res.string.bulk_clear)) }
                }
                WorkflowMessage(stringResource(Res.string.bulk_selected, state.selected.size))
                if (result != null) WorkflowMessage(stringResource(Res.string.bulk_result, result.succeeded.size, result.failed.size))
                if (busy) WorkflowMessage(stringResource(Res.string.bulk_busy))
            }
            if (state.rows.isEmpty()) item { WorkflowMessage(stringResource(Res.string.workflow_empty)) }
            items(state.rows, key = { it.task.id }) { row ->
                ListItem(headlineContent = { Text(row.task.title) }, supportingContent = { Text(row.detail) },
                    leadingContent = { Checkbox(checked = row.task.id in state.selected, onCheckedChange = null, enabled = !busy) },
                    modifier = Modifier.toggleable(value = row.task.id in state.selected, enabled = !busy, role = Role.Checkbox, onValueChange = { onToggle(row.task.id) }))
            }
        }
        FlowRow(Modifier.padding(OpenTasksTheme.dimens.paddingMedium)) {
            val enabled = state.selected.isNotEmpty() && !busy
            TextButton(onClick = { showMove = true }, enabled = enabled) { Text(stringResource(Res.string.bulk_move)) }
            TextButton(onClick = { showPriority = true }, enabled = enabled) { Text(stringResource(Res.string.bulk_priority)) }
            TextButton(onClick = { onApply(BulkTaskChange.Shortlist(true)) }, enabled = enabled) { Text(stringResource(Res.string.shortlist_add_to)) }
            TextButton(onClick = { onApply(BulkTaskChange.Shortlist(false)) }, enabled = enabled) { Text(stringResource(Res.string.shortlist_remove_from)) }
        }
    }
    if (showMove) AlertDialog(onDismissRequest = { showMove = false }, title = { Text(stringResource(Res.string.bulk_move)) }, text = {
        LazyColumn { items(state.categories, key = { it.id }) { category ->
            TextButton(onClick = { showMove = false; onApply(BulkTaskChange.Move(category.id)) }) { Text(category.name) }
        } }
    }, confirmButton = { TextButton(onClick = { showMove = false }) { Text(stringResource(Res.string.cancel)) } })
    if (showPriority) AlertDialog(onDismissRequest = { showPriority = false }, title = { Text(stringResource(Res.string.bulk_priority)) }, text = {
        Column { TaskPriority.entries.forEach { priority ->
            TextButton(onClick = { showPriority = false; onApply(BulkTaskChange.Priority(priority)) }) { Text(stringResource(when (priority) { TaskPriority.HIGH -> Res.string.high_priority; TaskPriority.MEDIUM -> Res.string.medium_priority; TaskPriority.LOW -> Res.string.low_priority; TaskPriority.NONE -> Res.string.no_priority })) }
        } }
    }, confirmButton = { TextButton(onClick = { showPriority = false }) { Text(stringResource(Res.string.cancel)) } })
}

@Composable
fun NoteTasksScreen(viewModel: NoteTasksViewModel, onBack: () -> Unit, onTaskClick: (Task) -> Unit, onCreate: (Note) -> Unit, onOpenNote: (Note) -> Unit) {
    val state by viewModel.state.collectAsState()
    NoteTasksContent(state, onBack, onTaskClick, onCreate, onOpenNote)
}

@Composable
private fun NoteTasksContent(state: NoteTasksState, onBack: () -> Unit, onTaskClick: (Task) -> Unit, onCreate: (Note) -> Unit, onOpenNote: (Note) -> Unit) {
    WorkflowLayout(stringResource(Res.string.linked_tasks), onBack) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                if (state.loading) CircularProgressIndicator()
                else {
                    val note = state.note
                    if (note == null) WorkflowMessage(stringResource(Res.string.note_unavailable))
                    else {
                        WorkflowMessage(note.title)
                        FlowRow(Modifier.padding(horizontal = OpenTasksTheme.dimens.paddingMedium)) {
                            TextButton(onClick = { onOpenNote(note) }) { Text(stringResource(Res.string.note_edit_source)) }
                            TextButton(onClick = { onCreate(note) }) { Text(stringResource(Res.string.note_create_task)) }
                        }
                    }
                }
            }
            if (!state.loading && state.rows.isEmpty()) item { WorkflowMessage(stringResource(Res.string.workflow_empty)) }
            items(state.rows, key = { it.task.id }) { row ->
                WorkflowTaskItem(row, { onTaskClick(row.task) }, trailing = {
                    if (row.task.status == TaskStatus.DONE) Text(stringResource(Res.string.workflow_completed))
                })
            }
        }
    }
}
