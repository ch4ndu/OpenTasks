package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.utcMillisToLocalMillis
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.NoteViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.empty_notes
import opentasks.composeapp.generated.resources.ic_settings
import opentasks.composeapp.generated.resources.notes
import opentasks.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Strips markdown formatting and returns the first line as a preview. */
internal fun noteContentPreview(content: String): String =
    content
        .replace(Regex("[#*_~`>\\-\\[\\]()]"), "")
        .trim()
        .lines()
        .firstOrNull()
        ?: ""

private fun formatNoteDate(utcMillis: Long): String {
    if (utcMillis == 0L) return ""
    val local = utcMillisToLocalMillis(utcMillis)
    val y = extractYear(local)
    return "${formatDateShort(local)} $y"
}

@Composable
fun NotesScreen(
    viewModel: NoteViewModel,
    onNoteClick: (Note) -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val notes by viewModel.notes.collectAsState()
    NotesContent(
        notes = notes,
        onNoteClick = onNoteClick,
        onSettingsClick = onSettingsClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesContent(
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    val density = LocalDensity.current
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val navBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val topBarHeight = dimens.topBarHeight + statusBarHeight

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.empty_notes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeight + dimens.paddingMedium,
                    bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge,
                ),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(note = note, onClick = { onNoteClick(note) })
                }
            }
        }

        // Translucent Top bar overlay
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
            ),
            title = {
                Text(
                    text = stringResource(Res.string.notes),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = stringResource(Res.string.settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingSmall),
        onClick = onClick,
        shape = RoundedCornerShape(dimens.cornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(dimens.paddingLarge)) {
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
            }
            if (note.content.isNotBlank()) {
                val preview = remember(note.content) { noteContentPreview(note.content) }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
            }
            Text(
                text = formatNoteDate(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -- Previews ------------------------------------------------------------------

private val previewNotes = listOf(
    Note(
        id = "preview-note-1",
        title = "Meeting Notes",
        content = "Discussed roadmap priorities for Q2 and assigned owners",
        createdAt = 1773619200000L,
        updatedAt = 1773619200000L,
    ),
    Note(
        id = "preview-note-2",
        title = "Shopping List",
        content = "Milk, eggs, bread, coffee beans",
        createdAt = 1773532800000L,
        updatedAt = 1773532800000L,
    ),
    Note(
        id = "preview-note-3",
        title = "",
        content = "Quick thought: look into Kotlin Notebooks for data exploration",
        createdAt = 1773446400000L,
        updatedAt = 1773446400000L,
    ),
)

@Composable
@Preview
private fun NotesContentPreview() {
    OpenTasksTheme {
        NotesContent(
            notes = previewNotes,
            onNoteClick = {},
            onSettingsClick = {},
        )
    }
}

@Composable
@Preview
private fun NotesContentEmptyPreview() {
    OpenTasksTheme {
        NotesContent(
            notes = emptyList(),
            onNoteClick = {},
            onSettingsClick = {},
        )
    }
}

@Composable
@Preview
private fun NoteCardPreview() {
    OpenTasksTheme {
        NoteCard(
            note = previewNotes.first(),
            onClick = {},
        )
    }
}
