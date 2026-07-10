package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.TaskNotificationUiState
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.edit
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.ic_open_in_new
import opentasks.composeapp.generated.resources.loading
import opentasks.composeapp.generated.resources.task_notification_due
import opentasks.composeapp.generated.resources.task_notification_got_it
import opentasks.composeapp.generated.resources.task_notification_mark_done
import opentasks.composeapp.generated.resources.task_notification_missing
import opentasks.composeapp.generated.resources.task_notification_notified
import opentasks.composeapp.generated.resources.task_notification_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskNotificationBottomSheet(
    sheetState: SheetState,
    uiState: TaskNotificationUiState,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit,
    onGotIt: () -> Unit,
    onEdit: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        TaskNotificationBottomSheetContent(
            uiState = uiState,
            onMarkDone = onMarkDone,
            onGotIt = onGotIt,
            onEdit = onEdit,
        )
    }
}

@Composable
internal fun TaskNotificationBottomSheetContent(
    uiState: TaskNotificationUiState,
    onMarkDone: () -> Unit,
    onGotIt: () -> Unit,
    onEdit: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val hasTask = uiState.task != null
    val hasEvent = uiState.event != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingLarge)
            .padding(bottom = dimens.paddingXXLarge),
    ) {
        Text(
            text = stringResource(Res.string.task_notification_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(dimens.spacerMedium))
        Text(
            text = when {
                hasTask -> uiState.taskTitle
                hasEvent -> stringResource(Res.string.task_notification_missing)
                else -> stringResource(Res.string.loading)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (uiState.notificationTimeText.isNotBlank() || uiState.dueText.isNotBlank()) {
            Spacer(Modifier.height(dimens.spacerLarge))
            if (uiState.notificationTimeText.isNotBlank()) {
                DetailLine(
                    label = stringResource(Res.string.task_notification_notified),
                    value = uiState.notificationTimeText,
                )
            }
            if (uiState.dueText.isNotBlank()) {
                DetailLine(
                    label = stringResource(Res.string.task_notification_due),
                    value = uiState.dueText,
                )
            }
        }
        Spacer(Modifier.height(dimens.paddingLarge))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacerMedium),
        ) {
            Button(
                onClick = onMarkDone,
                enabled = hasTask && !uiState.isBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                )
                Spacer(Modifier.width(dimens.spacerSmall))
                Text(stringResource(Res.string.task_notification_mark_done))
            }
            OutlinedButton(
                onClick = onGotIt,
                enabled = hasEvent && !uiState.isBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = null,
                )
                Spacer(Modifier.width(dimens.spacerSmall))
                Text(stringResource(Res.string.task_notification_got_it))
            }
        }
        TextButton(
            onClick = onEdit,
            enabled = hasTask && !uiState.isBusy,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = null,
            )
            Spacer(Modifier.width(dimens.spacerSmall))
            Text(stringResource(Res.string.edit))
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacerMedium),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
        )
    }
}
