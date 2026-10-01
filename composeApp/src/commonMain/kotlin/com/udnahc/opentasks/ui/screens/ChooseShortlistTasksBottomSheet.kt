package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.usecase.task.ShortlistSuggestion
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.viewmodel.ShortlistChooserState
import com.udnahc.opentasks.viewmodel.ShortlistRow
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.not_urgent_important
import opentasks.composeapp.generated.resources.shortlist_add_task_description
import opentasks.composeapp.generated.resources.shortlist_all_tasks
import opentasks.composeapp.generated.resources.shortlist_choose_tasks
import opentasks.composeapp.generated.resources.shortlist_group_due_today
import opentasks.composeapp.generated.resources.shortlist_group_no_date
import opentasks.composeapp.generated.resources.shortlist_no_candidates
import opentasks.composeapp.generated.resources.shortlist_sheet_description
import opentasks.composeapp.generated.resources.shortlist_sheet_footer
import opentasks.composeapp.generated.resources.shortlist_suggestions
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseShortlistTasksBottomSheet(
    state: ShortlistChooserState,
    pendingTaskIds: Set<String>,
    onAdd: (Task) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ChooseShortlistTasksContent(
            state = state,
            pendingTaskIds = pendingTaskIds,
            onAdd = onAdd,
            onDone = onDismiss,
        )
    }
}

@Composable
internal fun ChooseShortlistTasksContent(
    state: ShortlistChooserState,
    pendingTaskIds: Set<String>,
    onAdd: (Task) -> Unit,
    onDone: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    var showAllTasks by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingXLarge)
            .padding(bottom = dimens.paddingXLarge),
    ) {
        Text(
            text = stringResource(Res.string.shortlist_choose_tasks),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.shortlist_sheet_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(dimens.spacerXLarge))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(false, true).forEachIndexed { index, isAllTasks ->
                SegmentedButton(
                    selected = showAllTasks == isAllTasks,
                    onClick = { showAllTasks = isAllTasks },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = PrimaryBlue,
                        activeContentColor = Color.White,
                    ),
                    icon = {},
                ) {
                    Text(
                        text = stringResource(
                            if (isAllTasks) Res.string.shortlist_all_tasks else Res.string.shortlist_suggestions
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        val isEmpty = if (showAllTasks) state.allTasks.isEmpty() else state.suggestions.isEmpty()
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            if (isEmpty) {
                item(key = "empty") {
                    Text(
                        text = stringResource(Res.string.shortlist_no_candidates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = dimens.paddingXLarge),
                    )
                }
            } else if (showAllTasks) {
                items(state.allTasks, key = { "all:${it.task.id}" }) { row ->
                    CandidateRow(row, row.task.id in pendingTaskIds, onAdd)
                }
            } else {
                state.suggestions.forEach { group ->
                    item(key = "group:${group.suggestion}") {
                        SuggestionGroupHeader(group.suggestion)
                    }
                    items(group.rows, key = { "suggestion:${it.task.id}" }) { row ->
                        CandidateRow(row, row.task.id in pendingTaskIds, onAdd)
                    }
                }
            }
        }
        Text(
            text = stringResource(Res.string.shortlist_sheet_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = dimens.paddingMedium),
        )
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.done))
        }
    }
}

@Composable
private fun SuggestionGroupHeader(suggestion: ShortlistSuggestion) {
    Text(
        text = stringResource(
            when (suggestion) {
                ShortlistSuggestion.IMPORTANT_NOT_URGENT -> Res.string.not_urgent_important
                ShortlistSuggestion.DUE_TODAY -> Res.string.shortlist_group_due_today
                ShortlistSuggestion.NO_DATE -> Res.string.shortlist_group_no_date
            }
        ),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .semantics { heading() }
            .padding(top = OpenTasksTheme.dimens.paddingXLarge, bottom = OpenTasksTheme.dimens.paddingSmall),
    )
}

@Composable
private fun CandidateRow(
    row: ShortlistRow,
    isPending: Boolean,
    onAdd: (Task) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(title = row.task.title, isCompleted = false, activeColor = MaterialTheme.colorScheme.onSurface)
            ShortlistMetaText(row)
        }
        val addDescription = stringResource(Res.string.shortlist_add_task_description, row.task.title)
        TextButton(
            onClick = { onAdd(row.task) },
            enabled = !isPending,
            modifier = Modifier.semantics { contentDescription = addDescription },
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(dimens.iconDefault),
            )
            Spacer(Modifier.width(dimens.spacerSmall))
            Text(stringResource(Res.string.add))
        }
    }
}
