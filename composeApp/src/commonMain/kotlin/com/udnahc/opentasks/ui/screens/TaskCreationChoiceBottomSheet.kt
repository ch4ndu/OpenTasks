package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.full_task
import opentasks.composeapp.generated.resources.quick_add
import opentasks.composeapp.generated.resources.task_creation_choice_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreationChoiceBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onQuickAdd: () -> Unit,
    onFullTask: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        TaskCreationChoiceContent(
            onQuickAdd = onQuickAdd,
            onFullTask = onFullTask,
        )
    }
}

@Composable
internal fun TaskCreationChoiceContent(
    onQuickAdd: () -> Unit,
    onFullTask: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = OpenTasksTheme.dimens.paddingXLarge),
    ) {
        Text(
            text = stringResource(Res.string.task_creation_choice_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = OpenTasksTheme.dimens.paddingLarge),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.quick_add)) },
            modifier = Modifier
                .semantics { role = Role.Button }
                .clickable(onClick = onQuickAdd),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.full_task)) },
            modifier = Modifier
                .semantics { role = Role.Button }
                .clickable(onClick = onFullTask),
        )
    }
}
