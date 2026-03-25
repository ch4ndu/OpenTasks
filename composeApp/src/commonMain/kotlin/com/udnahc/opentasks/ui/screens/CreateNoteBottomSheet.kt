package com.udnahc.opentasks.ui.screens

import org.lighthousegames.logging.logging
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.bold
import opentasks.composeapp.generated.resources.bullet_list
import opentasks.composeapp.generated.resources.code
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.delete_note
import opentasks.composeapp.generated.resources.delete_note_message
import opentasks.composeapp.generated.resources.delete_note_title
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.ic_delete
import opentasks.composeapp.generated.resources.ic_code
import opentasks.composeapp.generated.resources.ic_format_bold
import opentasks.composeapp.generated.resources.ic_format_italic
import opentasks.composeapp.generated.resources.ic_format_list_bulleted
import opentasks.composeapp.generated.resources.ic_format_list_numbered
import opentasks.composeapp.generated.resources.ic_format_strikethrough
import opentasks.composeapp.generated.resources.ic_format_underline
import opentasks.composeapp.generated.resources.italic
import opentasks.composeapp.generated.resources.note
import opentasks.composeapp.generated.resources.note_title_hint
import opentasks.composeapp.generated.resources.save
import opentasks.composeapp.generated.resources.numbered_list
import opentasks.composeapp.generated.resources.strikethrough
import opentasks.composeapp.generated.resources.underline
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val log = logging("CreateNoteBottomSheet")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    editNote: Note? = null,
    onSave: (title: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val stateKey = editNote?.id ?: 0L
    var title by remember(stateKey) { mutableStateOf(editNote?.title ?: "") }
    val richTextState = rememberRichTextState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(stateKey) {
        if (editNote != null) {
            log.d { "Loading note content: length=${editNote.content.length}, has newlines=${'\n' in editNote.content}" }
            log.v { "Raw content: ${editNote.content.take(200)}" }
            richTextState.setHtml(editNote.content)
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete_note_title)) },
            text = { Text(stringResource(Res.string.delete_note_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    onDismiss()
                }) {
                    Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            CreateNoteTopBar(
                onBack = onDismiss,
                onSave = {
                    val content = richTextState.toHtml()
                    log.d { "Saving note content: length=${content.length}, has newlines=${'\n' in content}" }
                    if (title.isNotBlank() || content.isNotBlank()) {
                        onSave(title, content)
                    }
                    onDismiss()
                },
                onDelete = if (editNote != null) {
                    { showDeleteConfirm = true }
                } else {
                    null
                },
            )

            val dimens = OpenTasksTheme.dimens

            // Title field
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                ),
                cursorBrush = SolidColor(PrimaryBlue),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(
                            horizontal = dimens.paddingLarge,
                            vertical = dimens.paddingMedium,
                        ),
                    ) {
                        if (title.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.note_title_hint),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Rich text editor
            BasicRichTextEditor(
                state = richTextState,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(PrimaryBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        horizontal = dimens.paddingLarge,
                        vertical = dimens.paddingMedium,
                    ),
            )

            // Markdown formatting toolbar
            MarkdownToolbar(
                richTextState = richTextState,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CreateNoteTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingSmall, vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = stringResource(Res.string.back),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(dimens.iconXLarge),
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(Res.string.note),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(Res.string.save),
            style = MaterialTheme.typography.labelLarge,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onSave)
                .padding(horizontal = dimens.paddingMedium, vertical = dimens.paddingSmall),
        )

        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.delete_note),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(dimens.iconXLarge),
                )
            }
        }
    }
}

@Composable
private fun MarkdownToolbar(
    richTextState: RichTextState,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = dimens.paddingSmall, vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bold
        ToolbarButton(
            icon = Res.drawable.ic_format_bold,
            contentDescription = stringResource(Res.string.bold),
            isActive = richTextState.currentSpanStyle.fontWeight == FontWeight.Bold,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
        )
        // Italic
        ToolbarButton(
            icon = Res.drawable.ic_format_italic,
            contentDescription = stringResource(Res.string.italic),
            isActive = richTextState.currentSpanStyle.fontStyle == FontStyle.Italic,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
        )
        // Underline
        ToolbarButton(
            icon = Res.drawable.ic_format_underline,
            contentDescription = stringResource(Res.string.underline),
            isActive = richTextState.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
        )
        // Strikethrough
        ToolbarButton(
            icon = Res.drawable.ic_format_strikethrough,
            contentDescription = stringResource(Res.string.strikethrough),
            isActive = richTextState.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
            onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) },
        )

        Spacer(Modifier.width(dimens.paddingSmall))

        // Bullet list
        ToolbarButton(
            icon = Res.drawable.ic_format_list_bulleted,
            contentDescription = stringResource(Res.string.bullet_list),
            isActive = richTextState.isUnorderedList,
            onClick = { richTextState.toggleUnorderedList() },
        )
        // Numbered list
        ToolbarButton(
            icon = Res.drawable.ic_format_list_numbered,
            contentDescription = stringResource(Res.string.numbered_list),
            isActive = richTextState.isOrderedList,
            onClick = { richTextState.toggleOrderedList() },
        )
        // Code span
        ToolbarButton(
            icon = Res.drawable.ic_code,
            contentDescription = stringResource(Res.string.code),
            isActive = richTextState.isCodeSpan,
            onClick = { richTextState.toggleCodeSpan() },
        )
    }
}

@Composable
private fun ToolbarButton(
    icon: DrawableResource,
    contentDescription: String?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(dimens.touchTargetMedium),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (isActive) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconLarge),
        )
    }
}

// region Previews

@Composable
@Preview
private fun CreateNoteTopBarPreview() {
    OpenTasksTheme {
        CreateNoteTopBar(
            onBack = {},
            onSave = {},
            onDelete = null,
        )
    }
}

@Composable
@Preview
private fun CreateNoteTopBarWithDeletePreview() {
    OpenTasksTheme {
        CreateNoteTopBar(
            onBack = {},
            onSave = {},
            onDelete = {},
        )
    }
}

@Composable
@Preview
private fun MarkdownToolbarPreview() {
    OpenTasksTheme {
        MarkdownToolbar(
            richTextState = rememberRichTextState(),
        )
    }
}

// endregion
