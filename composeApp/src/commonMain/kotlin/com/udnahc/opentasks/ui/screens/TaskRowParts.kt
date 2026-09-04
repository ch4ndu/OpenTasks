package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.StarGold
import com.udnahc.opentasks.ui.theme.minimumInteractiveTargetSize
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_check_box_outline
import opentasks.composeapp.generated.resources.ic_star
import opentasks.composeapp.generated.resources.task_add_star
import opentasks.composeapp.generated.resources.task_mark_complete
import opentasks.composeapp.generated.resources.task_mark_incomplete
import opentasks.composeapp.generated.resources.task_remove_star
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TaskCheckboxButton(
    isChecked: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    val actionDescription = stringResource(
        if (isChecked) Res.string.task_mark_incomplete else Res.string.task_mark_complete
    )
    IconToggleButton(
        checked = isChecked,
        onCheckedChange = { onClick() },
        modifier = modifier.minimumInteractiveTargetSize(),
    ) {
        Icon(
            painter = painterResource(
                if (isChecked) Res.drawable.ic_check_box else Res.drawable.ic_check_box_outline
            ),
            contentDescription = actionDescription,
            tint = tint,
            modifier = Modifier.size(dimens.iconLarge),
        )
    }
}

@Composable
internal fun TaskSquareCompletionButton(
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actionDescription = stringResource(
        if (isChecked) Res.string.task_mark_incomplete else Res.string.task_mark_complete
    )
    IconToggleButton(
        checked = isChecked,
        onCheckedChange = { onClick() },
        modifier = modifier
            .minimumInteractiveTargetSize()
            .semantics { contentDescription = actionDescription },
        content = content,
    )
}

@Composable
internal fun TaskStarButton(
    isStarred: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = OpenTasksTheme.dimens.iconDefault,
) {
    val actionDescription = stringResource(
        if (isStarred) Res.string.task_remove_star else Res.string.task_add_star
    )
    IconToggleButton(
        checked = isStarred,
        onCheckedChange = { onClick() },
        modifier = modifier.minimumInteractiveTargetSize(),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_star),
            contentDescription = actionDescription,
            tint = if (isStarred) StarGold
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun TaskTitleText(
    title: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    activeColor: Color = MaterialTheme.colorScheme.onBackground,
    completedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isCompleted) completedColor else activeColor,
        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun TaskContentPreviewText(
    previewText: String,
    modifier: Modifier = Modifier,
) {
    if (previewText.isNotBlank()) {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}
