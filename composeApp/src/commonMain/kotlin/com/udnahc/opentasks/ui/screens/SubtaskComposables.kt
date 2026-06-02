package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_subtask
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubtaskList(
    subtasks: List<SubtaskItem>,
    onSubtaskTextChange: (String, String) -> Unit,
    onSubtaskCheckedChange: (String, Boolean) -> Unit,
    onDeleteSubtask: (String) -> Unit,
    onAddSubtask: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    LazyColumn {
        items(subtasks, key = { it.id }) { subtask ->
            SubtaskRow(
                subtask = subtask,
                onTextChange = { onSubtaskTextChange(subtask.id, it) },
                onCheckedChange = { onSubtaskCheckedChange(subtask.id, it) },
                onDelete = { onDeleteSubtask(subtask.id) },
                focusRequester = if (subtasks.firstOrNull()?.id == subtask.id) firstItemFocusRequester else null,
            )
        }
        item {
            val dimens = OpenTasksTheme.dimens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddSubtask)
                    .padding(vertical = dimens.paddingMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimens.iconDefault),
                )
                Spacer(Modifier.width(dimens.spacerXLarge))
                Text(
                    stringResource(Res.string.add_subtask),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    subtask: SubtaskItem,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = subtask.isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedColor = PrimaryBlue,
            ),
            modifier = Modifier.size(dimens.priorityIndicatorSize),
        )
        Spacer(Modifier.width(dimens.spacerLarge))
        BasicTextField(
            value = subtask.text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(PrimaryBlue),
            modifier = Modifier.weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(dimens.iconXLarge)) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.delete),
                tint = PriorityHigh,
                modifier = Modifier.size(dimens.iconSmall),
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = dimens.dividerThin
    )
}
