package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.richeditor.model.RichTextState
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.minimumInteractiveTargetSize
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.bold
import opentasks.composeapp.generated.resources.bullet_list
import opentasks.composeapp.generated.resources.code
import opentasks.composeapp.generated.resources.ic_code
import opentasks.composeapp.generated.resources.ic_format_bold
import opentasks.composeapp.generated.resources.ic_format_italic
import opentasks.composeapp.generated.resources.ic_format_list_bulleted
import opentasks.composeapp.generated.resources.ic_format_list_numbered
import opentasks.composeapp.generated.resources.ic_format_strikethrough
import opentasks.composeapp.generated.resources.ic_format_underline
import opentasks.composeapp.generated.resources.italic
import opentasks.composeapp.generated.resources.numbered_list
import opentasks.composeapp.generated.resources.strikethrough
import opentasks.composeapp.generated.resources.underline
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun FormattingToolbar(
    richTextState: RichTextState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = dimens.paddingSmall, vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bold
        FormattingToolbarButton(
            icon = Res.drawable.ic_format_bold,
            contentDescription = stringResource(Res.string.bold),
            isActive = richTextState.currentSpanStyle.fontWeight == FontWeight.Bold,
            enabled = enabled,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
        )
        // Italic
        FormattingToolbarButton(
            icon = Res.drawable.ic_format_italic,
            contentDescription = stringResource(Res.string.italic),
            isActive = richTextState.currentSpanStyle.fontStyle == FontStyle.Italic,
            enabled = enabled,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
        )
        // Underline
        FormattingToolbarButton(
            icon = Res.drawable.ic_format_underline,
            contentDescription = stringResource(Res.string.underline),
            isActive = richTextState.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
            enabled = enabled,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
        )
        // Strikethrough
        FormattingToolbarButton(
            icon = Res.drawable.ic_format_strikethrough,
            contentDescription = stringResource(Res.string.strikethrough),
            isActive = richTextState.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
            enabled = enabled,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) },
        )

        Spacer(Modifier.width(dimens.paddingSmall))

        // Bullet list
        FormattingToolbarButton(
            icon = Res.drawable.ic_format_list_bulleted,
            contentDescription = stringResource(Res.string.bullet_list),
            isActive = richTextState.isUnorderedList,
            enabled = enabled,
            onClick = { richTextState.toggleUnorderedList() },
        )
        // Numbered list
        FormattingToolbarButton(
            icon = Res.drawable.ic_format_list_numbered,
            contentDescription = stringResource(Res.string.numbered_list),
            isActive = richTextState.isOrderedList,
            enabled = enabled,
            onClick = { richTextState.toggleOrderedList() },
        )
        // Code span
        FormattingToolbarButton(
            icon = Res.drawable.ic_code,
            contentDescription = stringResource(Res.string.code),
            isActive = richTextState.isCodeSpan,
            enabled = enabled,
            onClick = { richTextState.toggleCodeSpan() },
        )
    }
}

@Composable
private fun FormattingToolbarButton(
    icon: DrawableResource,
    contentDescription: String?,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.minimumInteractiveTargetSize(),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (isActive) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconLarge),
        )
    }
}
