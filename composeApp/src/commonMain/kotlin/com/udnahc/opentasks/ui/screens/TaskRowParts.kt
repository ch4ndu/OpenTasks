package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.StarGold
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_check_box
import opentasks.composeapp.generated.resources.ic_check_box_outline
import opentasks.composeapp.generated.resources.ic_star
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun TaskCheckboxButton(
    isChecked: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    IconButton(
        onClick = onClick,
        modifier = modifier.size(dimens.touchTargetMedium),
    ) {
        Icon(
            painter = painterResource(
                if (isChecked) Res.drawable.ic_check_box else Res.drawable.ic_check_box_outline
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(dimens.iconLarge),
        )
    }
}

@Composable
internal fun TaskStarButton(
    isStarred: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    IconButton(
        onClick = onClick,
        modifier = modifier.size(dimens.touchTargetMedium),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_star),
            contentDescription = null,
            tint = if (isStarred) StarGold
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(dimens.iconDefault),
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
    content: String,
    modifier: Modifier = Modifier,
) {
    if (content.isNotBlank()) {
        val previewText = remember(content) { stripHtmlTags(content) }
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
