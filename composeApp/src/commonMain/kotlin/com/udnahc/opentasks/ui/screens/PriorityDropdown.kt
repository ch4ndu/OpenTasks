package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.PriorityLow
import com.udnahc.opentasks.ui.theme.PriorityMedium
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.high_priority
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_flag
import opentasks.composeapp.generated.resources.low_priority
import opentasks.composeapp.generated.resources.medium_priority
import opentasks.composeapp.generated.resources.no_priority
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PriorityDropdown(
    expanded: Boolean,
    currentPriority: TaskPriority,
    onDismiss: () -> Unit,
    onSelected: (TaskPriority) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        PriorityMenuItem(
            TaskPriority.HIGH,
            stringResource(Res.string.high_priority),
            PriorityHigh,
            currentPriority,
            onSelected
        )
        PriorityMenuItem(
            TaskPriority.MEDIUM,
            stringResource(Res.string.medium_priority),
            PriorityMedium,
            currentPriority,
            onSelected
        )
        PriorityMenuItem(
            TaskPriority.LOW,
            stringResource(Res.string.low_priority),
            PriorityLow,
            currentPriority,
            onSelected
        )
        PriorityMenuItem(
            TaskPriority.NONE,
            stringResource(Res.string.no_priority),
            MaterialTheme.colorScheme.onSurfaceVariant,
            currentPriority,
            onSelected
        )
    }
}

@Composable
private fun PriorityMenuItem(
    priority: TaskPriority,
    label: String,
    color: Color,
    currentPriority: TaskPriority,
    onSelected: (TaskPriority) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.ic_flag),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(dimens.iconDefault),
                )
                Spacer(Modifier.width(dimens.spacerXLarge))
                Text(label, color = MaterialTheme.colorScheme.onBackground)
                if (priority == currentPriority) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(dimens.iconDefault),
                    )
                }
            }
        },
        onClick = { onSelected(priority) },
    )
}
